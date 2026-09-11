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

# Refuse to start when something else is already serving this port.
#
# Without this check the script is actively misleading: the new process fails to bind, dies, and the
# health poll below is answered by the stale server, so the script reports success and the suites run
# against an old build. That is how a green run can certify code that was never deployed. A loud
# failure here is far cheaper than the confusion it prevents.
if curl -sf "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; then
  echo "Something is already serving port $PORT, but it was not started by this script." >&2
  if command -v lsof > /dev/null 2>&1; then
    # -sTCP:LISTEN restricts this to the process actually bound to the port. Without it, lsof also
    # reports clients holding an established connection - an editor with the page open, a browser tab -
    # and the message then names a PID that must not be killed.
    echo "Listening PID(s): $(lsof -ti:"$PORT" -sTCP:LISTEN | tr '\n' ' ')" >&2
  fi
  echo "Stop it first (scripts/stop-app.sh, or kill the PID above) and try again." >&2
  exit 1
fi

# Optional JaCoCo instrumentation of the application process.
#
# The API, UI and BDD suites drive the application over HTTP, so an in-process coverage run sees nothing
# of what they exercise. Attaching the agent here is the only way to measure their contribution.
#
# append=true matters: the suites are run as several separate Maven invocations against one application
# instance, and the data is written once on JVM shutdown. output=file keeps it to a plain destfile rather
# than opening a TCP port.
JACOCO_ARGS=()
if [[ "${JACOCO:-false}" == "true" ]]; then
  AGENT="$ROOT/billing-app/target/jacoco-agent.jar"
  if [[ ! -f "$AGENT" ]]; then
    echo "JACOCO=true but the agent jar is missing at $AGENT" >&2
    echo "Build first: mvn -B clean install -DskipITs" >&2
    exit 1
  fi
  EXEC_FILE="$ROOT/billing-app/target/jacoco-e2e.exec"
  rm -f "$EXEC_FILE"
  JACOCO_ARGS=("-javaagent:$AGENT=destfile=$EXEC_FILE,output=file,append=true")
  echo "JaCoCo agent attached; coverage will be written to $EXEC_FILE on shutdown"
fi

echo "Starting billing-app on port $PORT"
# The QA test-support endpoints are switched on explicitly here. They default to off so that a
# deployment which has not opted in cannot expose a route capable of wiping its data.
nohup java "${JACOCO_ARGS[@]}" -jar "$JAR" \
  --server.port="$PORT" \
  --qa.test-support.enabled=true \
  > "$LOG" 2>&1 &
echo $! > "$PID_FILE"

# Liveness is checked before health on each pass, deliberately. Checking health first means a single
# early success ends the loop before the process has been examined at all, which is exactly how a dead
# process gets reported as a healthy one.
elapsed=0
while true; do
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "Application exited during startup. Last 40 log lines:" >&2
    tail -40 "$LOG" >&2
    exit 1
  fi
  if curl -sf "http://localhost:$PORT/actuator/health" > /dev/null 2>&1; then
    break
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
