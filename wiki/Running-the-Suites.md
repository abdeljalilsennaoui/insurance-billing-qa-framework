# Running the suites

The suites here have been run on macOS with JDK 25 and on Ubuntu runners in CI. Full detail is in the
[README](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework#readme).

## Prerequisites

| | |
|---|---|
| JDK | 25 — the build targets it via `maven.compiler.release`, and CI pins the same major |
| Maven | 3.9+ |
| Browser | Google Chrome. No driver to install: Selenium Manager resolves a matching chromedriver at runtime |
| Node | 22+, only for the Cypress smoke suite |

## Everything, in one command

```bash
./scripts/run-all.sh
```

Six steps: build and application tests, start application, API suite, UI suite, BDD scenarios, Cypress
smoke. The application is stopped from an exit trap, so a failing suite never leaves a process holding
port 8080. The Cypress step is skipped with a notice if its npm dependencies are missing; every Java
suite is mandatory.

## Piece by piece

```bash
# Build, and run the application's own 66 unit and integration tests.
mvn -B clean install -DskipITs

# Start and stop the application under test. The start script polls the health endpoint;
# it does not sleep for a fixed interval and hope.
./scripts/start-app.sh
./scripts/stop-app.sh

# Each suite against a running application
mvn -B verify -pl qa-api-tests
mvn -B verify -pl qa-ui-tests
mvn -B verify -pl qa-bdd-tests
( cd cypress && npx cypress run )

# Narrower slices of the API suite by TestNG group
mvn -B verify -pl qa-api-tests -Dapi.groups=smoke        # 13 tests, critical path
mvn -B verify -pl qa-api-tests -Dapi.groups=negative     # 33 rejection scenarios
mvn -B verify -pl qa-api-tests -Dapi.groups=regression   # 104 tests, the CI default
mvn -B verify -pl qa-api-tests -Dapi.groups=soap         # 7 SOAP tests

# Point any suite somewhere else
mvn -B verify -pl qa-api-tests -Dapp.base.url=http://localhost:9090

# Watch a UI failure happen in a visible browser
mvn -B verify -pl qa-ui-tests -Dui.headless=false
```

## Coverage and evidence

```bash
./scripts/coverage.sh              # full-stack coverage, including the black-box suites
open billing-app/target/site/jacoco-full/index.html

./scripts/capture-screenshots.sh   # regenerate the images in the test report
```

See [Coverage and quality](Coverage-and-Quality) for what the two coverage reports mean.

## Five traps, each of which cost time at least once

**`-DskipITs`, not `-DskipTests`.** `-DskipTests` alone does not stop maven-failsafe-plugin 3.6.0 from
executing the integration suites; they then fail with connection refused because no application is
running. `-DskipITs` is the flag that actually excludes them.

**The `test-support` group must run alone.** It resets the database. Run beside the parallel regression
suite, it deletes fixtures other tests are using and they fail for reasons that have nothing to do with
what they check — failures in innocent tests, the most expensive kind to diagnose.

```bash
mvn -B verify -pl qa-api-tests -Dapi.groups=test-support   # on its own, never with anything else
```

**Something already on port 8080.** `scripts/start-app.sh` refuses to continue rather than letting the
new process fail to bind while the health poll is answered by the stale server. Without that check a
green run can certify code that was never deployed.

**Cypress needs its binary installed explicitly.** Under an npm `ignore-scripts` policy the postinstall
hook does not run and the binary is missing after `npm ci`:

```bash
cd cypress && npm ci && npx cypress install && npx cypress verify
```

**`JAVA_HOME` pointing at the wrong JDK.** If Maven picks up a JDK older than 25, compilation stops with
`error: release version 25 not supported`. A newer one builds, but it is not the JDK CI runs:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS
```

## What a clean run looks like

551 tests, all passing, in about four minutes end to end — the per-suite breakdown and the durations are
in the [test report](Test-Report).
