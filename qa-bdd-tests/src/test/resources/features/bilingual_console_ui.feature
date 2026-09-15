@ui
Feature: Reading the billing console in either official language
  As a policyholder in Canada
  I want the billing console in English or in French
  So that I can read what I owe in the language I do business in

  The scenarios are written twice on purpose. A rule that holds in one language and not the other is
  exactly the defect a second language exists to expose, and a suite that only ever runs in English
  cannot see it.

  Scenario Outline: The same payment behaves the same in either language
    Given the billing console is open in "<language>"
    And an unpaid invoice for 450.00
    When I open that invoice in the console
    And I record a payment of 150.00
    Then the console confirms the payment
    And the console shows 150.00 paid and 300.00 outstanding
    And the console shows the invoice as "PARTIALLY_PAID"

    Examples:
      | language |
      | English  |
      | French   |

  Scenario Outline: The same refusal is explained in either language
    Given the billing console is open in "<language>"
    And an unpaid invoice for 100.00
    When I open that invoice in the console
    And I record a payment of 500.00
    Then the console refuses the payment with an explanation
    And the console shows 0.00 paid and 100.00 outstanding

    Examples:
      | language |
      | English  |
      | French   |

  Scenario: An invoice reads the same figures in both languages
    Given the billing console is open in "English"
    And an unpaid invoice for 1450.00
    When I open that invoice in the console
    Then the balance is written as "$1,450.00"
    When I switch the console to "French"
    Then the balance is written as "1 450,00 $"
    And the console shows 0.00 paid and 1450.00 outstanding
