# Defect log

Twelve defects found while building and stabilising the project. Every one was actually encountered —
none is an illustrative example written to fill a template — and each is linked to its fixing commit in
[`docs/defect-reports.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/defect-reports.md),
where each has steps to reproduce, expected and actual behaviour, root cause and resolution.

**None is open.**

| ID | Title | Severity | Found by |
|---|---|---|---|
| DEF-001 | Every path-variable endpoint fails at runtime | Critical | API integration tests |
| DEF-002 | `LazyInitializationException` mapping an invoice response | Critical | API integration tests |
| DEF-003 | Session id appended to redirect URLs | Medium | Manual smoke check |
| DEF-004 | Application reports healthy before data is seeded | High | Manual smoke check |
| DEF-005 | Test group selection silently ignored | High | Group count inspection |
| DEF-006 | `StaleElementReferenceException` on repeated form submission | High | Selenium suite |
| DEF-007 | Start script reports healthy for a server it did not start | High | SOAP suite investigation |
| DEF-008 | Cucumber hooks never registered | High | BDD UI scenarios |
| DEF-009 | SOAP endpoint rejects `application/xml` | Medium | SOAP suite |
| DEF-010 | Surefire claims an `*IT` class and runs it without an application | Medium | Coverage work |
| DEF-011 | `stalenessOf` escapes as a CDP error on a CI runner | High | CI, not local |
| DEF-012 | The overdue rule is decided by the host's time zone | High | SonarQube Cloud static analysis |
| DEF-013 | Language switch renders with no destination on a new page | Medium | Agent console tests |
| DEF-014 | Documented test counts drifted apart from the suite | Low | Documentation audit |

## The ones worth reading first

**Three of the fourteen were defects in the test infrastructure, not the application** — and those are the
dangerous ones, because each produced a green or healthy result while testing less than it claimed.

**DEF-012 — the overdue rule read the host's time zone.** The one no test could have caught.
`LocalDate.now()` resolves against `ZoneId.systemDefault()`, so whether an invoice was `OVERDUE` was a
property of whichever machine the process started on. Every test evaluated the rule in the same zone the
code did, so the test and the defect agreed with each other — and it sat inside code reporting as fully
covered. Found by static analysis rather than by anything failing. See
[Coverage and quality](Coverage-and-Quality) for why 99% line coverage did not help.

**DEF-005 — test group selection silently ignored.** maven-failsafe-plugin 3.6.0 dropped support for
`suiteXmlFiles`. It accepts the parameter, warns that it is unsupported, and runs every test class
anyway. A "smoke" run was quietly a full regression run. Found by counting the tests that actually
executed and comparing with the number the group should contain — not by anything failing.

**DEF-007 — start script reports healthy for a server it did not start.** The new process failed to
bind the port, died, and the health poll was answered by a stale server left over from an earlier run.
The script reported success and the suites ran against an old build. That is how a green pipeline
certifies code that was never deployed.

**DEF-011 — `stalenessOf` escapes as a CDP error on a CI runner.** Waiting for an element to go stale is
the textbook way to detect navigation. When ChromeDriver discards a document mid-navigation it can raise
`WebDriverException: Node with given id does not belong to the document`, which `stalenessOf` does not
catch. It passed consistently on a developer machine and failed on the runner. The fix asks the document
about itself instead, so it never touches a reference that may already be dead.

**DEF-013 — a language switch with nowhere to go.** The bilingual header is built from a model
attribute supplied by a `@ControllerAdvice` scoped to named controller types. A new screen has to be
added to that list, and nothing fails when it is not: Thymeleaf renders the null as an empty string, so
the page serves a 200 with a switch that looks finished and strands every French reader. Found by a
test written for the new console, not by looking at it. The regression guard walks **every console
path** rather than the list of controllers — a test reading the list would have agreed with the defect.

**DEF-014 — the documentation's own numbers had drifted.** Eight figures disagreed across five
documents: 171 tests in one place and 165 in another, twelve defects here and nine there. Nothing was
miscalculated and no user was affected, which is exactly why it matters — this project's argument is
that its claims can be checked, and a reader who finds two numbers for the same fact has a reason to
stop checking the others. Fixed by generating the counts instead of typing them: a script reads the
last run's surefire XML and Cucumber JSON, every documented figure is marked with which total it
claims to be, and CI fails on any disagreement.

Both are the same defect in different materials — a list kept in step with a population by somebody
remembering — and both were fixed by checking the population instead of the list.

## How defects were managed

Found → reproduced → recorded with steps and root cause → fixed on a branch → the fix's regression test
added → closed by the pull request that merged it. The full workflow is in
[`docs/agile-workflow.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/agile-workflow.md).
