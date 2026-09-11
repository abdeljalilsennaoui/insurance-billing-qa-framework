@ui
Feature: Paying an invoice through the billing console
  As a billing clerk
  I want to record payments against an invoice in the browser
  So that a customer's balance is up to date and mistakes are refused with an explanation

  Background:
    Given the billing console is open

  Scenario: Recording a partial payment updates the balance on screen
    Given an unpaid invoice for 450.00
    When I open that invoice in the console
    And I record a payment of 150.00
    Then the console confirms the payment
    And the console shows 150.00 paid and 300.00 outstanding
    And the console shows the invoice as "PARTIALLY_PAID"

  Scenario: Settling an invoice in the console
    Given an unpaid invoice for 300.00
    When I open that invoice in the console
    And I record a payment of 300.00
    Then the console shows the invoice as "PAID"
    And the console says the invoice is settled in full

  Scenario: An overpayment is refused with a visible explanation
    Given an unpaid invoice for 100.00
    When I open that invoice in the console
    And I record a payment of 500.00
    Then the console shows an error containing "exceeds the outstanding balance"
    And the console shows 0.00 paid and 100.00 outstanding

  Scenario: Typing something that is not an amount is explained, not swallowed
    Given an unpaid invoice for 100.00
    When I open that invoice in the console
    And I record a payment of abc
    Then the console shows an error containing "Enter a valid amount"

  Scenario: A cancelled invoice is marked as such and refuses payment
    Given a cancelled invoice for 100.00
    When I open that invoice in the console
    Then the console says the invoice is cancelled
    When I record a payment of 10.00
    Then the console shows an error containing "cancelled"

  Scenario: An invoice appears in the list with its balance and status
    Given an unpaid invoice for 250.00
    And 100.00 has already been paid
    When I look for that invoice in the list
    Then the list shows it as "PARTIALLY_PAID" with 150.00 outstanding
