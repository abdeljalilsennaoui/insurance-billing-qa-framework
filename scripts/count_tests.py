#!/usr/bin/env python3
"""Builds docs/test-inventory.md from the reports the last run produced.

Reads surefire and failsafe XML for the Java suites and Cucumber JSON for the BDD scenarios, and
writes a Markdown inventory to stdout. Nothing here is hand-maintained: if a figure in this file is
wrong, the run that produced the reports was wrong.

Cypress is counted from its spec files rather than from a report, because the suite is run through
the Cypress binary rather than Maven and leaves no machine-readable artefact behind by default. That
is stated in the output rather than hidden, so nobody mistakes it for a measured result.
"""

from __future__ import annotations

import glob
import json
import os
import re
import xml.etree.ElementTree as ET
from collections import defaultdict

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Packages under billing-app whose tests boot a Spring context. Everything else in the module runs as
# plain Java with no context at all, and the documentation quotes the two halves separately.
#
# This is a list rather than the single `api` it started as because the split is by kind of test, and
# the package is only a proxy for it. The XML these counts are read from carries a class name and
# nothing else, so there is no way to ask a report whether its tests booted a context - which means a
# new package of Spring tests has to be added here, exactly as a new console page has to be added to
# WebPageModelAdvice. Both are the same shape of trap and neither fails loudly; what fails is the
# figure, quietly, in a document whose whole purpose is to be accurate.
SPRING_CONTEXT_PACKAGES = {"api", "assistant"}

# Module -> the layer it is reported as. Ordered as a reader would read them: inside out.
LAYERS = [
    (
        "billing-app",
        "surefire-reports",
        "Application — domain and service unit",
        lambda pkg: pkg not in SPRING_CONTEXT_PACKAGES,
    ),
    (
        "billing-app",
        "surefire-reports",
        "Application — Spring integration",
        lambda pkg: pkg in SPRING_CONTEXT_PACKAGES,
    ),
    ("qa-api-tests", "failsafe-reports", "API (REST Assured)", None),
    ("qa-ui-tests", "failsafe-reports", "UI (Selenium)", None),
]


def read_java_suites():
    """Per-class test counts, keyed by layer."""
    results = {}
    for module, reports, label, package_filter in LAYERS:
        pattern = os.path.join(REPO, module, "target", reports, "TEST-*.xml")
        classes = {}
        for path in sorted(glob.glob(pattern)):
            root = ET.parse(path).getroot()
            name = root.get("name", os.path.basename(path))
            # Cucumber's TestNG runner reports one "test" per scenario; those are counted from the
            # JSON instead, so the runner classes are skipped here to avoid counting them twice.
            if name.endswith("ScenariosIT"):
                continue
            if package_filter is not None:
                parts = name.split(".")
                package = parts[2] if len(parts) > 3 else ""
                if not package_filter(package):
                    continue
            classes[name] = {
                "tests": int(root.get("tests", 0)),
                "failures": int(root.get("failures", 0)),
                "errors": int(root.get("errors", 0)),
                "skipped": int(root.get("skipped", 0)),
            }
        results[label] = classes
    return results


def read_cucumber():
    """Scenario counts per feature, from the Cucumber JSON reports."""
    features = {}
    for path in sorted(glob.glob(os.path.join(REPO, "qa-bdd-tests", "target", "cucumber-reports", "*.json"))):
        with open(path, encoding="utf-8") as handle:
            for feature in json.load(handle):
                name = feature.get("name", "?")
                # A Scenario Outline contributes one element per Examples row, which is the right
                # count: each row is a scenario that can pass or fail on its own.
                scenarios = [e for e in feature.get("elements", []) if e.get("type") == "scenario"]
                steps = sum(len(e.get("steps", [])) for e in scenarios)
                passed = sum(
                    1
                    for e in scenarios
                    if all(s.get("result", {}).get("status") == "passed" for s in e.get("steps", []))
                )
                entry = features.setdefault(name, {"scenarios": 0, "steps": 0, "passed": 0})
                entry["scenarios"] += len(scenarios)
                entry["steps"] += steps
                entry["passed"] += passed
    return features


def count_cypress():
    """Cypress tests, counted from the specs themselves. See the module docstring."""
    specs = {}
    for path in sorted(glob.glob(os.path.join(REPO, "cypress", "cypress", "e2e", "*.cy.js"))):
        with open(path, encoding="utf-8") as handle:
            source = handle.read()
        specs[os.path.basename(path)] = len(re.findall(r"^\s*it\(", source, re.MULTILINE))
    return specs


def testng_groups():
    """Group membership, read from the @Test annotations themselves."""
    groups = defaultdict(int)
    for module in ("qa-api-tests", "qa-ui-tests"):
        for path in glob.glob(os.path.join(REPO, module, "src", "test", "**", "*.java"), recursive=True):
            with open(path, encoding="utf-8") as handle:
                source = handle.read()
            for match in re.finditer(r"@Test\s*\(\s*groups\s*=\s*(\{[^}]*\}|\"[^\"]*\")", source):
                for group in re.findall(r"\"([^\"]+)\"", match.group(1)):
                    groups[group] += 1
    return groups


