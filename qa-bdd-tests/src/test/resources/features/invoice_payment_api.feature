@api
Feature: Invoice payment rules over the billing API
  As a billing operator
  I want payments to be accepted or refused according to the policy and invoice state
  So that an invoice can never be settled for more than it is owed

  # Written in business language on purpose: no URLs, status codes, JSON or locators appear here.
  # Those belong in the step definitions. A scenario that mentions "POST /api/invoices" stops being a
  # specification of behaviour and becomes a description of an implementation.

  Scenario: A partial payment reduces what is still owed
    Given an unpaid invoice for 450.00
    When a payment of 150.00 is submitted
    Then the payment is accepted
    And the invoice shows 150.00 paid and 300.00 outstanding
    And the invoice is "PARTIALLY_PAID"

  Scenario: Paying the outstanding balance settles the invoice
    Given an unpaid invoice for 450.00
    When a payment of 450.00 is submitted
    Then the payment is accepted
    And the invoice is "PAID"
    And the invoice shows 450.00 paid and 0.00 outstanding

  Scenario: Instalments add up exactly
    Given an unpaid invoice for 100.00
    When payments of 33.33, 33.33 and 33.34 are submitted
    Then the invoice is "PAID"
    And the invoice shows 100.00 paid and 0.00 outstanding

  Scenario Outline: An invalid amount is refused for a stated reason
    Given an unpaid invoice for 450.00
    When a payment of <amount> is submitted
    Then the payment is refused because "<reason>"
    And the invoice still shows 450.00 outstanding

    Examples: amounts that are not a positive sum of money
      | amount  | reason                |
      | 0.00    | AMOUNT_NOT_POSITIVE   |
      | -250.00 | AMOUNT_NOT_POSITIVE   |
      | 10.001  | AMOUNT_SCALE_INVALID  |

    Examples: amounts beyond what is owed
      | amount   | reason                      |
      | 450.01   | EXCEEDS_OUTSTANDING_BALANCE |
      | 10000.00 | EXCEEDS_OUTSTANDING_BALANCE |

  Scenario: Overpayment is judged against what remains, not the original total
    Given an unpaid invoice for 450.00
    And 400.00 has already been paid
    When a payment of 60.00 is submitted
    Then the payment is refused because "EXCEEDS_OUTSTANDING_BALANCE"

  Scenario: A settled invoice accepts nothing further
    Given a fully settled invoice for 100.00
    When a payment of 10.00 is submitted
    Then the payment is refused because "INVOICE_ALREADY_PAID"

  Scenario: A cancelled invoice cannot be paid
    Given a cancelled invoice for 100.00
    When a payment of 10.00 is submitted
    Then the payment is refused because "INVOICE_CANCELLED"

  Scenario Outline: An invoice on a policy that is no longer active cannot be paid
    Given an invoice for 100.00 on a <policy state> policy
    When a payment of 10.00 is submitted
    Then the payment is refused because "POLICY_NOT_ACTIVE"

    Examples:
      | policy state |
      | LAPSED       |
      | CANCELLED    |

  Scenario: An overdue invoice is reported as overdue until it is settled
    Given an invoice for 200.00 that is past its due date
    Then the invoice is flagged overdue
    When a payment of 200.00 is submitted
    Then the invoice is "PAID"
    And the invoice is not flagged overdue
