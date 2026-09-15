# Manual test cases

Test cases written in the form a QA team would keep them, with the automation status of each recorded
honestly. "Automated" means a named test actually covers the case — the mapping is in
[`requirements-traceability-matrix.md`](requirements-traceability-matrix.md).

**Environment for all cases:** application started with `scripts/start-app.sh`, console at
`http://localhost:8080/invoices`, baseline seed data loaded.

**Seeded fixtures referenced below:**

| Invoice | Total | State |
|---|---|---|
| `SEED-INV-001` | 360.00 | Unpaid, due in 25 days |
| `SEED-INV-002` | 360.00 | Partially paid, 180.00 outstanding |
| `SEED-INV-003` | 480.00 | Paid in full |
| `SEED-INV-004` | 200.00 | Overdue, nothing paid |
| `SEED-INV-005` | 600.00 | Cancelled |
| `SEED-INV-006` | 270.00 | On a lapsed policy |

**Seeded billing accounts referenced below:**

| Account | Insured | Term total | Why it exists |
|---|---|---|---|
| `ACCT-100001` | Dominique Fortin | 1591.60 | Premium and tax divide evenly across twelve |
| `ACCT-100002` | Élise Marchand | 1112.00 | Does **not** divide evenly, and carries a returned payment and its fee |

`ACCT-100002` is the fixture for anything about rounding or failed payments. The accented name is
deliberate: it is a UTF-8 round trip through JSON, JPA, Thymeleaf and SOAP that a test would otherwise
have to contrive.

---

## Payment processing

### TC-001 — Record a partial payment

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — UI, API and BDD |

**Preconditions:** An unpaid invoice for 450.00 exists.

| Step | Action | Expected result |
|---|---|---|
| 1 | Open the invoice detail page | Total 450.00, paid 0.00, outstanding 450.00, status `UNPAID` |
| 2 | Enter amount `150.00`, method `CARD` | Field accepts the value |
| 3 | Click **Record payment** | Page confirms the payment |
| 4 | Read the summary | Paid 150.00, outstanding 300.00, status `PARTIALLY_PAID` |
| 5 | Read the payment history | One row, 150.00, `CARD` |

---

### TC-002 — Settle an invoice with a final payment

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — UI, API and BDD |

**Preconditions:** An invoice for 450.00 with 300.00 outstanding.

| Step | Action | Expected result |
|---|---|---|
| 1 | Pay exactly 300.00 | Payment confirmed |
| 2 | Read the summary | Outstanding 0.00, status `PAID` |
| 3 | Observe the form area | A message states the invoice is settled in full |

---

### TC-003 — Instalments add up exactly

| | |
|---|---|
| **Priority** | Critical — rounding error would lose money |
| **Automated** | Yes — unit, API, UI and BDD |

**Preconditions:** An unpaid invoice for exactly 100.00.

| Step | Action | Expected result |
|---|---|---|
| 1 | Pay 33.33 | Outstanding 66.67 |
| 2 | Pay 33.33 | Outstanding 33.34 |
| 3 | Pay 33.34 | Outstanding 0.00, status `PAID` |
| 4 | Read payment history | Three rows; no residual balance of 0.01 |

---

### TC-004 — Overpayment is refused

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — unit, API, UI, BDD and Cypress |

**Preconditions:** An unpaid invoice for 100.00.

| Step | Action | Expected result |
|---|---|---|
| 1 | Attempt to pay 500.00 | Error shown: amount exceeds the outstanding balance |
| 2 | Read the summary | Outstanding still 100.00, paid still 0.00 |
| 3 | Read payment history | No payment recorded |

**API equivalent:** `422` with `code` `EXCEEDS_OUTSTANDING_BALANCE`.

---

### TC-005 — Overpayment is judged against the remaining balance

| | |
|---|---|
| **Priority** | Critical — a naive implementation compares against the invoice total |
| **Automated** | Yes — unit, API and BDD |

**Preconditions:** An invoice for 450.00 with 400.00 already paid.

| Step | Action | Expected result |
|---|---|---|
| 1 | Attempt to pay 60.00 | Refused — 60.00 exceeds the 50.00 remaining, even though it is well under the 450.00 total |
| 2 | Read the summary | Paid still 400.00 |

---

### TC-006 — Zero and negative amounts are refused

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — unit, API (data-driven), UI and BDD |

| Step | Action | Expected result |
|---|---|---|
| 1 | Attempt to pay `0.00` | Refused: amount must be greater than zero |
| 2 | Attempt to pay `-50.00` | Refused, same reason |
| 3 | Read the summary after each | Balance unchanged, nothing recorded |

