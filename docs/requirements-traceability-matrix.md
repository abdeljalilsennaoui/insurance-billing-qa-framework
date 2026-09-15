# Requirements traceability matrix

Maps each requirement to its manual test case and to the automated tests that cover it, by real class
and method name. Every name below was taken from the source, not from memory; they can be checked with
`grep -rn "<methodName>" .`

**Coverage summary:** 33 requirements, all automated at least once. 16 are covered at four or more
levels; these are the critical money-handling rules.

Level abbreviations: **U** domain unit · **I** application integration · **A** API automation ·
**S** Selenium UI · **B** BDD · **C** Cypress · **P** performance

---

## Payment rules

### REQ-01 — A partial payment reduces the outstanding balance and sets `PARTIALLY_PAID`

| Level | Class | Method |
|---|---|---|
| U | `InvoiceBalanceTest` | `partialPaymentReducesBalance` |
| I | `InvoicePaymentApiIntegrationTest` | `partialPaymentIsAccepted` |
| A | `InvoicePaymentApiIT` | `partialPaymentReducesTheOutstandingBalance` |
| S | `InvoicePaymentUiIT` | `aPartialPaymentReducesTheOutstandingBalance` |
| B | `invoice_payment_api.feature` | "A partial payment reduces what is still owed" |
| B | `invoice_payment_ui.feature` | "Recording a partial payment updates the balance on screen" |
| C | `invoice_console_smoke.cy.js` | "records a valid payment and updates the balance" |

**Manual:** TC-001

---

### REQ-02 — A payment equal to the outstanding balance settles the invoice

| Level | Class | Method |
|---|---|---|
| U | `InvoiceBalanceTest` | `fullPaymentSettlesInvoice` |
| I | `InvoicePaymentApiIntegrationTest` | `fullPaymentSettlesInvoice` |
| A | `InvoicePaymentApiIT` | `payingTheOutstandingBalanceSettlesTheInvoice` |
| S | `InvoicePaymentUiIT` | `payingTheOutstandingBalanceSettlesTheInvoice` |
| B | `invoice_payment_api.feature` | "Paying the outstanding balance settles the invoice" |
| B | `invoice_payment_ui.feature` | "Settling an invoice in the console" |

**Manual:** TC-002

---

### REQ-03 — Sequential partial payments settle an invoice exactly, with no rounding loss

| Level | Class | Method |
|---|---|---|
| U | `InvoiceBalanceTest` | `sequentialPartialPaymentsSettleInvoiceExactly` |
| A | `InvoicePaymentApiIT` | `sequentialPartialPaymentsSettleTheInvoiceExactly` |
| S | `InvoicePaymentUiIT` | `severalPartialPaymentsSettleTheInvoiceThroughTheUi` |
| B | `invoice_payment_api.feature` | "Instalments add up exactly" |

**Manual:** TC-003 · **Test data:** 33.33 + 33.33 + 33.34 against 100.00

---

### REQ-04 — A payment exceeding the outstanding balance is refused

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `overpaymentIsRejected` |
| A | `PaymentValidationApiIT` | `invalidAmountsAreRefusedWithTheirOwnReason` (rows `450.01`, `1000.00`) |
| S | `InvoicePaymentUiIT` | `anOverpaymentIsRefusedAndTheBalanceIsUnchanged` |
| B | `invoice_payment_api.feature` | Scenario Outline "amounts beyond what is owed" |
| B | `invoice_payment_ui.feature` | "An overpayment is refused with a visible explanation" |
| C | `invoice_console_smoke.cy.js` | "refuses an overpayment with a visible message and leaves the balance alone" |

**Manual:** TC-004 · **Error code:** `EXCEEDS_OUTSTANDING_BALANCE` (HTTP 422)

---

### REQ-05 — Overpayment is measured against the remaining balance, not the invoice total

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `overpaymentIsMeasuredAgainstRemainingBalance` |
| A | `PaymentValidationApiIT` | `overpaymentIsMeasuredAgainstTheRemainingBalanceNotTheInvoiceTotal` |
| B | `invoice_payment_api.feature` | "Overpayment is judged against what remains, not the original total" |

