@api
Feature: Paying a policy term by installments
  As a billing operator
  I want a payment to settle the oldest installment it can reach
  So that a policyholder's schedule is retired in the order it was quoted to them

  # Written in business language on purpose: no URLs, status codes, JSON or locators appear here.
  # The arithmetic is the specification. Where a figure looks arbitrary it is not - 90.87 is a down
  # payment carrying the rounding remainder of a term that does not divide evenly, and saying so in
  # the scenario is the difference between a test and a description of what the code already does.

  Scenario: The whole term is owed the moment it is bound
    Given a term of 1591.60 payable over twelve installments
    Then the term balance is 1591.60
    And twelve installments are outstanding

  Scenario: The schedule collects exactly what the term is worth
    Given a term of 1112.00 that does not divide evenly
    Then the installments add up to 1112.00 exactly

  Scenario: The rounding remainder lands on the down payment
    Given a term of 1112.00 that does not divide evenly
    Then installment 1 is for 90.87
    And installment 2 is for 92.83
    And installment 12 is for 92.83

  Scenario: A payment settles the oldest installment first
    Given a term of 1591.60 payable over twelve installments
    When a payment of 130.80 is made against the term
    Then installment 1 is settled
    And installment 2 is still outstanding
    And eleven installments are outstanding
    And the term balance is 1460.80

  Scenario: A payment reduces the balance by exactly what was paid
    Given a term of 1112.00 that does not divide evenly
    When a payment of 90.87 is made against the term
    Then the term balance is 1021.13

  Scenario: A returned payment puts the balance back and charges a fee
    Given a term of 1112.00 that does not divide evenly
    And a payment of 90.87 has been made against the term
    When the bank returns that payment for "INSUFFICIENT_FUNDS"
    Then the term balance is 1137.00
    And the ledger records a returned payment and a returned payment fee
    And the account has been charged 1 returned payment and 1 NSF

  Scenario: An installment that bounced is not the same as one never paid
    Given a term of 1591.60 payable over twelve installments
    And a payment of 130.80 has been made against the term
    When the bank returns that payment for "INSUFFICIENT_FUNDS"
    Then installment 1 is marked as reversed

  Scenario: A return the policyholder did not cause carries no fee
    Given a term of 1591.60 payable over twelve installments
    And a payment of 130.80 has been made against the term
    When the bank returns that payment for "ACCOUNT_CLOSED"
    Then the term balance is 1591.60
    And the account has been charged 1 returned payment and 0 NSF

  Scenario: The same payment cannot be returned twice
    Given a term of 1591.60 payable over twelve installments
    And a payment of 130.80 has been made against the term
    And the bank has returned that payment for "INSUFFICIENT_FUNDS"
    When the bank returns that payment again
    Then the return is refused because "PAYMENT_ALREADY_RETURNED"

  Scenario: Every line of the ledger agrees with the balance beside it
    Given a term of 1591.60 payable over twelve installments
    And a payment of 130.80 has been made against the term
    Then each ledger line's balance is the sum of that line and the ones before it
