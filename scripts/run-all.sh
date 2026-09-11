#!/usr/bin/env bash
#
# Runs everything: build, application tests, then every automation suite against one running instance.
#
# This is the single command the README points at, and it mirrors what CI does, so a green run here
# means the same thing a green pipeline means.
#
# The application is stopped on exit whatever happens, via a trap, so a failing suite never leaves a
# process holding port 8080 and making the next run fail for the wrong reason.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

cleanup() {
  "$ROOT/scripts/stop-app.sh" || true
}
trap cleanup EXIT

echo "=============================================="
echo "1/4  Build and application tests"
echo "=============================================="
# -DskipITs leaves the integration suites out of this step on purpose. They drive a running
# application, and nothing is running yet: a plain 'mvn install' here would fail with connection
# refused before the application has even been built. The suites run in step 3, once it is up.
mvn -B clean install -DskipITs

echo
echo "=============================================="
echo "2/4  Start application"
echo "=============================================="
scripts/start-app.sh

echo
echo "=============================================="
echo "3/4  API suite (REST Assured + TestNG)"
echo "=============================================="
mvn -B verify -pl qa-api-tests

echo
echo "=============================================="
echo "4/4  Done"
echo "=============================================="
echo "All suites passed."
