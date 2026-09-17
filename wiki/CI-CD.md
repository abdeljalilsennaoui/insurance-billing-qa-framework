# CI and CD

[`.github/workflows/ci.yml`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/.github/workflows/ci.yml)
runs on every push to `main` and every pull request. A merge is gated by pipeline evidence, not by
someone having run the suites locally.

## The jobs

| # | Job | What it does |
|---|---|---|
| 1 | **Build and application tests** | Builds every module, runs the 66 unit and integration tests, publishes the application jar and its coverage data |
| 2 | **API automation** | Downloads that jar, starts it, runs 104 REST Assured tests |
| 3 | **UI automation** | The same, for 60 Selenium tests |
| 4 | **BDD scenarios** | The same, for 43 Cucumber scenarios |
| 5 | **Smoke suite** | The same, for 12 Cypress tests |
| 6 | **Coverage and static analysis** | Merges the coverage data jobs 1–4 produced, renders the full-stack report, submits it to Codecov and SonarQube Cloud |

Jobs 2 to 5 run in parallel and each takes a few minutes; the whole pipeline is under ten.

## Decisions worth explaining

**The suites run against the artifact job 1 validated, not a rebuild.** Jobs 2–5 download the jar rather
than building the source again. Testing a second build of the same source proves that build works, which
is not the question.

**CI starts the application with the same script a developer uses.** `scripts/start-app.sh` in the
pipeline and on a laptop, so "works on my machine" and "works in CI" cannot quietly diverge. The script
polls the health endpoint rather than sleeping: a fixed sleep is either wasted time on a fast run or an
intermittent failure on a slow one.

**Coverage is merged rather than re-measured.** Jobs 2–4 attach the JaCoCo agent to the *application*
process and upload what it records; job 6 merges those with the in-process data from job 1. That is what
turns 91% line coverage into 99% without running a single suite twice — see
[Coverage and quality](Coverage-and-Quality).

**Artifacts are uploaded for the run you actually have to debug.** Test reports upload on success and
failure. The application log and the browser screenshots upload only on failure, because those are what
a red run needs and a green run does not.

**A new push cancels the run still in flight.** The older result is about to be irrelevant, and leaving
it running only delays the one that matters.

**The `test-support` group is excluded from CI.** It wipes the database, so it cannot run beside the
parallel regression suite. It runs alone, locally, via `scripts/coverage.sh`. 547 of the 551 tests run on
every pull request; those two are the exception, and the reason is written down rather than left as a
gap in the count.

## Quality services

Codecov comments on each pull request with the coverage of the lines it changes. SonarQube Cloud
analyses the source for bugs, code smells, security hotspots and duplication, with the merged coverage
imported alongside.

Neither blocks a merge on a coverage threshold — that decision, and what each tool is allowed to gate,
is in
[`docs/code-quality.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/code-quality.md).

## Jenkins

[`Jenkinsfile`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/Jenkinsfile)
is a declarative pipeline covering the same stages, included because Jenkins is still the dominant CI
server in enterprise QA environments.

**It has never been executed.** No Jenkins controller was available. It is written against this project's
real layout and real Maven commands, but it is a reference implementation rather than a verified one, and
it says so at the top of the file, in the README and in the test strategy. Anyone adopting it should
expect to adjust the agent definition, the JDK and Maven tool names, and plugin availability.

## A second workflow you may see

A workflow named `Copilot` sometimes appears in the Actions tab. That is GitHub's own automated code
review, enabled at the account level. It is not part of this repository.
