# Coverage and quality

Three tools, three different questions. Full detail in
[`docs/coverage.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/coverage.md)
and
[`docs/code-quality.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/code-quality.md).

| Tool | Question it answers | Blocks a merge? |
|---|---|---|
| **JaCoCo** | Which lines and branches were executed? | No |
| **SonarQube Cloud** | What is wrong with code nobody wrote a test for? | Only through its own pull-request check |
| **Codecov** | How much of *this pull request* is covered? | No — informational by configuration |

## The measured figure

| Metric | In-process | Full-stack |
|---|---:|---:|
| Line | 91.5% | **98.4%** |
| Branch | 83.3% | **87.6%** |
| Class | 95.2% | **100%** |

![JaCoCo full-stack coverage report](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/15-coverage-jacoco.png)

## Why there are two columns

`jacoco:prepare-agent` instruments the JVM Surefire forks. That measures the application's own 66 unit
and integration tests perfectly well and captures **nothing** from the API, UI, BDD and smoke suites,
because those drive the application over HTTP in a *separate process*. From the test JVM's point of
view, no class in `billing-app` was ever called.

The effect is not subtle: the SOAP package, exercised exclusively by black-box tests, reports **44%
in-process and 94% full-stack**, and `BillingApplication.main` goes from 33% to 100% — `main` only runs
when the real application starts. Publishing the in-process number alone would show the black-box suites
contributing nothing while they are the only thing testing several classes.

So the agent is attached to the *application* process, and the execution data merged afterwards. CI does
this across jobs: the build job contributes the in-process data, each suite job contributes what the
agent recorded in the application it drove, and a final job merges all five and renders one report.
**No suite is run twice to produce it.**

## Branch coverage is the honest number

87.6%, and it barely improves when the black-box suites are included — because the missed branches
are unreachable through any route the application offers. Each is named individually in
[`docs/coverage.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/coverage.md):
a `policy == null` guard on invoices that are always created against a policy, a `CANCELLED` check in
`refreshStatus` that cannot fire because a cancelled invoice is refused earlier, a `rawAmount == null`
branch that an HTML form cannot produce because it submits an empty string.

## There is no coverage gate, deliberately

A threshold set before seeing the numbers is arbitrary; one set to match them is decoration. There is a
sharper reason here: several of the remaining branches are unreachable defensive code, so a gate would
eventually be satisfied by writing tests that construct impossible states. That is worse than the gap.

The figure is published and every gap is named instead.

## What 99% does not mean

A line counts as covered if a test executed it, whether or not any assertion looked at what it did.

**This is not hypothetical here — it produced a real defect.** DEF-012: whether an invoice was
`OVERDUE` was decided by `LocalDate.now()`, which reads the host's default time zone. Every one of the
Every test evaluated that rule in the same zone the code did, so the test and the defect agreed with each
other, and the lines involved reported as fully covered. Static analysis found it; no amount of coverage
would have.

The one still open: `Invoice` has **no `@Version`**, and two concurrent payments against the same
invoice are untested — the load plan gives every thread its own invoice. Under real concurrency both
could read the same balance and both be accepted.

Coverage found real things here all the same: six dead members with zero call sites, since deleted, and
two endpoints at zero lines covered — one of which deletes every row in the database.

## Related

- [Test report](Test-Report) — the run these figures came from
- [CI and CD](CI-CD) — where the merge happens
