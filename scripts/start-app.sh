#!/usr/bin/env bash
#
# Starts the billing application and waits until it is genuinely ready to serve requests.
#
# Used by both the local run script and the CI workflow, so that "it works locally" and "it works in
# CI" mean the same thing. The health poll replaces a fixed sleep: a sleep that is long enough for a
# slow CI runner wastes time on every fast run, and a sleep that is tuned to a fast run fails
# intermittently on a slow one.
#
# Usage: scripts/start-app.sh [port]

set -euo pipefail

PORT="${1:-8080}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/billing-app/target/billing-app.jar"
LOG="$ROOT/billing-app/target/app.log"
PID_FILE="$ROOT/billing-app/target/app.pid"
TIMEOUT_SECONDS=90

if [[ ! -f "$JAR" ]]; then
  echo "Application jar not found at $JAR" >&2
  echo "Build it first: mvn -B clean install -DskipTests" >&2
  exit 1
fi

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Application already running with PID $(cat "$PID_FILE")"
  exit 0
fi

echo "Starting billing-app on port $PORT"
# The QA test-support endpoints are switched on explicitly here. They default to off so that a
# deployment which has not opted in cannot expose a route capable of wiping its data.
nohup java -jar "$JAR" \
  --server.port="$PORT" \
  --qa.test-support.enabled=true \
  > "$LOG" 2>&1 &
echo $! > "$PID_FILE"

elapsed=0
until curl -sf "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; do
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Application exited during startup. Last 40 log lines:" >&2
    tail -40 "$LOG" >&2
    exit 1
  fi
  if (( elapsed >= TIMEOUT_SECONDS )); then
    echo "Application did not become healthy within ${TIMEOUT_SECONDS}s. Last 40 log lines:" >&2
    tail -40 "$LOG" >&2
    exit 1
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done

echo "Application healthy after ${elapsed}s (PID $(cat "$PID_FILE"), log: $LOG)"