**Manual:** TC-005 · **Test data:** 60.00 against 50.00 remaining of a 450.00 invoice

---

### REQ-06 — A zero or negative amount is refused

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `nonPositiveAmountIsRejected` (`0.00`, `0`, `-0.01`, `-250.00`) |
| I | `InvoicePaymentApiIntegrationTest` | `zeroAmountIsRefusedWith422` |
| A | `PaymentValidationApiIT` | `invalidAmountsAreRefusedWithTheirOwnReason` |
| S | `InvoicePaymentUiIT` | `aZeroAmountIsRefusedWithAVisibleMessage`, `aNegativeAmountIsRefused` |
| B | `invoice_payment_api.feature` | Scenario Outline "amounts that are not a positive sum of money" |

**Manual:** TC-006 · **Error code:** `AMOUNT_NOT_POSITIVE` (HTTP 422)

---

### REQ-07 — An amount with more than two decimal places is refused

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `amountWithTooManyDecimalPlacesIsRejected` (`10.001`, `0.005`, `99.9999`) |
| I | `InvoicePaymentApiIntegrationTest` | `overPreciseAmountIsRefusedWith422` |
| A | `PaymentValidationApiIT` | `invalidAmountsAreRefusedWithTheirOwnReason` |
| B | `invoice_payment_api.feature` | Scenario Outline row `10.001` |

**Manual:** TC-007 · **Error code:** `AMOUNT_SCALE_INVALID` (HTTP 422)

---

### REQ-08 — A fully paid invoice accepts no further payment

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `paymentAgainstSettledInvoiceIsRejected` |
| I | `InvoicePaymentApiIntegrationTest` | `payingSettledInvoiceIsRefused` |
| A | `PaymentValidationApiIT` | `invoicesInAnUnpayableStateRefusePayment` (row `settled`) |
| B | `invoice_payment_api.feature` | "A settled invoice accepts nothing further" |

**Manual:** TC-008 · **Error code:** `INVOICE_ALREADY_PAID` (HTTP 422)

---

### REQ-09 — A cancelled invoice cannot be paid

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `paymentAgainstCancelledInvoiceIsRejected` |
| I | `InvoicePaymentApiIntegrationTest` | `payingCancelledInvoiceIsRefused` |
| A | `PaymentValidationApiIT` | `invoicesInAnUnpayableStateRefusePayment` (row `cancelled`) |
| S | `InvoicePaymentUiIT` | `aCancelledInvoiceRefusesPayment` |
| B | `invoice_payment_api.feature` | "A cancelled invoice cannot be paid" |
| B | `invoice_payment_ui.feature` | "A cancelled invoice is marked as such and refuses payment" |

**Manual:** TC-009 · **Error code:** `INVOICE_CANCELLED` (HTTP 422)

---

### REQ-10 — An invoice on a non-active policy cannot be paid

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `paymentOnInactivePolicyIsRejected` (`LAPSED`, `CANCELLED`) |
| I | `InvoicePaymentApiIntegrationTest` | `payingOnLapsedPolicyIsRefused` |
| A | `PaymentValidationApiIT` | `invoicesInAnUnpayableStateRefusePayment` (rows `policyLapsed`, `policyCancelled`) |
| S | `InvoicePaymentUiIT` | `anInvoiceOnALapsedPolicyRefusesPayment` |
| B | `invoice_payment_api.feature` | Scenario Outline "An invoice on a policy that is no longer active" |

**Manual:** TC-010 · **Error code:** `POLICY_NOT_ACTIVE` (HTTP 422)

---

### REQ-11 — A refused payment leaves the invoice completely unchanged

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `rejectedPaymentLeavesInvoiceUnchanged` |
| A | `PaymentValidationApiIT` | `aRefusedPaymentLeavesEarlierPaymentsIntact` |
| S | `InvoicePaymentUiIT` | `anOverpaymentIsRefusedAndTheBalanceIsUnchanged` |

