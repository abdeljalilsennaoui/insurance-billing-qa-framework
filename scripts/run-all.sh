#!/usr/bin/env bash
#
# Runs everything: build, application tests, then every automation suite against one running instance.
#
# This is the single command the README points at, and it mirrors what CI does, so a green run here means
# the same thing a green pipeline means.
#
# The application is stopped on exit whatever happens, via a trap, so a failing suite never leaves a
# process holding port 8080 and making the next run fail for the wrong reason.
#
# The Cypress suite is skipped with a clear notice if its dependencies are not installed, rather than
# failing the whole run: it is a smoke suite over ground the Selenium suite already covers, and an
# absent npm install is a setup detail rather than a product defect. Every Java suite is mandatory.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

cleanup() {
  "$ROOT/scripts/stop-app.sh" || true
}
trap cleanup EXIT

banner() {
  echo
  echo "=============================================="
  echo "$1"
  echo "=============================================="
}

banner "1/6  Build and application tests"
# -DskipITs leaves the integration suites out of this step on purpose. They drive a running application,
# and nothing is running yet: a plain 'mvn install' here would fail with connection refused before the
# application has even been built. The suites run in steps 3 to 6.
mvn -B clean install -DskipITs

banner "2/6  Start application"
scripts/start-app.sh

banner "3/6  API suite (REST Assured + TestNG, includes SOAP)"
mvn -B verify -pl qa-api-tests

banner "4/6  UI suite (Selenium, headless)"
mvn -B verify -pl qa-ui-tests

banner "5/6  BDD scenarios (Cucumber, API + UI)"
mvn -B verify -pl qa-bdd-tests

banner "6/6  Cypress smoke suite"
if [[ -d "$ROOT/cypress/node_modules/cypress" ]]; then
  (cd "$ROOT/cypress" && npx cypress run)
else
  echo "SKIPPED: Cypress dependencies are not installed."
  echo "Install them with:  cd cypress && npm ci && npx cypress install"
fi

banner "All suites passed"
