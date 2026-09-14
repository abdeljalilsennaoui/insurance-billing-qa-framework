# Insurance Billing QA Framework

[![CI](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/actions/workflows/ci.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=abdeljalilsennaoui_insurance-billing-qa-framework&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=abdeljalilsennaoui_insurance-billing-qa-framework)
[![codecov](https://codecov.io/gh/abdeljalilsennaoui/insurance-billing-qa-framework/branch/main/graph/badge.svg)](https://codecov.io/gh/abdeljalilsennaoui/insurance-billing-qa-framework)
![Java](https://img.shields.io/badge/Java-21-blue)
![Maven](https://img.shields.io/badge/build-Maven-C71A36)
![REST Assured](https://img.shields.io/badge/API-REST%20Assured-green)
![TestNG](https://img.shields.io/badge/runner-TestNG-orange)

End-to-end QA automation framework for a simulated insurance billing platform.

The repository contains two halves, deliberately kept separate:

- **The system under test** — a Spring Boot insurance billing application: customers, policies,
  invoices and payments, with a REST API and a server-rendered web console.
- **The QA automation** — API and UI suites that exercise that application the way a test automation
  engineer would exercise a real billing platform.

The automation modules have **no dependency on the application's code**. They test the published
HTTP contract and the rendered DOM, not shared classes. If both sides shared an enum, renaming a
constant would change the test and the application together and the suite would still pass while the
contract had silently broken.

## Module layout

| Module | Purpose |
|---|---|
| `billing-app` | Spring Boot 3 application under test (domain, REST API, web console) |
| `qa-api-tests` | REST Assured + TestNG API automation |
| `qa-ui-tests` | Selenium 4 + Page Object Model UI automation |
| `qa-bdd-tests` | Cucumber feature files and step definitions |
| `scripts/` | Application lifecycle and full-suite run scripts |
| `docs/` | Test strategy, manual test cases, traceability matrix, defect reports |

Framework code in the automation modules lives in `src/main/java`, and the tests themselves in
`src/test/java`. That split lets the BDD module reuse the API clients and page objects as ordinary
library classes instead of consuming another module's test-jar.

## Prerequisites

- JDK 21
- Maven 3.9+
- Google Chrome (for the browser suites)
- Node.js 20+ (for the Cypress suite)

## Running everything

One command builds the project, starts the application, runs every suite, and stops the application
again:

```bash
./scripts/run-all.sh
```

Its six steps are: build and application tests, start application, API suite, UI suite, BDD scenarios,
Cypress smoke. The Cypress step is skipped with a notice if its npm dependencies are not installed; every
Java suite is mandatory.

The application is stopped on exit via a trap, so a failing suite never leaves a process holding
port 8080.

## Running pieces individually

```bash
# Build everything and run the application's own unit and integration tests.
# -DskipITs leaves out the suites that need a running server.
mvn -B clean install -DskipITs

# Start and stop the application under test (health-polled, not a fixed sleep)
./scripts/start-app.sh
./scripts/stop-app.sh

# Regenerate the screenshots embedded in docs/test-report.md. Resets the database to the seeded
# baseline first, so it must not run while a suite is running.
./scripts/capture-screenshots.sh

# API suite against a running application
mvn -B verify -pl qa-api-tests

# Narrower slices by TestNG group
mvn -B verify -pl qa-api-tests -Dapi.groups=smoke        # 7 tests, critical path
mvn -B verify -pl qa-api-tests -Dapi.groups=negative     # 33 rejection scenarios
mvn -B verify -pl qa-api-tests -Dapi.groups=regression   # 52 tests, full coverage (the CI default)
mvn -B verify -pl qa-api-tests -Dapi.groups=soap         # 7 SOAP tests
mvn -B verify -pl qa-api-tests -Dapi.groups=test-support # 2 tests — MUST run alone, see below

# The test-support group resets the database, so it is excluded from every other group and from CI's
# default run. Running it alongside the parallel regression suite would delete fixtures other tests
# were using, and they would fail for reasons unrelated to what they check.

# Point any suite at a different environment
mvn -B verify -pl qa-api-tests -Dapp.base.url=http://localhost:9090
```

### Why `-DskipITs` rather than `-DskipTests`

`-DskipTests` alone does **not** stop maven-failsafe-plugin 3.6.0 from executing the integration
suites; they then fail with connection refused because no application is running. `-DskipITs` is the
flag that actually excludes them. A plain `mvn verify` at the root runs everything and therefore
requires a running application, which is correct Maven semantics.

Group selection uses Failsafe's own `groups` parameter rather than TestNG suite XML files: Failsafe
3.6.0 has dropped `suiteXmlFiles` support, accepting the parameter, warning that it is unsupported,
and then silently running every test class instead.

## The application under test

Once started, the application is available at `http://localhost:8080`:

| Path | What it is |
|---|---|
| `/invoices` | Invoice console: list, filter, detail, payment form |
| `/api/customers`, `/api/policies`, `/api/invoices` | REST API |
| `/api/invoices/{id}/payments` | Payment endpoint |
| `/actuator/health` | Readiness probe used by the start script and CI |
| `/h2-console` | In-memory database console |

It runs against in-memory H2 and seeds a deterministic baseline at startup: 3 customers, 4 policies
and 6 invoices covering every invoice state (unpaid, partially paid, paid, overdue, cancelled, and
one on a lapsed policy). Seeded records are for reads; any test that mutates data creates its own.

### Error semantics

The API distinguishes failure kinds so a test can prove *why* something was refused:

| Status | Meaning | Example `code` |
|---|---|---|
| 400 | The request could not be understood | `VALIDATION_FAILED`, `MALFORMED_REQUEST` |
| 404 | The referenced resource does not exist | `NOT_FOUND` |
| 409 | Well formed, conflicts with existing state | `DUPLICATE_EMAIL` |
| 422 | Understood, refused by a billing rule | `EXCEEDS_OUTSTANDING_BALANCE`, `AMOUNT_NOT_POSITIVE`, `INVOICE_CANCELLED`, `POLICY_NOT_ACTIVE` |

A zero payment is therefore `422 AMOUNT_NOT_POSITIVE`, not a generic 400. Without that split, an
assertion could not tell "the client sent nonsense" from "the platform applied a billing rule".

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to `main` and every pull
request:

1. **Build, application tests** — builds all modules, runs the application's 72 unit and integration
   tests, publishes the application jar and its coverage data as artifacts.
2. **API automation** — downloads that jar, starts it, runs the REST Assured regression suite, stops
   it, uploads test reports.
3. **UI automation** — the same, for the Selenium suite.
4. **BDD scenarios** — the same, for Cucumber.
5. **Smoke suite** — the same, for Cypress.
6. **Coverage and static analysis** — merges the coverage data every job above produced, renders the
   full-stack report, and submits it to Codecov and SonarQube Cloud.

Jobs 2 to 5 run against the exact artifact the first job validated, rather than a second build of the
same source, and they run in parallel. Test reports upload on success and failure; the application log
uploads on failure, which is what is actually needed to diagnose a red run.

Jobs 2 to 4 start the application with the JaCoCo agent attached, so the final job can measure what the
black-box suites exercised without re-running any of them — see
[docs/code-quality.md](docs/code-quality.md).

CI uses the same `scripts/start-app.sh` a developer runs locally, so "works on my machine" and "works
in CI" cannot quietly diverge.

### Jenkins

[`Jenkinsfile`](Jenkinsfile) is a declarative pipeline covering the same stages, provided because
Jenkins is still the dominant CI server in enterprise QA environments.

**It is not executed for this repository.** GitHub Actions is the pipeline that actually runs and gates
merges. The Jenkinsfile is written against the real project layout and the real Maven commands, but no
Jenkins controller was available to run it, so it is a reference implementation rather than a verified
one. Anyone adopting it should expect to adjust the agent definition, the JDK and Maven tool names, and
plugin availability for their own controller.

### Performance testing

The JMeter plan in [`perf/`](perf/README.md) is run manually, not in CI, and
[`perf/README.md`](perf/README.md) records the measured numbers together with the reasons they are not a
capacity measurement. A shared runner's CPU is too variable for response-time thresholds: they would
either never fire or fire at random, and a performance gate that fails at random gets ignored.

## Documentation

| Document | What it covers |
|---|---|
| [Test execution report](docs/test-report.md) | One full run of every suite, with screenshots of the console, coverage figures and the limitations |
| [Test strategy](docs/test-strategy.md) | Test levels, architectural decisions with their trade-offs, risk prioritisation, known limitations |
| [Manual test cases](docs/manual-test-cases.md) | 22 cases with steps and expected results, each marked automated or not |
| [Traceability matrix](docs/requirements-traceability-matrix.md) | 23 requirements mapped to manual cases and automated tests by class and method |
| [Defect reports](docs/defect-reports.md) | The 12 defects found during development, each linked to its fixing commit |
| [Agile workflow](docs/agile-workflow.md) | How the work was run, and how it maps to Jira and Zephyr |
| [Performance results](perf/README.md) | Measured JMeter numbers and why they are not a capacity claim |
| [Code coverage](docs/coverage.md) | Line and branch coverage, how black-box suites are measured, every remaining gap named |
| [Code quality tooling](docs/code-quality.md) | JaCoCo, SonarQube Cloud and Codecov — what each answers, how they are configured, what is allowed to block a merge |
| [Design rationale](docs/design-rationale.md) | How the framework is built and why, by file path — including the decisions that were wrong first, and the limits of what this repository claims |

## Test counts

| Suite | Tests | Runner | In CI |
|---|---:|---|---|
| Domain unit | 27 | JUnit 5 | yes |
| Application integration (API + web layer) | 45 | JUnit 5 + MockMvc | yes |
| API automation (incl. 7 SOAP) | 52 | TestNG + REST Assured | yes |
| UI automation | 18 | TestNG + Selenium 4 | yes |
| BDD scenarios (14 API + 6 UI) | 20 | Cucumber 7 + TestNG | yes |
| Smoke | 7 | Cypress | yes |
| Reset endpoint (`test-support` group) | 2 | TestNG + REST Assured | no — must run alone |
| **Total** | **171** | | **169** |
| Performance | 1 plan | JMeter | no — run manually, see [perf](perf/README.md) |

169 of the 171 run on every pull request across five CI jobs, with a sixth that runs no tests and
publishes their merged coverage. The two excluded are the `test-support`
reset tests, which wipe the database and therefore cannot run beside anything else; `scripts/coverage.sh`
runs them last, on their own.

Verified to pass **twice in a row against one running application instance** — 97 black-box tests
(52 API, 18 UI, 20 BDD, 7 smoke) run through twice on 2026-09-14 against an application started once,
with no reset between the passes. That is the check that catches shared-state coupling: a suite which
only passes against a freshly started application is hiding a dependency that surfaces later as
apparent flakiness in an unrelated test.

The application uses JUnit 5 because that is the idiomatic Spring Boot stack; the automation modules use
TestNG for its groups, data providers and parallel execution, which is what the QA tooling ecosystem is
built around.

## Code coverage

```bash
mvn -B clean install -DskipITs     # in-process coverage, automatic
open billing-app/target/site/jacoco/index.html

./scripts/coverage.sh              # full stack, including the black-box suites
open billing-app/target/site/jacoco-full/index.html
```

| Metric | In-process | Full-stack |
|---|---|---|
| Line | 91.0% | **99.0%** |
| Branch | 88.9% | **88.9%** |
| Class | 95.2% | **100%** |

The two columns differ because the API, UI and BDD suites drive the application in a **separate JVM**, so
an ordinary in-process coverage run sees nothing of what they exercise — the SOAP package reads 44%
in-process and 94% full-stack. `scripts/coverage.sh` attaches the JaCoCo agent to the application process
and merges the execution data.

CI produces the full-stack figure too, by attaching the agent in each suite job and merging the results
in a final job, so the number on the badges is the one that accounts for the black-box suites rather than
the narrower in-process one.

Branch coverage is the lower, more honest figure, and every one of the six missed branches is accounted
for individually in [docs/coverage.md](docs/coverage.md). There is deliberately **no coverage gate**: a
threshold set before seeing the numbers is arbitrary, and one set to match them is decoration.

## A note on the two CI-visible workflows

`CI` is this project's pipeline. A second workflow named `Copilot` may also appear in the Actions tab:
that is GitHub's own automated code review, enabled at the account level, and is not part of this
repository.
