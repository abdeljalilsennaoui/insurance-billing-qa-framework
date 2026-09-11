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

## Not automated

Cases kept manual, with the reason:

| Case | Why it is manual |
|---|---|
| TC-019 — Console is legible at 1280×720 and 1920×1080 | No viewport assertions exist. Visual judgement, low risk for a server-rendered table. |
| TC-020 — H2 console at `/h2-console` is reachable for debugging | A development convenience, not product behaviour. |
| TC-021 — Application log contains no stack traces after a clean suite run | Checked by reading the log; asserting on log contents would be brittle. |
| TC-022 — Reset endpoint is absent when `qa.test-support.enabled` is false | The *absence* case stays manual: automating it needs a second application context with different properties, which is more machinery than the risk warrants. The endpoint's **behaviour** when enabled is now automated in `ResetEndpointIT`, after coverage showed it at zero lines covered. |
