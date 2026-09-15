#!/usr/bin/env python3
"""Holds the documentation's claims to the things they describe.

Two kinds of claim are checked: a figure quoted in prose against docs/test-inventory.md, and a
screenshot a document points at against the files in docs/screenshots. Both are a reference that has
to stay in step with something else by somebody remembering, which is the mechanism behind DEF-014.

The inventory is generated from the last run's reports. This reads the totals out of it and then
checks every document that quotes one of them. A mismatch exits non-zero and names the file, the
figure it claims and the figure the suite actually produced.

Two kinds of figure are checked: the size of the suite, and the size of the catalogues the
documentation keeps — defects logged, requirements traced, manual cases written. Both drifted
(DEF-014), and both are now derived rather than typed.

Seeded amounts, performance figures and coverage percentages are not in the inventory and are not
checked: they come from a measurement rather than from a list, and a stale one is caught by re-running
the measurement rather than by counting rows.
"""

from __future__ import annotations

import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INVENTORY = os.path.join(REPO, "docs", "test-inventory.md")
SCREENSHOTS = os.path.join(REPO, "docs", "screenshots")

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
    # Catalogue figures. These do not come from a run — see the inventory's Document catalogue.
    "defects": "Defects logged",
    "infra-defects": "Defects in test infrastructure",
    "requirements": "Requirements traced",
    "cases": "Manual test cases",
    "manual-cases": "Cases kept manual",
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

MARKER = re.compile(r"<!--count:([a-z-]+)-->\s*([0-9,]+)\s*<!--/count-->")

# Matches both forms a screenshot is referenced by: the relative path the docs use, and the raw
# githubusercontent URL the wiki pages need because the wiki is a separate repository.
IMAGE = re.compile(r"screenshots/([A-Za-z0-9._-]+\.png)")


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


def documents_with_images():
    """Every document that embeds a screenshot: the docs, the README and the staged wiki pages."""
    paths = list(DOCUMENTS)
    wiki = os.path.join(REPO, "wiki")
    if os.path.isdir(wiki):
        paths += [os.path.join("wiki", n) for n in sorted(os.listdir(wiki)) if n.endswith(".md")]
    return paths


def check_screenshots():
    """Every screenshot a document points at exists, and every screenshot is pointed at.

    The images are numbered, so inserting one renumbers the rest. That has twice left a document
    pointing at a name that no longer existed, and it fails silently: a missing image renders as a
    broken icon, not as an error, and on the wiki it is a 404 nobody sees until a reader does.

    Both directions matter. A reference with no file is a broken image; a file with no reference is
    an image nobody removed when the text that used it was rewritten.
    """
    problems = []
    if not os.path.isdir(SCREENSHOTS):
        return problems

    on_disk = {n for n in os.listdir(SCREENSHOTS) if n.endswith(".png")}
    referenced = set()

    for relative in documents_with_images():
        path = os.path.join(REPO, relative)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as handle:
            for number, line in enumerate(handle, start=1):
                for match in IMAGE.finditer(line):
                    name = match.group(1)
                    referenced.add(name)
                    if name not in on_disk:
                        problems.append(
                            f"{relative}:{number} points at screenshots/{name}, which does not exist"
                        )

    for name in sorted(on_disk - referenced):
        problems.append(f"docs/screenshots/{name} is referenced by no document")

    return problems


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
                            f"{relative}:{number} marks a figure as '{key}', which is not a counted "
                            f"layer or catalogue. Known: {', '.join(sorted(totals))}"
                        )
                    elif claimed != totals[key]:
                        problems.append(
                            f"{relative}:{number} claims {key} = {claimed}; the suite produced {totals[key]}"
                        )

    images = check_screenshots()

    if problems or images:
        if problems:
            print("Documentation figures disagree with the suite:\n", file=sys.stderr)
            for problem in problems:
                print("  " + problem, file=sys.stderr)
            print(
                "\nRun scripts/count-tests.sh after a full run, then correct the marked figures.",
                file=sys.stderr,
            )
        if images:
            print("\nScreenshot references do not match docs/screenshots:\n", file=sys.stderr)
            for problem in images:
                print("  " + problem, file=sys.stderr)
            print(
                "\nRe-run scripts/capture-screenshots.sh, or correct the reference.",
                file=sys.stderr,
            )
        sys.exit(1)

    print(f"{checked} documented figures agree with docs/test-inventory.md.")
    for key in sorted(totals):
        print(f"  {key:<14} {totals[key]}")
    print(f"Every screenshot reference in {len(documents_with_images())} documents resolves.")


if __name__ == "__main__":
    main()
