#!/usr/bin/env bash
#
# Produces a full-stack line and branch coverage report.
#
# The point of this script is that ordinary `mvn test` coverage is incomplete here. The application's own
# unit and integration tests run in the same JVM as the coverage agent, so they are measured for free. The
# API, UI and BDD suites do not: they drive the application over HTTP in a separate process, and from the
# test JVM's point of view nothing in billing-app was ever called. Running only in-process coverage makes
# the black-box suites look like they test nothing at all — the SOAP package, exercised exclusively by
# black-box tests, reports 44% line coverage in-process and considerably more once the agent is attached.
#
# So this script:
#   1. builds and runs the in-process tests, producing jacoco.exec
#   2. starts the application with the JaCoCo agent attached
#   3. runs the API, UI and BDD suites against it
#   4. stops the application, which flushes jacoco-e2e.exec on JVM shutdown
#   5. merges both execution files and renders one report
#
# Usage: scripts/coverage.sh
# Output: billing-app/target/site/jacoco-full/index.html

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

banner "1/5  Build and in-process coverage"
# Produces billing-app/target/jacoco.exec from the 66 unit and integration tests.
mvn -B clean install -DskipITs

banner "2/5  Start application with the JaCoCo agent"
JACOCO=true scripts/start-app.sh

banner "3/5  Run the black-box suites against the instrumented application"
mvn -B verify -pl qa-api-tests
mvn -B verify -pl qa-ui-tests
mvn -B verify -pl qa-bdd-tests

# Last, and on its own: the test-support group resets the database, so anything still using a fixture
# would lose it. Running it here means the reset endpoint is included in the coverage figure without it
# being able to disturb the suites above.
mvn -B verify -pl qa-api-tests -Dapi.groups=test-support

banner "4/5  Stop the application to flush its coverage data"
# The agent writes its destfile from a JVM shutdown hook, so the data does not exist until the process
# has exited. Stopping here rather than leaving it to the trap keeps the ordering explicit.
scripts/stop-app.sh

if [[ ! -f billing-app/target/jacoco-e2e.exec ]]; then
  echo "Expected billing-app/target/jacoco-e2e.exec to exist after shutdown, but it does not." >&2
  echo "The agent may not have attached. Check billing-app/target/app.log." >&2
  exit 1
fi

banner "5/5  Merge execution data and render the report"
mvn -B -pl billing-app jacoco:merge@merge-all-coverage jacoco:report@full-coverage-report

echo
echo "HTML report: $ROOT/billing-app/target/site/jacoco-full/index.html"
echo "XML report:  $ROOT/billing-app/target/site/jacoco-full/jacoco.xml"
echo
echo "In-process only, for comparison: $ROOT/billing-app/target/site/jacoco/index.html"