**API equivalent:** `422` with `AMOUNT_NOT_POSITIVE`.

---

### TC-007 — Over-precise amounts are refused

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — unit and API (data-driven) |

| Step | Action | Expected result |
|---|---|---|
| 1 | Submit `10.001` via the API | `422` with `AMOUNT_SCALE_INVALID` |
| 2 | Submit `0.005` | Same |

**Note:** deliberately refused rather than rounded. Silently rounding a sub-cent amount is how a
platform accumulates unexplained differences.

---

### TC-008 — A settled invoice accepts nothing further

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — unit, API and BDD |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open a fully paid invoice | Status `PAID`, outstanding 0.00, settled message shown |
| 2 | Attempt to pay 10.00 | Refused: the invoice is already paid in full |

**API equivalent:** `422` with `INVOICE_ALREADY_PAID`.

---

### TC-009 — A cancelled invoice cannot be paid

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — unit, API, UI and BDD |

**Preconditions:** `SEED-INV-005`, or an invoice cancelled via the API.

| Step | Action | Expected result |
|---|---|---|
| 1 | Open the invoice | Status `CANCELLED`; a message states it is cancelled |
| 2 | Attempt to pay 10.00 | Refused, naming cancellation as the reason |

**API equivalent:** `422` with `INVOICE_CANCELLED`.

---

### TC-010 — An invoice on a non-active policy cannot be paid

| | |
|---|---|
| **Priority** | High — billing a customer who is not covered |
| **Automated** | Yes — unit, API, UI and BDD (both `LAPSED` and `CANCELLED`) |

**Preconditions:** `SEED-INV-006`, on a lapsed policy.

| Step | Action | Expected result |
|---|---|---|
| 1 | Attempt to pay 10.00 | Refused, naming the policy state |
| 2 | Repeat with a cancelled policy | Refused for the same reason |

**API equivalent:** `422` with `POLICY_NOT_ACTIVE`.

---

### TC-011 — Non-numeric and empty amounts are explained

| | |
|---|---|
| **Priority** | Medium — usability, but a generic failure here hides real problems |
| **Automated** | Yes — UI, Cypress and web-layer tests |

| Step | Action | Expected result |
|---|---|---|
| 1 | Type `abc` and submit | "Enter a valid amount, for example 125.00." |
| 2 | Submit with the field empty | "Enter a payment amount." |
| 3 | Observe the form | The rejected value is still visible for correction |

---

### TC-012 — Each payment method is recorded

| | |
|---|---|
| **Priority** | Medium |
| **Automated** | Yes — API |

| Step | Action | Expected result |
|---|---|---|
| 1 | Pay with `CARD`, `BANK_TRANSFER`, `DIRECT_DEBIT`, `CHEQUE` on separate invoices | Each history row shows the method as submitted |

---

## Invoice listing and status

### TC-013 — The list shows balances and statuses

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — UI, Cypress and API |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open `/invoices` | All six seeded invoices appear |
| 2 | Inspect the status column | All five states appear across the rows |
| 3 | Compare a row's outstanding figure with its detail page | They agree |

---

### TC-014 — The list can be filtered by status

| | |
|---|---|
| **Priority** | Medium |
| **Automated** | Yes — UI and API |

| Step | Action | Expected result |
|---|---|---|
| 1 | Filter by `PARTIALLY_PAID` | Only partially paid invoices listed |
| 2 | Filter by a status with no invoices | An empty-state message, not a blank table |

---

### TC-015 — An overdue invoice is flagged

| | |
|---|---|
| **Priority** | Medium |
| **Automated** | Yes — unit, API, UI and BDD |

| Step | Action | Expected result |
|---|---|---|
| 1 | View `SEED-INV-004` in the list | Status `OVERDUE` with an overdue marker |
| 2 | Pay it in full | Status `PAID`, marker gone |
| 3 | Partially pay a different overdue invoice | Status `PARTIALLY_PAID`, but still reported overdue |

**Note on step 3:** deliberate asymmetry. Only an untouched past-due invoice takes `OVERDUE` status, so
payment progress stays visible, while the overdue flag still reports the lateness.

---

## Customers and policies

### TC-016 — Create a customer

| | |
|---|---|
| **Priority** | Medium |
| **Automated** | Yes — API and application integration |

| Step | Action | Expected result |
|---|---|---|
| 1 | `POST /api/customers` with valid details | `201` and the persisted representation |
| 2 | Omit `lastName` | `400`, `VALIDATION_FAILED`, field error naming `lastName` |
| 3 | Use a malformed email | `400` with a field error on `email` |
| 4 | Reuse an existing email | `409` with `DUPLICATE_EMAIL`, not `400` |

---

