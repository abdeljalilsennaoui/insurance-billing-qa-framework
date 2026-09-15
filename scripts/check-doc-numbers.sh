#!/usr/bin/env bash
#
# Fails if a headline figure quoted in the documentation disagrees with docs/test-inventory.md.
#
# This is the regression guard for DEF-014. The counts in the README and the docs used to be typed by
# hand in eight places; they drifted apart from the suite and from each other, and nothing noticed
# because no test reads prose. This does.
#
# It is deliberately narrow. It checks the numbers that are claims about the suite - how many tests
# there are, per layer - and nothing else. A check that tried to validate every number in every
# document would fail on the seeded amounts and the performance figures, which are not claims about
# the suite and do not live in the inventory.
#
# Usage: scripts/check-doc-numbers.sh
#   Run scripts/count-tests.sh first, or the inventory it reads will describe an older run.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

INVENTORY="docs/test-inventory.md"

if [[ ! -f "$INVENTORY" ]]; then
  echo "$INVENTORY is missing. Run scripts/count-tests.sh after a full run." >&2
  exit 1
fi

python3 scripts/check_doc_numbers.py