**Manual:** covered as the final step of TC-004, TC-006 and TC-009

---

### REQ-12 — The most actionable rejection reason is reported first

| Level | Class | Method |
|---|---|---|
| U | `InvoicePaymentRulesTest` | `amountIsValidatedBeforeInvoiceState` |

**Manual:** none · **Note:** only unit-tested. The rule is about ordering inside one method, and an
API-level test would assert the same thing more slowly through more layers.

---

### REQ-13 — The paid amount is the sum of recorded payments, in order

| Level | Class | Method |
|---|---|---|
| U | `InvoiceBalanceTest` | `amountPaidIsSumOfPayments` |
| I | `InvoicePaymentApiIntegrationTest` | `paymentsAreListedInOrder` |
| A | `InvoicePaymentApiIT` | `paymentsAreReturnedInTheOrderTheyWereReceived` |
| S | `InvoicePaymentUiIT` | `thePaymentHistoryListsEveryRecordedPayment` |

**Manual:** TC-001 step 5, TC-003 step 4

---

### REQ-14 — Every payment method is recorded as submitted

| Level | Class | Method |
|---|---|---|
| A | `InvoicePaymentApiIT` | `eachAcceptedPaymentMethodIsRecorded` |

**Manual:** TC-012

---

## Invoice status

### REQ-15 — An unpaid invoice past its due date is reported overdue

| Level | Class | Method |
|---|---|---|
| U | `InvoiceBalanceTest` | `unpaidInvoicePastDueDateIsOverdue`, `untouchedPastDueInvoiceIsPromotedToOverdue` |
| I | `InvoicePaymentApiIntegrationTest` | `pastDueInvoiceIsReportedOverdue` |
| A | `InvoicePaymentApiIT` | `anInvoicePastItsDueDateIsReportedOverdue` |
| S | `InvoicePaymentUiIT` | `anOverdueInvoiceSaysSoOnItsDetailPageWithoutRepeatingItself` |
| S | `InvoiceListUiIT` | `anOverdueInvoiceSaysSoInItsStatusWithoutRepeatingItself` |
| B | `invoice_payment_api.feature` | "An overdue invoice is reported as overdue until it is settled" |

**Manual:** TC-015

---

### REQ-16 — A settled or cancelled invoice is never reported overdue

| Level | Class | Method |
|---|---|---|
| U | `InvoiceBalanceTest` | `settledInvoiceIsNotOverdue`, `cancelledInvoiceIsNotOverdue`, `invoiceWithinDueDateIsNotOverdue` |
| A | `InvoicePaymentApiIT` | `payingAnOverdueInvoiceInFullClearsTheOverdueFlag` |
| A | `SoapInvoiceStatusIT` | `aSettledInvoiceIsReportedAsPaidWithNothingOutstanding` |

**Manual:** TC-015 step 2

---

### REQ-17 — A part-paid past-due invoice keeps `PARTIALLY_PAID` but still reads as overdue

| Level | Class | Method |
|---|---|---|
| U | `InvoiceBalanceTest` | `partPaidPastDueInvoiceKeepsPartiallyPaidStatus` |

**Manual:** TC-015 step 3 · **Note:** a deliberate asymmetry, unit-tested only because it is a pure
state-derivation rule.

---

### REQ-18 — Invoices can be listed and filtered by status

| Level | Class | Method |
|---|---|---|
| I | `InvoicePaymentApiIntegrationTest` | `invoiceListCanBeFilteredByStatus` |
| A | `InvoicePaymentApiIT` | `theInvoiceListCanBeFilteredByStatus`, `theInvoiceListIncludesDerivedBalanceFigures` |
| A | `PaymentValidationApiIT` | `anUnknownStatusFilterIsRejected` |
| S | `InvoiceListUiIT` | `theListCanBeFilteredToASingleStatus`, `filteringToAStatusWithNoInvoicesShowsAnEmptyStateRatherThanABlankTable` |
| C | `invoice_console_smoke.cy.js` | "lists the seeded invoices with their statuses" |

