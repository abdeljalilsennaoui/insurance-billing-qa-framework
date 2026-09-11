#!/usr/bin/env bash
#
# Regenerates the screenshots embedded in docs/test-report.md.
#
# Two capture passes, because the images come from two different places. The 'console' pass drives the
# running application through the UI suite's own page objects; the 'reports' pass photographs the HTML
# reports that JaCoCo, Cucumber and JMeter have already written to disk, and needs no application.
#
# The console pass resets the database to the seeded baseline before it starts, so the list screenshot
# shows six invoices rather than whatever a suite left behind. That reset deletes every row, which is
# why this script must not run while a suite is running.
#
# Usage: scripts/capture-screenshots.sh
# Output: docs/screenshots/*.png

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAR="$ROOT/billing-app/target/billing-app.jar"
STARTED_BY_THIS_SCRIPT=false

cleanup() {
  if [[ "$STARTED_BY_THIS_SCRIPT" == "true" ]]; then
    "$ROOT/scripts/stop-app.sh" || true
  fi
}
trap cleanup EXIT

if [[ ! -f "$JAR" ]]; then
  echo "Application jar not found at $JAR" >&2
  echo "Build it first: mvn -B clean install -DskipITs" >&2
  exit 1
fi

# An application that is already up is left alone and left running, because the common case is
# regenerating screenshots straight after a suite run against that same instance. Only an application
# this script started is one this script may stop.
if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
  echo "Using the application already running on port 8080"
else
  scripts/start-app.sh
  STARTED_BY_THIS_SCRIPT=true
fi

echo
echo "Capturing the invoice console"
mvn -q -pl qa-ui-tests exec:java -Dexec.args=console

echo
echo "Capturing the HTML reports"
mvn -q -pl qa-ui-tests exec:java -Dexec.args=reports

echo
echo "Screenshots written to $ROOT/docs/screenshots"