### TC-017 — Unknown and malformed identifiers

| | |
|---|---|
| **Priority** | Medium |
| **Automated** | Yes — API and application integration |

| Step | Action | Expected result |
|---|---|---|
| 1 | `GET /api/customers/999999` | `404` with `NOT_FOUND` |
| 2 | `GET /api/customers/not-a-number` | `400` with `MALFORMED_REQUEST`, **not** `404` |
| 3 | `GET /api/customers/999999/policies` | `404`, not an empty list — an empty list would imply the customer exists |
| 4 | Open `/invoices/999999` in the browser | The console's not-found page, not a JSON error body |

---

## SOAP service

### TC-018 — Invoice status over SOAP

| | |
|---|---|
| **Priority** | Medium |
| **Automated** | Yes — API module SOAP tests |

| Step | Action | Expected result |
|---|---|---|
| 1 | `GET /ws/invoiceStatus.wsdl` | `200`, WSDL describing `GetInvoiceStatus`, generated from the XSD |
| 2 | POST a valid envelope (`Content-Type: text/xml`) for an unpaid invoice | `200`, status `UNPAID`, correct totals |
| 3 | Compare the SOAP figures with `GET /api/invoices/{id}` | Identical |
| 4 | POST for an unknown invoice number | SOAP fault, code `Client`, naming the invoice |
| 5 | POST with an empty invoice number | Rejected by the schema |

---

## Billing accounts, terms and the ledger

### TC-023 — An installment schedule collects exactly what the term is worth

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — domain unit, API, UI and BDD |

**Preconditions:** a term of 1000.00 premium, 90.00 tax and a 2.00 installment fee, monthly.

| Step | Action | Expected result |
|---|---|---|
| 1 | Open the term's payment schedule | Twelve installments listed, numbered 1 to 12 |
| 2 | Read installment 1 | 90.87 — premium 83.37, tax 7.50, fee 2.00 |
| 3 | Read installment 2 | 92.83 — premium 83.33 |
| 4 | Read installment 12 | 92.83 |
| 5 | Add up the twelve amounts due | Exactly 1112.00 |

**Why the remainder is on installment 1.** 1000.00 over twelve is 83.3333; twelve payments of 83.33
collect four cents short. The remainder goes on the **down payment** rather than the last installment
because the down payment is the figure quoted at bind time, and a cent stranded on the final
installment leaves a balance that trips a collection notice on a fully paid term.

---

### TC-024 — A payment settles the oldest unpaid installment first

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — domain unit, API and BDD |

**Preconditions:** `ACCT-100001`-shaped term, 1591.60, nothing paid.

| Step | Action | Expected result |
|---|---|---|
| 1 | Read the term balance | 1591.60 — the whole term is posted at new business |
| 2 | Read installments remaining | 12 |
| 3 | Pay 130.80 | Accepted |
| 4 | Read installment 1 | `PAID` |
| 5 | Read installment 2 | Not `PAID` |
| 6 | Read installments remaining, then the balance | 11, and 1460.80 |

---

### TC-025 — A returned payment reverses it, charges a fee and counts against the account

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — domain unit, API, UI and BDD |

**Preconditions:** a term of 1112.00 with a payment of 90.87 recorded.

| Step | Action | Expected result |
|---|---|---|
| 1 | Read the balance | 1021.13 |
| 2 | Return the payment for `INSUFFICIENT_FUNDS` | Accepted |
| 3 | Read the ledger | A `PAYMENT_RETURNED` line and an `NSF_FEE` line, both posted |
| 4 | Read the reversal's columns | Premium, tax and fee each the negative of the original |
| 5 | Read the balance | 1137.00 — 1112.00 restored, plus the 25.00 fee |
| 6 | Read installment 1 | `REVERSED`, not `SCHEDULED` |
| 7 | Read the account's tallies | 1 returned payment, 1 NSF |

**The distinction in step 7.** Every refused payment is a returned payment; only one refused for want
of funds is an NSF. Returning a payment for `ACCOUNT_CLOSED` instead charges no fee and leaves the NSF
tally at zero — a policy held on an account the bank closed has a payment problem, not a funding
problem, and collections treats the two differently.

---

### TC-026 — The same payment cannot be returned twice

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — API and BDD |

| Step | Action | Expected result |
|---|---|---|
| 1 | Pay, then return the payment | Accepted |
| 2 | Return the same payment again | `422`, code `PAYMENT_ALREADY_RETURNED` |
| 3 | Read the balance | Unchanged by the second attempt — one fee, not two |

`422` rather than `400`: the request is well formed and was refused by a billing rule. That split is a
hard rule throughout this API.

---