**Manual:** TC-013, TC-014

---

## API contract

### REQ-19 — Malformed requests are 400; rule violations are 422 with a named reason

| Level | Class | Method |
|---|---|---|
| I | `InvoicePaymentApiIntegrationTest` | `missingAmountIsA400`, `unparseableBodyIsA400` |
| A | `PaymentValidationApiIT` | `malformedBodiesAreRejectedAsBadRequestsNotRuleViolations` |
| A | `CustomerPolicyApiIT` | `aMissingRequiredFieldNamesTheOffendingField`, `anInvalidEmailIsRejected` |

**Manual:** TC-016 · **Note:** this is the requirement that makes every other rejection assertion
meaningful.

---

### REQ-20 — Unknown resources are 404; malformed identifiers are 400; conflicts are 409

| Level | Class | Method |
|---|---|---|
| I | `CustomerPolicyApiIntegrationTest` | `unknownCustomerReturns404`, `nonNumericCustomerIdReturns400`, `duplicateEmailReturns409` |
| A | `CustomerPolicyApiIT` | `anUnknownCustomerIsNotFound`, `aNonNumericCustomerIdIsABadRequestNotANotFound`, `reusingAnEmailIsAConflictNotAValidationFailure`, `listingPoliciesForAnUnknownCustomerIsNotFoundRatherThanAnEmptyList`, `aPolicyForAnUnknownCustomerIsNotFound` |
| A | `PaymentValidationApiIT` | `payingAnUnknownInvoiceIsNotFound` |
| I | `InvoiceConsoleWebTest` | `unknownInvoiceRendersNotFoundPage` |
| C | `invoice_console_smoke.cy.js` | "shows a not-found page for an unknown invoice instead of a JSON error" |

**Manual:** TC-017

---

## SOAP service

### REQ-21 — Invoice status is available over SOAP, with a fault for an unknown invoice

| Level | Class | Method |
|---|---|---|
| A | `SoapInvoiceStatusIT` | `theWsdlIsPublishedFromTheSchema`, `anUnpaidInvoiceIsReportedOverTheSoapService`, `soapAndRestReportTheSameBalanceForTheSameInvoice`, `aSettledInvoiceIsReportedAsPaidWithNothingOutstanding`, `anOverdueInvoiceIsFlaggedOverTheSoapService`, `anUnknownInvoiceReturnsASoapFaultRatherThanAnEmptyResponse`, `anEmptyInvoiceNumberIsRefusedByTheContract` |

**Manual:** TC-018 · **Note:** `soapAndRestReportTheSameBalanceForTheSameInvoice` exists specifically to
prove the two protocols cannot disagree.

---

### REQ-22 — A policy can be retrieved by id

| Level | Class | Method |
|---|---|---|
| A | `CustomerPolicyApiIT` | `aPolicyCanBeRetrievedById`, `anUnknownPolicyIsNotFound` |

**Manual:** TC-016 · **Note:** added after a coverage report showed `PolicyController.get` was never
called by any test.

---

### REQ-23 — The QA reset endpoint restores the seeded baseline

| Level | Class | Method |
|---|---|---|
| A | `ResetEndpointIT` | `resetRestoresTheSeededBaseline`, `theSeededBaselineCoversEveryInvoiceState` |

**Manual:** TC-022 · **Note:** added after a coverage report showed `TestSupportController.reset` at zero
lines covered. Runs in the `test-support` group, excluded from every other group because it deletes all
data.

---

## Non-functional

### NFR-01 — The API sustains concurrent reads and payment writes without errors

| Level | Artefact | Detail |
|---|---|---|
| P | `perf/invoice-api-load.jmx` | 10 threads, 1050 samples, 0% errors, 227.9 req/s |

**Results and limitations:** [`../perf/README.md`](../perf/README.md). **Not** a capacity measurement —
see the limitations section there.

---

## Billing accounts, terms and the ledger

### REQ-24 — An installment schedule sums to the term total exactly

