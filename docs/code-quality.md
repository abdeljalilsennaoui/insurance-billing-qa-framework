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

> **Status: both services are connected and analysing `main`.** Quality gate **passing**, 0 bugs,
> 0 vulnerabilities, 0 security hotspots, coverage imported at exactly the figures JaCoCo produced.
>
> It did not start that way. The gate first came back `ERROR` on eight security findings in the workflow
> file — see [what the first analysis found](#what-the-first-analysis-found) — and before that, a Codecov
> upload was silently rejected while the job stayed green. Both are recorded in
> [the last section](#what-has-and-has-not-been-verified) rather than tidied away.

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
build         ── runs the 72 application tests ─────────────────► jacoco.exec
api-tests    ─┐
ui-tests      ├── JACOCO=true scripts/start-app.sh ─────────────► jacoco-e2e.exec  (one per job)
bdd-tests    ─┘   the agent is inside the application process
                                                                        │
coverage ── downloads all four, merges, renders ────────────────────────┘
            └─► full-stack report ─► Codecov, Sonar, and the badges
```

The `cypress-smoke` job is not instrumented. It never runs a Maven build — it needs the jar artifact and
Node, nothing else — so no agent jar exists there to attach, and adding a build to that job would buy
coverage that duplicates the Selenium suite's almost exactly. Its result still has to be green before
the coverage job runs.

**Nothing is re-run to get this.** Earlier, CI published the narrower in-process number on the grounds
that the full-stack figure would mean running every suite a second time inside one job — several minutes
on every pull request for a number that gates nothing. That reasoning was sound and its conclusion is
now obsolete: the suites already run, the agent costs them almost nothing, and the extra job only
collects what they recorded. The change is in the workflow, and
[`coverage.md`](coverage.md#what-ci-publishes) is updated to match.

One honest caveat, with the measured size of it. CI reports **97.1%** line coverage where a local
`scripts/coverage.sh` run reports 99.0%. Comparing the two reports class by class, the whole difference
is a single file: `TestSupportController`, 7 of 16 lines in CI against 16 of 16 locally. The
`test-support` group wipes the database, so it cannot run beside anything else and is excluded from CI;
locally the script runs it last and alone.

Nothing else differs — which also settles what the uninstrumented Cypress job costs: **zero measured
lines**. Everything the smoke suite touches, the Selenium suite already covers.

---

## The two coverage percentages, and why they differ

JaCoCo reports **97.1%** line coverage for the CI run. Codecov reports **95.91%** for the same upload.
Neither is wrong; they count a partially covered line differently.

| | lines | hits | misses | partials |
|---|---:|---:|---:|---:|
| Codecov | 489 | 469 | 14 | 6 |
| JaCoCo | 489 | 475 covered | 14 missed | — |

JaCoCo calls a line covered if it was executed at all, so a line whose `if` took only one of its two
branches counts as covered and the shortfall shows up in the separate branch figure. Codecov splits
those out as *partials* and leaves them out of the hit count: 469/489 = 95.91%, and
469 + 6 partials = JaCoCo's 475.

The six partials are the same six missed branches behind the 88.9% branch coverage, each named in
[`coverage.md`](coverage.md). So the two tools agree on the facts and disagree on one definition —
worth knowing before someone asks why the badge and the report disagree.

---

## What the first analysis found

Worth recording, because the tool earned its place immediately.

**Eight MAJOR security findings, every one of them in `.github/workflows/ci.yml`**, which failed the
quality gate on new code with a security rating of 3. They were fixed on the branch that introduced
them:

| Rule | Finding | Fix |
|---|---|---|
| `githubactions:S7637` | `codecov/codecov-action@v5` is a moving tag | Pinned to a commit SHA, tag kept in a comment |
| `githubactions:S6505` | `npm ci` allows lifecycle scripts to run | `npm ci --ignore-scripts` |
| `githubactions:S6505`, `S8543` | `npx` fetches and runs packages that are not installed locally | Call `./node_modules/.bin/cypress` by path |

The npx one is the interesting one. That job installs from a committed lockfile precisely so a
transitive release cannot change what CI runs — and then invoked the binary through a command that will
happily download a different version if `node_modules` is incomplete. The path form fails instead,
which is the behaviour the lockfile was there to guarantee.

After the fix the gate returned **OK** — `new_security_rating` back to 1, eight findings resolved, none
open — and the Cypress suite still passes with lifecycle scripts off, which is the check that mattered:
the browser binary is fetched by the explicit `cypress install` step, not by a postinstall hook.

**Nothing else blocking.** On `main`, after the first CI analysis: **0 bugs, 0 vulnerabilities, 0
security hotspots, 0% duplication, quality gate OK**, and 30 code smells left as follow-ups.

The 38 smells the import first reported became 30 once the CI scanner replaced SonarQube Cloud's
automatic analysis, and the two reported bugs disappeared with it. Both differences are the
configuration working as intended: automatic analysis cannot read the POM, so it judged the automation
modules as product code and analysed the Thymeleaf fragment as if it were a page. Numbers from an
automatic analysis and from a CI analysis are not comparable, which is worth knowing before quoting
either.

The 30 open smells the first analysis reported, in the order they were worth looking at:

| Count | Rule | What it says | Status |
|---:|---|---|---|
| 10 | `java:S8688` | `LocalDate.now()` with no `ZoneId` or `Clock` | **Fixed** — DEF-012 |
| 9 | `java:S5778` | An assertion lambda invokes more than one method that could throw | Open |
| 4 | `java:S6809` | `@Transactional` method called from within the same class | Open, see below |
| 2 | `java:S8786` | A regular expression with superlinear runtime | Open |
| 5 | assorted | A hidden field, two AssertJ idioms, a deprecated call, a missing private constructor | Open |

### `java:S8688` — fixed, and it was a real defect

This was the one to take seriously rather than suppress, and taking it seriously found a defect.
**Whether an invoice was overdue was decided by `LocalDate.now()` reading the server's default time
zone.** Moving the process to a machine in another zone changed invoice statuses by up to a day, and
nothing in the suite could catch it: every test evaluated the rule in the same zone it was written in,
so the test and the code made the identical assumption and agreed with each other. The 165 tests and 99%
line coverage, and the affected lines all reported as covered — because they *ran*. Coverage records
execution, not correctness.

The fix is a `Clock` bean built from `billing.time-zone` (default `America/Toronto`), injected into the
five classes that read the date, with `Invoice.applyPayment` now taking the receipt instant as an
argument for the same reason `isOverdue` takes the reference date. The regression test freezes that
clock at an instant where the UTC date and the business-zone date differ; it fails 4 of 6 against the
unfixed code. Full write-up in [`defect-reports.md`](defect-reports.md) as DEF-012.

This is the strongest argument in the repository for running a tool that reads the source rather than
executes it. Static analysis found in one pass what those 165 executing tests structurally could not.

### `java:S6809` — a check rather than a fix

`SeedDataLoader` already documents why its seeding call crosses a bean boundary — a self-invocation
would bypass the proxy and run without a transaction. The other three should be read against that same
reasoning.

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

`fail_ci_if_error: false` has a cost worth naming, because this project already has three defects of
exactly this shape: **a rejected upload leaves the job green**. The first run proved it — Codecov
refused the upload as unauthenticated, the step reported success, and the only symptom was a dashboard
that stayed empty. The trade is deliberate: a coverage service being unreachable should not fail a build
whose tests passed. The way to notice is the step log or the dashboard, not the job's colour.

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

**Verified by running it, locally and on a runner:**

- **The pipeline, end to end on `main`.** All five suite jobs green, the coverage job downloading four
  execution files, merging and rendering: 97.1% line, 88.9% branch, 100% class.
- **The Sonar analysis**, once `SONAR_TOKEN` existed: `ANALYSIS SUCCESSFUL`, and coverage imported as
  97.1% line / 88.9% branch — identical to the JaCoCo report, which is the evidence that
  `sonar.coverage.jacoco.xmlReportPaths` and the test-code declarations are doing what they claim.
- **The quality gate**, both ways: `ERROR` on the eight workflow findings, then `OK` after they were
  fixed, confirmed as 0 open / 8 resolved.
- **The Codecov upload**, once `CODECOV_TOKEN` existed: accepted, and confirmed through the Codecov API
  rather than from the step's exit code — `"active": true`, 469 hits / 14 misses / 6 partials.
- **Both README badges**, fetched directly: the Sonar badge renders *passed*, the Codecov badge 96%.
- `mvn -B validate` on every module with the Sonar properties in place — the POMs parse and
  `sonar.coverage.jacoco.xmlReportPaths` interpolates to the two absolute paths intended.
- `org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211` resolves from Maven Central.
- The merge across several execution files: `jacoco:merge@merge-all-coverage` with
  `jacoco-e2e-api.exec`, `jacoco-e2e-ui.exec` and `jacoco-e2e.exec` present produces the same figures as
  the single-file merge — 99.0% line, 88.9% branch, 100% class.
- **The analysis step skipping cleanly when `SONAR_TOKEN` is absent**, rather than failing the job.

**Still not verified:**

- The Codecov **pull-request comment** as a *coverage comparison*. PR #49 was the first to touch
  `billing-app/src/main/java`, and Codecov did comment on it — but with the one-off "once you merge this
  PR into your default branch, you're all set" notice, because there is no base report on `main` to
  compare against until a covered change lands there. So the comment mechanism works; what it will say
  about a diff is still unproven, and the first pull request after #49 merges is the real test.
- The quality gate **blocking anything**. It reports; nothing is configured to require it. See
  [what gates what](#what-gates-what). It does *run* on pull requests — #49 returned `OK` with
  `new_coverage` 100% — but nothing acts on the result.

**Corrected along the way**, since each was stated here as fact and was not:

- That the first Codecov upload had succeeded tokenlessly. It was rejected — `Token required - not valid
  tokenless upload` — and `fail_ci_if_error: false` left the step green. The Codecov API showing
  `"active": false` with zero commits is what exposed it; nothing in the pipeline did.
- That the project carried two bugs and 38 code smells. Those were SonarQube Cloud's *automatic*
  analysis, which cannot read the POM and therefore judged test framework code as product code. The CI
  analysis reports 0 bugs and 30 smells.
