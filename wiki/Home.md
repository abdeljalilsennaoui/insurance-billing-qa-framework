# Insurance Billing QA Framework

End-to-end QA automation for a simulated insurance billing platform — customers, policies, invoices,
policy terms with installment schedules and an append-only transaction ledger — built as portfolio work
for a test automation role.

**[Repository](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework)** ·
**[Test execution report](Test-Report)** ·
**[Running the suites](Running-the-Suites)**

---

## What is here

The repository contains two halves, deliberately kept apart:

- **The system under test** — a Spring Boot 3 application with a REST API, a contract-first SOAP
  endpoint and a server-rendered bilingual console, in two personas: the policyholder's own billing
  screens and an agent's portfolio view of the whole book.
- **The QA automation** — API, UI, BDD, smoke and performance suites that exercise it the way a test
  automation engineer would exercise a real billing platform.

The automation modules have **no dependency on the application's code**. They test the published HTTP
contract and the rendered DOM, not shared classes. If both sides shared an enum, renaming a constant
would change the test and the application together, and the suite would stay green while the contract
silently broke.

## The numbers

| | |
|---|---|
| Automated tests | **571**, all passing — [see the report](Test-Report) |
| Test levels | domain unit, application integration, API, UI, BDD, smoke, performance |
| Line coverage | **98.4%** full-stack · 87.6% branch, quality gate passing — [how that is measured](Coverage-and-Quality) |
| Defects found and fixed | **14**, each with steps and a fixing commit — [log](Defect-Log) |
| CI | six jobs on every pull request — [pipeline](CI-CD) |
| Version | **1.2.0** |

The test counts above are **generated** from the reports of the last run rather than typed — see
DEF-014 in the [defect log](Defect-Log), which is the defect that made that necessary.

## Start here

| If you want to… | Read |
|---|---|
| See what the application looks like and what a run produces | **[Test report](Test-Report)** — screenshots and measured results |
| Run it yourself | **[Running the suites](Running-the-Suites)** |
| Understand why the framework is built this way | **[Test strategy](Test-Strategy)** |
| Know what coverage actually proves here | **[Coverage and quality](Coverage-and-Quality)** |
| See what broke and how it was found | **[Defect log](Defect-Log)** |
| Look at the pipeline | **[CI and CD](CI-CD)** |

## What 1.2 added

The application under test was a flat invoice list until 1.2: a policy raised an invoice, a payment
reduced it. That is not the shape of commercial insurance billing, and the gap showed — the hardest
rule in the domain was "don't overpay".

1.2 gives it a domain worth testing:

- **Policy terms and installment schedules.** A term is posted to the ledger in full when it is bound;
  the schedule is a plan against that balance, not a set of separate charges. Schedules must sum back
  to the term exactly, and the rounding remainder lands on the down payment.
- **An append-only transaction ledger** with a running balance derived rather than stored, every amount
  split into premium, tax, fee and suspense.
- **Returned payments and NSF handling** — a reversal, a fee, and two counters that are deliberately not
  the same counter.
- **Bank details that cannot leak**, because no field holds a full account number.
- **A bilingual EN/FR console**, with bundle parity enforced by a unit test.
- **Two personas** — the policyholder's screens and an agent's portfolio grid — rendering the *same*
  markup, with tests that compare them figure for figure.

## A note on this wiki

The repository is the source of truth. These pages are a guided tour with the reasoning summarised;
every one of them links to the file in the repository that holds the full version, and that file is the
one kept up to date. The [test report](Test-Report) is the exception — it is a record of one run at one
point in time, so it does not drift, it ages.