| Level | Class | Method |
|---|---|---|
| U | `MoneyAllocationTest` | `everyAllocationSumsBackToItsTotal` |
| U | `InstallmentScheduleGeneratorTest` | `everyColumnReconcilesOnItsOwn` |
| A | `InstallmentScheduleApiIT` | `everyScheduleColumnReconcilesAgainstItsTerm` |
| S | `TermScheduleUiIT` | `theScheduleOnScreenCollectsExactlyWhatTheTermIsWorth` |
| B | `installment_payment_api.feature` | "The schedule collects exactly what the term is worth" |
| C | `billing_console_smoke.cy.js` | "shows the installment schedule adding up to the term exactly" |

**Manual:** TC-023

---

### REQ-25 — The rounding remainder lands on the down payment, not the last installment

| Level | Class | Method |
|---|---|---|
| U | `MoneyAllocationTest` | `remainderLandsOnTheFirstPart` |
| A | `InstallmentScheduleApiIT` | `anUnevenTermPutsTheOddCentsOnTheDownPayment` |
| S | `TermScheduleUiIT` | `theRoundingRemainderIsVisibleOnTheDownPayment` |
| B | `installment_payment_api.feature` | "The rounding remainder lands on the down payment" |

**Manual:** TC-023

---

### REQ-26 — A payment settles the oldest unpaid installment first

| Level | Class | Method |
|---|---|---|
| U | `PolicyTermLedgerTest` | `aPaymentSettlesTheOldestUnpaidInstallmentFirst` |
| A | `TransactionLedgerApiIT` | `onePaymentCoveringSeveralInstallmentsSettlesThemInOrder` |
| B | `installment_payment_api.feature` | "A payment settles the oldest installment first" |

**Manual:** TC-024

---

### REQ-27 — A returned payment reverses the posting, charges the fee and bumps the counters

| Level | Class | Method |
|---|---|---|
| U | `ReturnedPaymentTest` | `aReturnedPaymentRaisesAFeeOnTopOfTheRestoredBalance` |
| A | `ReturnedPaymentApiIT` | `aReturnedPaymentRestoresTheBalanceAndChargesAFee` |
| A | `ReturnedPaymentApiIT` | `theReversalNegatesTheOriginalPaymentColumnForColumn` |
| S | `TransactionLedgerUiIT` | `aReturnedPaymentAndItsFeeBothAppearOnTheLedger` |
| S | `AgentConsoleUiIT` | `aReturnedPaymentIsVisibleToTheAgentAsItIsToTheCustomer` |
| B | `installment_payment_api.feature` | "A returned payment puts the balance back and charges a fee" |
| B | `billing_console_ui.feature` | "A returned payment is visible to the policyholder" |

**Manual:** TC-025

---

### REQ-28 — Only a return for want of funds counts as an NSF

| Level | Class | Method |
|---|---|---|
| U | `ReturnedPaymentTest` | `otherReasonsAreReturnedButNotNsf` |
| A | `ReturnedPaymentApiIT` | `onlyAFundingFailureCountsAgainstTheNsfTally` |
| B | `installment_payment_api.feature` | "A return the policyholder did not cause carries no fee" |

**Manual:** TC-025

---

### REQ-29 — A payment cannot be returned twice

| Level | Class | Method |
|---|---|---|
| U | `ReturnedPaymentTest` | `theSamePaymentCannotBeReturnedTwice` |
| A | `ReturnedPaymentApiIT` | `theSamePaymentCannotBeReturnedTwice` |
| B | `installment_payment_api.feature` | "The same payment cannot be returned twice" |

**Manual:** TC-026

---

### REQ-30 — Bank details are never stored or served in full

| Level | Class | Method |
|---|---|---|
| U | `BankAccountReferenceTest` | `theClassHasNowhereToPutAWholeAccountNumber` |
| A | `AccountMaskingApiIT` | `noAccountNumberOnTheWireIsAnythingButFourStarsAndThreeDigits` |
| C | `billing_console_smoke.cy.js` | "shows a policyholder what they owe and how it will be collected" |

