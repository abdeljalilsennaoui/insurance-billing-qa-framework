@ui
Feature: Reading a policy term on screen
  As a policyholder, and as the agent who serves them
  I want the same term to read the same way on both of our screens
  So that neither of us has to take the other's word for what is owed

  # The two consoles render the same figures from the same Thymeleaf fragment. That makes them
  # impossible to disagree by construction - which is exactly the kind of claim that stops being true
  # the first time somebody is in a hurry. These scenarios are what would notice.

  Scenario: A policyholder sees their schedule
    Given a term of 1591.60 payable over twelve installments
    When the policyholder opens their payment schedule
    Then twelve installments are listed
    And the schedule on screen adds up to 1591.60

  Scenario: An agent finds the same term on the book
    Given a term of 1591.60 payable over twelve installments
    When the agent opens the console
    Then the term is listed on the portfolio with a balance of 1591.60
    And the portfolio total is the sum of the balances shown

  Scenario: Both screens report the same schedule
    Given a term of 1112.00 that does not divide evenly
    When the policyholder opens their payment schedule
    And the agent opens the same term's payment schedule
    Then both screens show the same installment amounts

  Scenario: Both screens report the same ledger
    Given a term of 1591.60 payable over twelve installments
    And a payment of 130.80 has been made against the term
    When the policyholder opens their transaction history
    And the agent opens the same term's transaction history
    Then both screens show the same running balances

  Scenario: A returned payment is visible to the policyholder
    Given a term of 1591.60 payable over twelve installments
    And a payment of 130.80 has been made against the term
    And the bank has returned that payment for "INSUFFICIENT_FUNDS"
    When the policyholder opens their transaction history
    Then the returned payment and its fee are both shown
    And the balance on screen is 1616.60

  Scenario Outline: The agent's portfolio reads in either language
    Given a term of 1591.60 payable over twelve installments
    When the agent opens the console in "<language>"
    Then the product is written as "<product>"
    And the term is listed on the portfolio with a balance of 1591.60

    Examples:
      | language | product    |
      | English  | Auto       |
      | French   | Automobile |

  Scenario: Switching language keeps the agent on the term they were reading
    Given a term of 1112.00 that does not divide evenly
    When the agent opens the same term's payment schedule
    And the agent switches the console to "French"
    Then the agent is still reading the same term's schedule
    And the schedule on screen adds up to 1112.00
