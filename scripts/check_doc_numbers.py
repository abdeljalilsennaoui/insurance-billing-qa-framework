#!/usr/bin/env python3
"""Holds the documentation's headline figures to docs/test-inventory.md.

The inventory is generated from the last run's reports. This reads the totals out of it and then
checks every document that quotes one of them. A mismatch exits non-zero and names the file, the
figure it claims and the figure the suite actually produced.

Only claims about the size of the suite are checked. Seeded amounts, performance figures and
coverage percentages are not in the inventory and are not the kind of number that drifted.
"""

from __future__ import annotations

import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INVENTORY = os.path.join(REPO, "docs", "test-inventory.md")

# Each entry: the label in the inventory's Totals table, and the token used in the documents to mark
# a figure as that count. Documents carry the marker as an HTML comment so the check is anchored to
# the author's intent rather than to a regex guessing at prose.
#
#     The suite runs <!--count:total-->473<!--/count--> checks.
LABELS = {
    "total": "**Total**",
    "unit": "Application — domain and service unit",
    "integration": "Application — Spring integration",
    "api": "API (REST Assured)",
    "ui": "UI (Selenium)",
    "bdd": "BDD scenarios (Cucumber)",
    "cypress": "Smoke (Cypress)",
}

DOCUMENTS = [
    "README.md",
    "docs/test-strategy.md",
    "docs/test-report.md",
    "docs/coverage.md",
    "docs/code-quality.md",
    "docs/defect-reports.md",
    "docs/manual-test-cases.md",
    "docs/requirements-traceability-matrix.md",
    "docs/agile-workflow.md",
    "docs/design-rationale.md",
]

MARKER = re.compile(r"<!--count:([a-z]+)-->\s*([0-9,]+)\s*<!--/count-->")


def read_totals():
    totals = {}
    with open(INVENTORY, encoding="utf-8") as handle:
        text = handle.read()
    for key, label in LABELS.items():
        pattern = re.compile(r"^\|\s*" + re.escape(label) + r"\s*\|\s*\**([0-9]+)\**\s*\|", re.MULTILINE)
        match = pattern.search(text)
        if not match:
            print(f"Could not find '{label}' in {INVENTORY}.", file=sys.stderr)
            sys.exit(1)
        totals[key] = int(match.group(1))
    return totals


def main():
    totals = read_totals()
    problems = []
    checked = 0

    for relative in DOCUMENTS:
        path = os.path.join(REPO, relative)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as handle:
            for number, line in enumerate(handle, start=1):
                for match in MARKER.finditer(line):
                    key, claimed = match.group(1), int(match.group(2).replace(",", ""))
                    checked += 1
                    if key not in totals:
                        problems.append(
                            f"{relative}:{number} marks a figure as '{key}', which is not a counted layer. "
                            f"Known: {', '.join(sorted(totals))}"
                        )
                    elif claimed != totals[key]:
                        problems.append(
                            f"{relative}:{number} claims {key} = {claimed}; the suite produced {totals[key]}"
                        )

    if problems:
        print("Documentation figures disagree with the suite:\n", file=sys.stderr)
        for problem in problems:
            print("  " + problem, file=sys.stderr)
        print(
            "\nRun scripts/count-tests.sh after a full run, then correct the marked figures.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"{checked} documented figures agree with docs/test-inventory.md.")
    for key in sorted(totals):
        print(f"  {key:<8} {totals[key]}")


if __name__ == "__main__":
    main()