**Manual:** TC-027

**Note.** This passes for a structural reason: `BankAccountReference` has no field that could hold a
full number, so there is nothing for an endpoint, a log or a SOAP response to leak. A masking filter
would satisfy the same assertions and would be one forgotten endpoint away from failing.

---

### REQ-31 — Every ledger line's running balance is the sum of the lines before it

| Level | Class | Method |
|---|---|---|
| U | `PolicyTermLedgerTest` | `theRunningBalanceIsTheOrderedSumOfTheLines` |
| A | `TransactionLedgerApiIT` | `theRunningBalanceIsTheOrderedSumOfTheLinesBelowIt` |
| S | `TransactionLedgerUiIT` | `theRunningBalanceOnScreenIsTheSumOfTheLinesBelowIt` |
| B | `installment_payment_api.feature` | "Every line of the ledger agrees with the balance beside it" |
| C | `billing_console_smoke.cy.js` | "shows the ledger with a running balance on every line" |

**Manual:** TC-029

---

## The console in two languages

### REQ-32 — Every console page is published in English and French, with no untranslated key

| Level | Class | Method |
|---|---|---|
| U | `MessageBundleParityTest` | `everyEnglishKeyHasAFrenchCounterpart` |
| U | `MessageBundleParityTest` | `everyFrenchKeyHasAnEnglishCounterpart` |
| I | `LocalisedConsoleWebTest` | `noPageRendersAnUnresolvedMessageKey` |
| I | `AgentConsoleWebTest` | `noPanelRendersAnUnresolvedMessageKey` |
| I | `LocalisedConsoleWebTest` | `everyConsolePageOffersAWorkingLanguageSwitch` |
| S | `BilingualConsoleUiIT` | `moneyIsWrittenTheWayTheReadersLanguageWritesIt` |
| S | `BilingualConsoleUiIT` | `switchingLanguageChangesTheWordsAndNotTheFigures` |
| B | `bilingual_console_ui.feature` | "An invoice reads the same figures in both languages" |
| C | `billing_console_smoke.cy.js` | "reads the agent console in French without changing the figures" |

**Manual:** TC-030 · **Defect:** DEF-013

---

## The agent console

### REQ-33 — An agent can read the whole book, and sees the same figures the policyholder sees

| Level | Class | Method |
|---|---|---|
| I | `AgentConsoleWebTest` | `theGridListsEveryTermOnTheBooks` |
| I | `AgentConsoleWebTest` | `theTotalIsTheSumOfTheBalancesShownAboveIt` |
| I | `AgentConsoleWebTest` | `bothConsolesShowTheSameFigures` |
| S | `AgentConsoleUiIT` | `theGridShowsATermTheMomentItIsBound` |
| S | `AgentConsoleUiIT` | `theTotalAddsUpTheColumnAboveIt` |
| S | `AgentConsoleUiIT` | `theAgentAndThePolicyholderReadTheSameSchedule` |
| B | `billing_console_ui.feature` | "Both screens report the same schedule" |
| B | `billing_console_ui.feature` | "Both screens report the same ledger" |
| C | `billing_console_smoke.cy.js` | "shows an agent the whole book with a total that adds up" |

**Manual:** TC-031, TC-032

---

## Requirements with single-level coverage

Stated explicitly so the gaps are visible rather than implied:

| Requirement | Covered only at | Why that is acceptable |
|---|---|---|
| REQ-12 (rejection ordering) | Unit | Ordering within one method; a slower test through more layers asserts the same thing |
| REQ-14 (payment methods) | API | Enum round-trip; no additional risk at the UI level |
| REQ-17 (overdue asymmetry) | Unit | Pure state derivation |
| REQ-21 (SOAP) | API | There is no UI for the SOAP service |
| NFR-01 (performance) | JMeter | Manual by design; see the strategy for why it is not a CI gate |
| REQ-30 (bank masking) | Unit + API + Cypress | No Selenium test: the structural check is stronger than any screen assertion, and the console screen is covered by TC-027 step 1 through Cypress |
