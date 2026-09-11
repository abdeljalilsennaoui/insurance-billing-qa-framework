#!/usr/bin/env bash
#
# Stops the billing application started by scripts/start-app.sh.
#
# Exits successfully when nothing is running, so it is safe to call unconditionally from a CI
# "always" step or a trap without masking the real failure that triggered it.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT/billing-app/target/app.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "No PID file; nothing to stop."
  exit 0
fi

PID="$(cat "$PID_FILE")"
if ! kill -0 "$PID" 2>/dev/null; then
  echo "Process $PID is not running; removing stale PID file."
  rm -f "$PID_FILE"
  exit 0
fi

echo "Stopping billing-app (PID $PID)"
kill "$PID"

for _ in $(seq 1 20); do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "Stopped."
    exit 0
  fi
  sleep 1
done

echo "Process did not exit after 20s; sending SIGKILL." >&2
kill -9 "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