### TC-027 — Bank details cannot be read back in full, anywhere

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — domain unit, API and Cypress |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open the account summary | Institution `***`, branch `*****`, account `****871` |
| 2 | `GET /api/accounts/ACCT-100002` | The same masked values; no field holds more |
| 3 | Search the whole response for an unmasked number | Absent |
| 4 | Read `BankAccountReference` | No field exists that could hold a full number |

**Why step 4 is the real test.** Steps 1–3 would also pass if a filter were masking on the way out,
and a filter can be bypassed by the next endpoint somebody adds. The account number is never stored, so
there is nothing to leak — the test passes for a structural reason rather than a defensive one.

---

### TC-028 — The account summary answers "what do I owe and when"

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — UI and Cypress |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open `/accounts/ACCT-100002` | Insured and account reference in the context bar |
| 2 | Read the billing panel | Total balance, unapplied amount, next payment date and amount |
| 3 | Read the tallies | Returned payments and NSFs to date, both 1 |
| 4 | Read the payment panel | Plan, method, holder, and the masked bank details |

---

### TC-029 — Every line of the ledger agrees with the balance beside it

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — domain unit, API, UI and BDD |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open the transaction history | Newest line first |
| 2 | Read the columns | Amount split into premium, tax, fee and suspense |
| 3 | Walk the lines oldest to newest, adding the amounts | Each line's running balance equals the sum so far |
| 4 | Compare the newest line's balance with the term balance | Identical |

The balance is **derived** from the ledger rather than stored, so step 3 is an invariant rather than a
spot check: there is no second copy of the figure that could disagree.

---

### TC-030 — The console is published in both official languages

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — application, UI, BDD and Cypress |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open any console page | English |
| 2 | Click **Français** | The same page, same query string, in French |
| 3 | Read a money figure | `1 591,60 $` where English shows `$1,591.60` |
| 4 | Read a date | Formatted for the locale, not reformatted English |
| 5 | Read a status pill | Translated words, identical `data-status` attribute |
| 6 | Search the rendered page for `??` | Absent — Thymeleaf writes `??key??` for a missing translation |

Step 5 is why the automation asserts attributes rather than display copy: a suite reading the words
would pass in English and fail in French while the application behaved identically.

---

### TC-031 — The agent console shows the whole book with a total that adds up

| | |
|---|---|
| **Priority** | High |
| **Automated** | Yes — application, UI, BDD and Cypress |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open `/agent` | A row per term: policy, insured, product, term status, effective, expiry, balance |
| 2 | Read the order | Ascending by policy number |
| 3 | Add up the balance column | Equals the **Total** row |
| 4 | Click a policy number | That term's header and panels appear below; the row is marked as selected |
| 5 | Change tab | The same term stays selected |

---

### TC-032 — The agent and the policyholder are shown the same figures

| | |
|---|---|
| **Priority** | Critical |
| **Automated** | Yes — application, UI and BDD |

| Step | Action | Expected result |
|---|---|---|
| 1 | Open a term's schedule on `/accounts/{ref}/terms` | Twelve amounts due |
| 2 | Open the same term's schedule on `/agent` | The same twelve amounts, in the same order |
| 3 | Repeat for the transaction history | The same running balances |

**Why this is a test and not an assumption.** Both screens render one Thymeleaf fragment, so they
cannot disagree by construction — which is exactly the kind of claim that stops being true the first
time somebody is in a hurry and copies the markup. An agent quoting a balance the customer cannot see
on their own screen is the failure being guarded against.


## Not automated

Cases kept manual, with the reason:

| Case | Why it is manual |
|---|---|
| TC-019 — Console is legible at 1280×720 and 1920×1080 | No viewport assertions exist. Visual judgement, low risk for a server-rendered table. |
| TC-020 — H2 console at `/h2-console` is reachable for debugging | A development convenience, not product behaviour. |
| TC-021 — Application log contains no stack traces after a clean suite run | Checked by reading the log; asserting on log contents would be brittle. |
| TC-022 — Reset endpoint is absent when `qa.test-support.enabled` is false | The *absence* case stays manual: automating it needs a second application context with different properties, which is more machinery than the risk warrants. The endpoint's **behaviour** when enabled is now automated in `ResetEndpointIT`, after coverage showed it at zero lines covered. |
| TC-033 — The agent console stays readable with a book of several hundred terms | The grid is not paginated (see the note in `AgentConsoleWebController`). Until it is, this is a judgement about legibility rather than a pass or fail, and automating it would assert a threshold nobody has agreed. |
| TC-034 — French copy reads as insurance French, not as translated English | Parity and formatting are automated; register and terminology are not machine-checkable. A reviewer who works in French has to read it. |
