# Code quality and coverage tooling

Three tools, each answering a different question. Running all three is only worth it because the
questions do not overlap as much as the marketing suggests.

| Tool | The question it answers | Where it runs | Does it block a merge? |
|---|---|---|---|
| **JaCoCo** | Which lines and branches were executed? | Every build, and the `coverage` CI job | No |
| **SonarQube Cloud** | What is wrong with the code that nobody ran a test for? | The `coverage` CI job | Only through its own pull-request check |
| **Codecov** | How much of *this pull request* is covered? | The `coverage` CI job | No — configured informational |

JaCoCo measures execution. Sonar reads the source: null dereferences, resource leaks, unreachable
branches, duplicated blocks, security hotspots. Codecov is the only one of the three that says anything
about the change in front of a reviewer rather than about the project as a whole.

> **Status: the Sonar analysis has never executed.** It needs a SonarQube Cloud project and a
> `SONAR_TOKEN` secret, neither of which existed when this was written. What was and was not verified is
> recorded in [the last section](#what-has-and-has-not-been-verified), and the workflow step carries the
> same warning. The Codecov upload and the coverage merge that feeds both tools have run.

---

## The coverage number these tools see

This is the part worth understanding, because the obvious wiring publishes a number that is wrong by
eight percentage points.

The application's own unit and integration tests run in the JVM Surefire forks, so `prepare-agent`
measures them for free. The API, UI, BDD and smoke suites do not: they drive the application over HTTP
in a **separate process**, and from the test JVM's point of view nothing in `billing-app` was ever
called. Measured here that is the difference between **91.0%** and **99.0%** line coverage, and the SOAP
package alone reads 44% against 94%. [`coverage.md`](coverage.md) covers the mechanism in full.

So the pipeline collects both halves:

```
build            ── runs the 66 application tests ──────────────► jacoco.exec
api-tests    ─┐
ui-tests      ├── JACOCO=true scripts/start-app.sh ─────────────► jacoco-e2e.exec  (one per job)
bdd-tests     │   the agent is inside the application process
cypress-smoke ┘
                                                                        │
coverage ── downloads all five, merges, renders ────────────────────────┘
            └─► full-stack report ─► Codecov, Sonar, and the badges
```

**Nothing is re-run to get this.** Earlier, CI published the narrower in-process number on the grounds
that the full-stack figure would mean running every suite a second time inside one job — several minutes
on every pull request for a number that gates nothing. That reasoning was sound and its conclusion is
now obsolete: the suites already run, the agent costs them almost nothing, and the extra job only
collects what they recorded. The change is in the workflow, and
[`coverage.md`](coverage.md#what-ci-publishes) is updated to match.

One honest caveat. CI's figure is fractionally below the local one, because the `test-support` group is
excluded from CI — it wipes the database, so it cannot run beside anything else, and
[`scripts/coverage.sh`](../scripts/coverage.sh) runs it last and alone. `TestSupportController.reset`
therefore shows as uncovered in CI and covered locally.

---

## How Sonar is configured, and why

### In the POM, not in `sonar-project.properties`

The Maven scanner reads its configuration from the POM and **ignores** `sonar-project.properties` in a
multi-module build. A properties file would sit in the repository looking authoritative while doing
nothing — the same failure mode as the TestNG suite XMLs that Failsafe 3.6.0 silently ignored
(DEF-005 in [`defect-reports.md`](defect-reports.md)).

### The automation modules are declared as test code

Each of `qa-api-tests`, `qa-ui-tests` and `qa-bdd-tests` sets:

```xml
<sonar.sources></sonar.sources>
<sonar.tests>src/main/java,src/test/java</sonar.tests>
```

Everything in those modules is test code, including what lives in `src/main/java` — the clients, page
objects and specifications are the framework the tests are written against, not product code that
ships. It lives under `src/main/java` only so the BDD module can depend on it as an ordinary library
rather than consuming another module's test-jar.

Leaving the default would do two bad things. Sonar would apply product-code rules to test code. And
every one of those framework classes would be a file with no coverage data behind it, which Sonar counts
as **0% covered** — dragging the project's coverage figure down with classes that nothing measures and
that nothing was ever going to measure.

### Generated code is excluded

`billing-app` sets `sonar.exclusions=**/soap/generated/**`, mirroring the JaCoCo exclusion. `xjc`
generates those classes from the XSD into `target/generated-sources`, Maven adds that as a source root,
and issues raised against generated code are issues with the generator: nobody can act on them here, and
they would bury the findings that matter.

### The scanner version is pinned

`sonar:sonar` as an unversioned plugin prefix resolves to whatever the latest release is on the day the
job runs. A scanner that upgrades itself can change an analysis result with nothing in the history to
explain it, so the version is pinned in the root POM like every other plugin.

---

## What gates what

Neither tool fails the build.

**Codecov** is configured `informational: true` for both the project and patch statuses in
[`codecov.yml`](../codecov.yml). This project has no coverage threshold, deliberately: a threshold set
before seeing the numbers is arbitrary and one set to match them is decoration. There is a second reason
here specifically — several remaining uncovered branches are defensive code no route through the
application can reach, so a gate would eventually be satisfied by writing tests that construct
impossible states. That is worse than the gap.

**Sonar** submits its analysis and the Maven step ends there; it does not wait on the quality gate.
Enforcement, if wanted, belongs to SonarQube Cloud's own pull-request check combined with a branch
protection rule — that is the mechanism the service provides, it reports on the pull request where the
result is visible, and it does not conflate "the analysis was submitted" with "the gate passed".

Making the Maven step itself blocking is one line:

```bash
mvn -B sonar:sonar -Dsonar.qualitygate.wait=true
```

It is left off until the first analysis has actually been looked at. The default *Sonar way* gate
requires 100% of new security hotspots to be reviewed, and a project's first analysis has a backlog of
unreviewed ones by definition — turning it on before that review would fail the pipeline for a reason
that has nothing to do with the change being merged.

---

## Setting the services up

Only the repository owner can do these; they need an account.

### SonarQube Cloud

1. Sign in at [sonarcloud.io](https://sonarcloud.io) with GitHub.
2. **+** → **Analyze new project**, grant access to `insurance-billing-qa-framework`, import it.
3. In **Administration → Analysis Method**, switch **Automatic Analysis off**. It conflicts with
   CI-based analysis, and leaving both on makes the Maven step fail with a message about it.
4. Generate a token and add it to the repository as the secret **`SONAR_TOKEN`**
   (*Settings → Secrets and variables → Actions*).
5. Check that the organisation and project keys SonarQube Cloud created match the ones in the root POM:

   ```xml
   <sonar.organization>abdeljalilsennaoui</sonar.organization>
   <sonar.projectKey>abdeljalilsennaoui_insurance-billing-qa-framework</sonar.projectKey>
   ```

   The import wizard usually produces exactly these, but it is worth confirming rather than debugging a
   "project not found" later.

Without `SONAR_TOKEN` the analysis step skips itself; it does not fail the pipeline.

### Codecov

1. Sign in at [codecov.io](https://codecov.io) with GitHub and add the repository.
2. Copy the upload token and add it as the secret **`CODECOV_TOKEN`**.

The token is optional for a public repository, but tokenless uploads are rate-limited and fail in ways
that look like flakiness. `fail_ci_if_error: false` means a coverage service being unreachable never
fails a build whose tests passed.

### The badges

The Sonar and Codecov badges in the [README](../README.md) resolve only once each project exists. Until
then they render as broken images — worth doing the two setups above in the same sitting as merging
this, or removing the badge lines until then.

---

## What has and has not been verified

Following the rule this repository holds itself to: anything not executed says so.

**Verified by running it:**

- `mvn -B validate` on every module with the Sonar properties in place — the POMs parse and
  `sonar.coverage.jacoco.xmlReportPaths` interpolates to the two absolute paths intended.
- `org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211` resolves from Maven Central.
- The merge across several execution files: `jacoco:merge@merge-all-coverage` with
  `jacoco-e2e-api.exec`, `jacoco-e2e-ui.exec` and `jacoco-e2e.exec` present produces the same figures as
  the single-file merge — 99.0% line, 88.9% branch, 100% class.
- `.github/workflows/ci.yml` parses as YAML with the expected jobs and step order.

**Not verified, because it cannot be without the accounts:**

- The Sonar analysis itself. It has never run. Whether the quality gate passes, what the analysis finds,
  and whether the organisation key matches are all open questions until step 4 above is done.
- The Codecov upload. The action is wired and the report it points at exists; nothing has been sent.
- The Codecov pull-request comment, which depends on the app being installed on the repository.
