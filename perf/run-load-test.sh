#!/usr/bin/env bash
#
# Runs the invoice API load plan and produces an HTML report.
#
# JMeter is not a project dependency and is not installed by this script. Point JMETER_HOME at an
# installation, or put jmeter on PATH:
#
#   JMETER_HOME=/opt/apache-jmeter-5.6.3 perf/run-load-test.sh
#   perf/run-load-test.sh 20 10 25          # threads, ramp-up seconds, loops per thread
#
# Results and the HTML report land in perf/results/, which is git-ignored: they describe one run on one
# machine and are not a project artefact.

set -euo pipefail

THREADS="${1:-10}"
RAMPUP="${2:-5}"
LOOPS="${3:-20}"
HOST="${HOST:-localhost}"
PORT="${PORT:-8080}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLAN="$ROOT/perf/invoice-api-load.jmx"
OUT="$ROOT/perf/results"
STAMP="$(date +%Y%m%d-%H%M%S)"

if [[ -n "${JMETER_HOME:-}" && -x "$JMETER_HOME/bin/jmeter" ]]; then
  JMETER="$JMETER_HOME/bin/jmeter"
elif command -v jmeter > /dev/null 2>&1; then
  JMETER="$(command -v jmeter)"
else
  echo "JMeter not found. Set JMETER_HOME to an Apache JMeter installation, or add jmeter to PATH." >&2
  echo "Download: https://jmeter.apache.org/download_jmeter.cgi" >&2
  exit 1
fi

# Fail before spending minutes on a run that cannot produce meaningful numbers.
if ! curl -sf "http://$HOST:$PORT/actuator/health" > /dev/null 2>&1; then
  echo "No healthy application at http://$HOST:$PORT - start it with scripts/start-app.sh first." >&2
  exit 1
fi

mkdir -p "$OUT"
RESULTS="$OUT/results-$STAMP.jtl"
REPORT="$OUT/report-$STAMP"

echo "Running $THREADS threads, ${RAMPUP}s ramp-up, $LOOPS loops against http://$HOST:$PORT"

"$JMETER" -n \
  -t "$PLAN" \
  -l "$RESULTS" \
  -e -o "$REPORT" \
  -Jhost="$HOST" \
  -Jport="$PORT" \
  -Jthreads="$THREADS" \
  -Jrampup="$RAMPUP" \
  -Jloops="$LOOPS"

echo
echo "Raw results: $RESULTS"
echo "HTML report: $REPORT/index.html"