def read_catalogues():
    """Counts the catalogues the documentation keeps: defects, requirements and manual cases.

    These are read from the documents that own them rather than from a run, because those documents
    are the record — the defect log *is* the list of defects. They are counted here for exactly the
    reason the suite figures are (DEF-014): each was quoted in several other files and drifted apart
    from its own list. A count now comes from the list it describes, never typed beside it.
    """

    def read(name):
        with open(os.path.join(REPO, "docs", name), encoding="utf-8") as handle:
            return handle.read()

    defects = read("defect-reports.md")
    logged = re.findall(r"^## (DEF-\d{3})\b", defects, re.MULTILINE)

    # The Component line under each heading says where the defect lived. An "... infrastructure"
    # component means the defect was in the testing apparatus rather than in the application — the
    # class that can make a green run untrustworthy, which the documents quote separately.
    components = re.findall(r"^\*\*Severity:\*\*.*?\*\*Component:\*\*\s*(.+?)\s*$", defects, re.MULTILINE)
    if len(components) != len(logged):
        raise SystemExit(
            f"defect-reports.md has {len(logged)} headings but {len(components)} Component lines; "
            "every defect needs one for the infrastructure count to be trustworthy."
        )
    infrastructure = [c for c in components if "infrastructure" in c.lower()]

    cases_doc = read("manual-test-cases.md")
    cases = set(re.findall(r"TC-\d{3}", cases_doc))
    # The "Cases kept manual" table lists the ones no suite covers; everything else is automated.
    kept_manual = set()
    tail = cases_doc.split("Cases kept manual", 1)
    if len(tail) == 2:
        kept_manual = set(re.findall(r"^\|\s*(TC-\d{3})\b", tail[1], re.MULTILINE))

    requirements = set(re.findall(r"REQ-\d{2}", read("requirements-traceability-matrix.md")))

    return [
        ("Defects logged", len(logged)),
        ("Defects in test infrastructure", len(infrastructure)),
        ("Requirements traced", len(requirements)),
        ("Manual test cases", len(cases)),
        ("Cases kept manual", len(kept_manual)),
    ]


def table(rows, headers, aligns=None):
    aligns = aligns or ["---"] * len(headers)
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join(aligns) + "|"]
    out += ["| " + " | ".join(str(c) for c in row) + " |" for row in rows]
    return "\n".join(out)


def main():
    java = read_java_suites()
    cucumber = read_cucumber()
    cypress = count_cypress()
    groups = testng_groups()

    lines = [
        "# Test inventory",
        "",
        "**Generated by `scripts/count-tests.sh` from the reports of the last run. Do not edit.**",
        "",
        "Every headline figure quoted in the README and the other documents is checked against this",
        "file by `scripts/check-doc-numbers.sh`, which CI runs. That is the fix for DEF-014: the counts",
        "used to be typed in several places and drifted apart from the suite and from each other.",
        "",
        "## Totals",
        "",
    ]

    totals = []
    grand = 0
    for _, _, label, _ in LAYERS:
        count = sum(c["tests"] for c in java[label].values())
        grand += count
        totals.append((label, count))

    bdd_scenarios = sum(f["scenarios"] for f in cucumber.values())
    cypress_total = sum(cypress.values())
    grand += bdd_scenarios + cypress_total

    rows = [(label, count) for label, count in totals]
    rows.append(("BDD scenarios (Cucumber)", bdd_scenarios))
    rows.append(("Smoke (Cypress)", cypress_total))
    rows.append(("**Total**", f"**{grand}**"))
    lines += [table(rows, ["Layer", "Count"], ["---", "---:"]), ""]

    lines += ["## By class", ""]
    for _, _, label, _ in LAYERS:
        classes = java[label]
        if not classes:
            continue
        lines += [f"### {label}", ""]
        rows = []
        for name, stats in sorted(classes.items()):
            outcome = "pass" if stats["failures"] == 0 and stats["errors"] == 0 else "FAIL"
            rows.append((f"`{name.rsplit('.', 1)[-1]}`", stats["tests"], outcome))
        rows.append(("**Subtotal**", f"**{sum(c['tests'] for c in classes.values())}**", ""))
        lines += [table(rows, ["Class", "Tests", "Result"], ["---", "---:", ":---:"]), ""]

    if cucumber:
        lines += ["### BDD scenarios (Cucumber)", ""]
        rows = [
            (f"{name}", stats["scenarios"], stats["steps"], "pass" if stats["passed"] == stats["scenarios"] else "FAIL")
            for name, stats in sorted(cucumber.items())
        ]
        rows.append(("**Subtotal**", f"**{bdd_scenarios}**", f"**{sum(f['steps'] for f in cucumber.values())}**", ""))
        lines += [table(rows, ["Feature", "Scenarios", "Steps", "Result"], ["---", "---:", "---:", ":---:"]), ""]

    if cypress:
        lines += [
            "### Smoke (Cypress)",
            "",
            "Counted from the specs, not from a report: the suite runs through the Cypress binary rather",
            "than Maven and produces no machine-readable artefact by default.",
            "",
        ]
        rows = [(f"`{name}`", count) for name, count in sorted(cypress.items())]
        rows.append(("**Subtotal**", f"**{cypress_total}**"))
        lines += [table(rows, ["Spec", "Tests"], ["---", "---:"]), ""]

    if groups:
        lines += [
            "## TestNG groups",
            "",
            "Read from the `@Test(groups = ...)` annotations, so these are **annotated methods, not",
            "executed tests**. The two differ wherever a data provider expands one method into a row per",
            "case, which is why the group figures here are smaller than the per-class counts above.",
            "",
            "A test in neither `regression` nor `ui-regression` never runs in CI.",
            "",
            table(
                [(f"`{name}`", count) for name, count in sorted(groups.items())],
                ["Group", "Tests"],
                ["---", "---:"],
            ),
            "",
        ]

    lines += [
        "## Document catalogue",
        "",
        "Counted from the documents that own these lists, not from a run: the defect log is the list of",
        "defects, and the traceability matrix is the list of requirements. They are here so that every",
        "other document quoting one of them is checked against its source rather than against a memory",
        "of it.",
        "",
        table(read_catalogues(), ["Catalogue", "Count"], ["---", "---:"]),
        "",
    ]

    print("\n".join(lines).rstrip() + "\n")


if __name__ == "__main__":
    main()
