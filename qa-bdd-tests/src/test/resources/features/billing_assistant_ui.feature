@ui @assistant
Feature: Asking the billing assistant about a bill
  As a policyholder looking at what I owe
  I want to ask why the figures are what they are
  So that I understand my bill without telephoning somebody

  Written in business language on purpose: no URLs, status codes, JSON or locators appear here.

  Two things in these scenarios are unusual enough to state. The assistant's wording is not asserted,
  because an answer phrased three ways is still the same answer and a scenario pinned to one phrasing
  would fail on a correct one. What is asserted is the arithmetic, the provenance, and the refusals -
  the parts that have a right answer.

  And the console says where an answer came from. A recorded answer presented as though it had just
  been produced would be the platform misrepresenting itself, so "the console says which assistant
  answered" is a requirement rather than a detail.

  Scenario: The assistant explains a balance using the account's own figures
    Given my billing account is open in "English"
    When I ask the assistant why my balance is what it is
    Then the assistant answers
    And every figure in the answer appears in my account
    And the answer says which billing records it read

  Scenario: The assistant does not invent an answer it does not have
    Given my billing account is open in "English"
    When I ask the assistant something it has no answer for
    Then the assistant says it has no answer
    And the answer cites no billing records

  Scenario: The policyholder and the agent are told the same thing
    Given my billing account is open in "English"
    When I ask the assistant why my balance is what it is
    And an agent asks the assistant the same question
    Then both are given the same answer
    And both answers cite the same billing records

  Scenario: The assistant answers in the language the console is read in
    Given my billing account is open in "French"
    When I ask the assistant in French why my balance is what it is
    Then the assistant answers
    And the answer is in French
    And the answer says which billing records it read

  Scenario: The console says whether an answer was produced or replayed
    Given my billing account is open in "English"
    Then the console says which assistant answered
