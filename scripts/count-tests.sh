#!/usr/bin/env bash
#
# Derives the suite counts from the reports the last run actually produced, and writes
# docs/test-inventory.md.
#
# This exists because the counts in this repository's documentation drifted apart from the suite and
# from each other (DEF-013). Every figure quoted in the README and the docs is now generated from
# surefire/failsafe XML and Cucumber JSON rather than typed by hand, so the only way for a number to
# be wrong is for the run itself to have been wrong.
#
# Requires a completed run. `scripts/run-all.sh` first, then this. It reads target/ only and starts
# nothing.
#
# Usage: scripts/count-tests.sh [--check]
#   --check   print the inventory to stdout and write nothing, for use in a pipeline

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OUTPUT="docs/test-inventory.md"
CHECK_ONLY=false
[[ "${1:-}" == "--check" ]] && CHECK_ONLY=true

if ! command -v python3 > /dev/null 2>&1; then
  echo "python3 is required to parse the report XML." >&2
  exit 1
fi

python3 scripts/count_tests.py > "$ROOT/.test-inventory.tmp"

if $CHECK_ONLY; then
  cat "$ROOT/.test-inventory.tmp"
  rm -f "$ROOT/.test-inventory.tmp"
  exit 0
fi

mv "$ROOT/.test-inventory.tmp" "$OUTPUT"
echo "Wrote $OUTPUT"
