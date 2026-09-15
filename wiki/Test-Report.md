# Test execution report

> Mirrors [`docs/test-report.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/test-report.md) in the repository, which is the
> canonical copy. Related pages: [Coverage and quality](Coverage-and-Quality) · [Running the suites](Running-the-Suites).

A record of one complete run of every suite in this repository, against one application instance, on
one machine, with the evidence it produced.

| | |
|---|---|
| **Run date** | 2026-09-15 |
| **Commit under test** | `4f2cfde` (`main`), with the 1.2.0 version bump applied |
| **Application version** | `billing-app` 1.2.0, Spring Boot 3.5.16 |
| **Executed by** | Abdeljalil Sennaoui, locally |
| **Result** | **477 tests executed, 477 passed, 0 failed, 0 skipped** |

Everything below was produced by the run described in [How the run was performed](#how-the-run-was-performed).
Nothing in this document is an expected value copied from a specification: the counts come from the
Maven and Cypress output, the coverage figures from the JaCoCo XML, and the screenshots from the
application itself while the suites were running against it.

> **This report replaces the 1.1.0 one**, which described the run of 2026-09-11 against `8d5edff` —
> 165 tests, `billing-app` 1.0.0. That version is in the git history at the `v1.1.0` tag and is not
> restated here with new numbers, because a report is a record of a run and editing its figures would
> destroy the only thing it is for.

---

## 1. Summary

| Suite | Tests | Passed | Failed | Duration | Runner |
|---|---:|---:|---:|---:|---|
| Domain and service unit | 151 | 151 | 0 | — | JUnit 5 |
| Application integration (API + web layer) | 103 | 103 | 0 | — | JUnit 5 + MockMvc |
| *(the two above, as one Maven invocation)* | 254 | 254 | 0 | 9.1 s | |
| API automation (incl. 7 SOAP) | 104 | 104 | 0 | 2.8 s | TestNG + REST Assured |
| UI automation | 60 | 60 | 0 | 1 min 58 s | TestNG + Selenium 4 |
| BDD scenarios (24 API + 19 UI) | 43 | 43 | 0 | 42.6 s | Cucumber 7 + TestNG |
| Smoke | 12 | 12 | 0 | 2 s | Cypress 15 |
| Reset endpoint (`test-support` group) | 4 | 4 | 0 | 1.8 s | TestNG + REST Assured |
| **Total** | **477** | **477** | **0** | **≈ 3 min 15 s** | |

The API automation total is 108 including the four `test-support` tests,
which run separately for the reason given below.

The BDD run reports 43 scenarios over **238 steps** (96 API + 142 UI), all passing.

Durations are wall-clock for the Maven invocation of each suite, measured on the machine described in
[section 3](#3-environment) while the application was running with the JaCoCo agent attached. The agent
adds measurement overhead, so these are not performance numbers — for those see
[section 7](#7-performance).

**These counts are not typed.** `scripts/count-tests.sh` reads them out of the surefire and failsafe
XML and the Cucumber JSON this run produced, into [`test-inventory.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/test-inventory.md), and
`scripts/check-doc-numbers.sh` fails the build if a figure quoted in this document disagrees with it.
That is the fix for DEF-014 — see [section 10](#10-defects).

---

## 2. Scope

**In scope, and exercised by this run:**

| Area | Levels that covered it |
|---|---|
| Payment rules: partial, settling, overpayment, zero, negative, over-precise, cancelled, non-active policy | unit, integration, API, UI, BDD, Cypress |
| Invoice state transitions (`UNPAID` → `PARTIALLY_PAID` → `PAID`, `OVERDUE`, `CANCELLED`) | unit, integration, API, UI, BDD |
| Balance arithmetic and rounding | unit, integration, API |
| **Installment schedule generation, and that a schedule sums to its term exactly** | unit, API, UI, BDD, Cypress |
| **Allocation order — a payment settles the oldest unpaid installment first** | unit, API, BDD |
| **The transaction ledger, and its running balance as an invariant on every line** | unit, API, UI, BDD, Cypress |
| **Returned payments: reversal, NSF fee, and the two counters that are not the same counter** | unit, API, UI, BDD |
| **Bank detail masking, structurally rather than by filtering** | unit, API, Cypress |
| **The console in English and French, including bundle parity and locale formatting** | unit, integration, UI, BDD, Cypress |
| **The agent console, and that both personas are shown the same figures** | integration, UI, BDD, Cypress |
| Customer and policy creation, duplicate email conflict, validation | integration, API, BDD |
| Error semantics — 400 vs 404 vs 409 vs 422, and the `code` in each body | integration, API, BDD |
| Invoice console: listing, status filter, overdue flag, detail page, payment form, error and success banners, 404 page | UI, BDD, Cypress |
| Invoice status over SOAP, including the WSDL and the fault path | API (7 tests) |
| QA reset endpoint | API (`test-support` group, run alone) |

Requirement-by-requirement mapping, by test class and method name, is in
[`requirements-traceability-matrix.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/requirements-traceability-matrix.md) — 33 requirements, each
automated at least once. Every method name in that document was checked against the source before this
release; two that had gone stale were corrected.

**Out of scope for this run**, and stated here rather than left to be discovered:

- **Security testing.** No authentication exists in the application to test, and no scanning was run.
- **Accessibility.** No axe or WCAG checks. The console is server-rendered; that is a reason the risk
  is lower, not a reason it was tested.
- **Cross-browser.** Chrome only, in both Selenium and Cypress.
- **Concurrency against a single invoice or term.** The load plan gives every thread its own fixture,
  so two simultaneous payments against *the same* invoice are untested. `Invoice` has no `@Version`, so
  this is a plausible real defect the suite would not catch — see [section 9](#9-known-limitations).
- **Asynchronous client-side state.** The console renders on the server, so nothing here demonstrates
  waiting on an SPA's hydration or XHR.
- **A real database.** In-memory H2 throughout; no Testcontainers, because Docker is not installed on
  this machine.
- **French copy as French.** Bundle parity and locale formatting are automated. Whether the French
  reads as insurance French rather than as translated English is not machine-checkable and was not
  reviewed by a French-speaking reader.

---

## 3. Environment

| | |
|---|---|
| Machine | MacBook, macOS 27.0, arm64 |
| JDK | OpenJDK 21.0.12.1 (Homebrew, **arm64 native**) |
| Maven | 3.9.16 |
| Browser | Google Chrome 152.0.7977.83, headless (`--headless=new`), 1440×900 viewport |
| Chromedriver | 152.0.7977.82, resolved at runtime by Selenium Manager; nothing pinned or committed |
| Node | v26.8.2, Cypress 15.21.1 |
| Application | `billing-app.jar` on port 8080, in-memory H2, `--qa.test-support.enabled=true` |
| Coverage | JaCoCo 0.8.15 agent attached to the application process |

**The toolchain changed between 1.1.0 and this release.** The 1.1.0 run executed on an x86_64 JDK under
Rosetta 2 translation; macOS 27 removed Rosetta from this machine, and the toolchain was reinstalled
native for arm64. Every JVM figure moved at once as a result — suite durations here are roughly a third
of the 1.1.0 ones, and the performance numbers in [section 7](#7-performance) are about ten times
better. None of that is the application getting faster, and the two releases' timings should not be
compared.

---

## 4. How the run was performed

Exactly these commands, in this order:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

scripts/coverage.sh               # steps 1-4 and 6 below, in one script

# 1. Build, and run the application's own 254 unit and integration tests.
# 2. Start the application with the JaCoCo agent attached.
# 3. The black-box suites, against that one instance:
#      mvn -B verify -pl qa-api-tests
#      mvn -B verify -pl qa-ui-tests
#      mvn -B verify -pl qa-bdd-tests
# 4. The reset group last and alone: it wipes the database.
# 6. Stop the application - the agent writes its data from a shutdown hook - then merge and render.

# 5. The Cypress smoke suite, against its own instance.
scripts/start-app.sh
( cd cypress && ./node_modules/.bin/cypress run )
scripts/stop-app.sh

# 7. Evidence for this report.
scripts/capture-screenshots.sh

# 8. The counts in this document, derived from the reports above.
scripts/count-tests.sh
scripts/check-doc-numbers.sh
```

**Cypress runs against its own instance, and its coverage is not in the merged figure.** `coverage.sh`
stops the application at the end, because the JaCoCo agent writes its execution data from a shutdown
hook. Running Cypress after that means running it against nothing — which is exactly what happened on
the first attempt at this report, and is recorded here rather than tidied away.

**One application instance for the Java suites.** That matters: it is the arrangement under which
shared-state coupling between suites shows up. A suite that passes only against a freshly started
application is a suite with a hidden dependency on data nobody else has touched.

### A run that was discarded

The first attempt at this report failed in the UI suite with
`NoSuchSessionException: invalid session id: session deleted as the browser has closed the connection`.
Chrome died mid-suite; a chromedriver from an earlier run had been left behind, and that suite took
118 s against its usual 28 s. It is recorded here because the honest thing about an infrastructure
failure is to say it happened, and because the figures in this document come from the clean re-run
afterwards rather than from a partially-completed one.

---

## 5. Evidence

Screenshots are captured by
[`DocumentationScreenshots`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/docs/DocumentationScreenshots.java),
which drives the application through the **same page objects and the same locators as the UI suite**,
in the same headless Chrome at the same viewport. The images therefore show the application as the
tests see it, and they break when the page objects break rather than drifting quietly out of date.

The database is reset to the seeded baseline immediately before capture, so the listing shows the six
seeded invoices rather than the several hundred records a full suite run leaves behind. Anything the
capture then pays or cancels is created by the capture itself.

### 5.1 The invoice console

![Invoice console listing all invoices](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/01-invoice-console-all.png)

The seeded baseline: six invoices covering every state the domain can be in — unpaid, cancelled,
overdue, partially paid and paid — across three customers and four policies. Outstanding balances are
derived, not stored. This is the fixture every read-only scenario asserts against.

![Invoice console filtered to overdue invoices](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/02-invoice-console-filtered-overdue.png)

The status filter applied. Proves `GET /invoices?status=OVERDUE` narrows the table and that the overdue
flag is rendered next to the status badge. The UI suite waits for a *new document* here rather than for
an element to go stale — see DEF-011 in [`defect-reports.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/defect-reports.md) for why that
distinction cost a day.

### 5.2 Invoice detail and payment history

![Invoice detail page for a partially paid invoice](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/03-invoice-detail-partially-paid.png)

A seeded invoice with two instalments already received: total 360.00, paid 180.00, outstanding 180.00,
status `PARTIALLY_PAID`. Summary figures, payment history and the payment form are all on one page, and
every element the suite touches carries a `data-testid`.

### 5.3 A payment that is accepted

![Payment accepted, with confirmation banner and updated balance](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/04-payment-accepted.png)

120.00 paid against a 480.00 invoice through the console form. The banner confirms the amount, the
summary moves to paid 120.00 / outstanding 360.00 / `PARTIALLY_PAID`, and the payment appears in the
history with its method and reference. What is on screen is a fresh `GET` of the invoice: the
controller redirects after a successful payment rather than rendering the `POST` response, so a browser
refresh cannot resubmit the payment. The form is empty again for the same reason — compare the refused
attempt below, which re-renders in place and keeps the rejected value in the field.

### 5.4 Payments that are refused, and *why* they are refused

![Payment refused because it exceeds the outstanding balance](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/05-payment-rejected-exceeds-balance.png)

10,000.00 against a 360.00 balance. The API answers this with `422 EXCEEDS_OUTSTANDING_BALANCE` — a
billing rule refusing a well-formed request, not a validation error. The console shows the rule's own
message and the balance is untouched.

![Payment refused because the invoice is cancelled](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/06-payment-rejected-cancelled-invoice.png)

The same form against a cancelled invoice: refused for a different reason, with a different message,
and the page also states the invoice's state outright. Distinguishing *which* rule refused a payment is
the whole point of the status split documented in the [README](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/README.md#error-semantics); a suite
that only asserted "an error appeared" would pass if the two rules were swapped.

### 5.5 Terminal and error states

![A fully settled invoice](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/07-invoice-settled.png)

Settled in full: status `PAID`, outstanding 0.00, and the page says so explicitly rather than leaving an
empty form to be interpreted.

![The console's own not-found page](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/08-invoice-not-found.png)

`/invoices/999999`. The console returns its own 404 page, not the JSON error body the REST API returns
for the same missing resource — the web controller declares a local `@ExceptionHandler` for exactly this
reason. Cypress asserts on it too.

### 5.6 The policyholder's billing account

![Account summary: balance, next payment, and the two failed-payment tallies](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/09-account-summary.png)

`ACCT-100002`, the seeded account whose premium does not divide evenly and whose first payment was
returned. The billing panel answers "what do I owe and when does it come out"; the payment panel shows
the plan, the method and the bank details **masked**. The masking is structural: `BankAccountReference`
has no field that could hold a full account number, so there is nothing for an endpoint, a log or a
SOAP response to leak.

![Payment schedule: the down payment carries the rounding remainder](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/10-term-schedule.png)

Twelve installments summing to exactly 1112.00. 1000.00 of premium over twelve is 83.3333, so twelve
payments of 83.33 would collect four cents short; the remainder goes on the **down payment** rather
than the last installment, because the down payment is the figure quoted at bind time and a cent
stranded on the final installment leaves a balance that trips a collection notice on a fully paid term.
Installment 1 also reads `REVERSED` — the payment against it was returned.

![Transaction history with a running balance](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/11-term-transactions.png)

New business, a payment, its reversal and the fee, newest first, every amount split into premium, tax,
fee and suspense. The running balance on each line is **derived** from the lines below it rather than
stored, so there is no second copy of the figure that could disagree — which makes "every line agrees
with the balance beside it" an invariant rather than a spot check.

![The same account in French](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/12-account-summary-french.png)

The same screen in French. The figures are identical; the words and the number format are not
(`1 591,60 $` against `$1,591.60`). Every status carries a language-independent `data-status`
attribute, which is why the automation asserts attributes rather than display copy: a suite reading the
words would pass in English and fail in French while the application behaved identically.

### 5.7 The agent's console

![Agent console: every term on the books, ordered by policy number and totalled](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/13-agent-console-portfolio.png)

The whole book in one grid — policy, insured, product, term status, effective, expiry, balance —
ordered by policy number rather than by insertion order, with a **Total** row that is summed from the
rows on screen rather than queried separately, so it cannot disagree with the column above it.

![The same ledger, read from the agent's console](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/14-agent-console-ledger.png)

The same ledger as 5.6, for the same term, read by the other persona. Both screens render the **same
Thymeleaf fragment**, so they cannot disagree by construction — and that is exactly the kind of claim
that stops being true the first time somebody is in a hurry, which is why it is asserted at three
levels rather than assumed.

---

## 6. Coverage

Measured during this run, from the merged JaCoCo execution data:

| Metric | Covered / total | In-process | Full-stack |
|---|---|---:|---:|
| Line | 1290 / 1311 | 91.5% | **98.4%** |
| Branch | 163 / 186 | 83.3% | **87.6%** |
| Instruction | 5408 / 5524 | 90.7% | **97.9%** |
| Method | 376 / 385 | 91.7% | **97.7%** |
| Class | 75 / 75 | 96.0% | **100%** |

![JaCoCo full-stack coverage report](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/15-coverage-jacoco.png)

Both columns come from this run and reproduce the figures recorded in [`coverage.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/coverage.md)
exactly. The gap between them is the point: the API, UI and BDD suites drive the application in a
**separate JVM**, so an ordinary in-process coverage run sees nothing they do.

The application grew from 42 classes to 75 in this release, so these are not the same denominators as
the 1.1.0 report's and the percentages should not be read as a trend.

All 21 missed lines and 23 missed branches are named class by class in [`coverage.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/coverage.md).
Almost all are defensive code whose failing side cannot be reached through the application — and one is
not: the agent console's empty-portfolio branch is perfectly reachable and simply has no suite that
arranges a database with nothing in it. It is listed as the real gap it is. There is deliberately **no
coverage gate**.

### What this coverage figure does not say

98.4% of lines were executed. It does not follow that 98.4% of the behaviour is verified: a line is
"covered" if a test ran it, whether or not any assertion looked at what it did. The concurrency gap in
[section 9](#9-known-limitations) sits inside code that reports as fully covered.

---

## 7. Performance

The load plan was re-run for this release, natively:

![JMeter dashboard for the invoice API load test](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/18-jmeter-dashboard.png)

1050 samples, 0% errors, 1.3 ms mean, 3 ms p95, 227.9 requests/s total — including the two new samplers
over the installment schedule and the transaction ledger, each with a per-thread account and bound term
so no two threads share a schedule.

Read [`perf/README.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/perf/README.md) before quoting any of that. The load generator and the
application shared one machine, the database was in-memory, and the run lasted five seconds. It shows
the paths work under concurrent load. It is not a capacity measurement.

**The figures are roughly ten times the 1.1.0 ones and none of that is the application.** That run
executed under Rosetta 2 translation; this one is native arm64. Both tables are kept in
`perf/README.md`, with a note that they are not comparable to each other.

---

## 8. BDD scenario reports

![Cucumber report for the API scenarios](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/16-cucumber-api-scenarios.png)

![Cucumber report for the browser scenarios](https://raw.githubusercontent.com/wiki/abdeljalilsennaoui/insurance-billing-qa-framework/screenshots/17-cucumber-ui-scenarios.png)

24 API scenarios over 96 steps and 19 browser scenarios over 142 steps, all passing. The two runners are
split by tag so a browser problem and a contract problem cannot arrive as the same red result.

---

## 9. Known limitations

Stated here because a report that only lists what passed is an advertisement.

1. **No optimistic locking on `Invoice`.** There is no `@Version`, and two concurrent payments against
   the same invoice are untested. Under load both could read the same balance and both be accepted,
   overpaying the invoice. This is the most likely real defect in the application, and it sits in code
   that reports as covered.
2. **`GET /api/invoices` has no pagination, and neither does the agent console's grid.** The grid
   renders every term on the books and hydrates every schedule and ledger with them. Harmless at two
   rows, linear at six hundred thousand. Both are noted in the source rather than left to be found.
3. **H2, not the database a billing platform would use.** Query latency and connection pooling — the
   costs that dominate in production — are absent from every figure here.
4. **Chrome only.** No Firefox, Safari or Edge run.
5. **Cypress overlaps the Selenium coverage.** Justified as triangulation across two independent
   toolchains; it is still duplication, and it is listed as such rather than counted twice.
6. **The Jenkins pipeline was not executed.** [`Jenkinsfile`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/Jenkinsfile) is written against this
   project's real layout and commands, but no Jenkins controller was available. GitHub Actions is the
   pipeline that actually gates merges.
7. **The two-consecutive-passes isolation check was not repeated at 1.2.0.** The 1.1.0 measurement
   stands as the dated measurement it is; it has not been restated for a suite it did not run against.

---

## 10. Defects

Fourteen defects were found and fixed while building the project, each documented with steps, root cause
and fixing commit in [`defect-reports.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/defect-reports.md). **None is open.**

This release added two, and both were found by looking rather than by a suite going red:

- **DEF-013** — the agent console shipped a language switch with no destination, because its controller
  was missing from a `@ControllerAdvice`'s `assignableTypes`. Thymeleaf renders the null as an empty
  string, so the page served a 200 and looked finished. Found by a test written for the new console.
- **DEF-014** — the documentation's own test counts had drifted apart from the suite and from each
  other: 171 tests in one document and 165 in another, twelve defects here and nine there. Fixed by
  generating the counts rather than typing them, with a CI check that fails on any disagreement.

Three earlier ones (DEF-005, DEF-007, DEF-008) were defects in the *test infrastructure* rather than the
application — a test group selector silently ignored, a start script reporting healthy for a server it
had not started, and Cucumber hooks never registered. Those are the dangerous ones: each produced a
green or healthy result while testing less than it claimed. DEF-013 and DEF-014 are quieter members of
the same family, and both were fixed the same way: check the population, not the list.

---

## 11. Reproducing this report

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./scripts/run-all.sh              # every suite, start to finish
./scripts/coverage.sh             # the same plus full-stack coverage
./scripts/capture-screenshots.sh  # regenerate every image in this document
./scripts/count-tests.sh          # regenerate docs/test-inventory.md
./scripts/check-doc-numbers.sh    # hold this document's figures to it
```

The screenshots are committed under [`screenshots/`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/screenshots) and are regenerated by the third
command, so a change to the console shows up as an image diff in the pull request that caused it. The
counts are regenerated by the fourth, for the same reason.
