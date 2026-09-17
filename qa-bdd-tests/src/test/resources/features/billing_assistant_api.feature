@api @assistant
Feature: Holding the billing assistant's answers to the billing records
  As the platform answering a policyholder's questions about their bill
  I want every answer held to the records it says it read
  So that nothing it tells somebody about their money is invented, borrowed from another account,
  or passed off as something it is not

  # Written in business language on purpose: no URLs, status codes, JSON or locators appear here.
  # The assistant's wording is never asserted - an answer phrased three ways is still the same answer.
  # What is asserted is where its figures came from, whose records it read, and when it refuses.

  Scenario: Every figure in an answer comes from the records it names
    Given a policyholder whose balance is 1328.00
    When they ask the assistant why their balance is what it is
    Then the platform gives an answer
    And every figure in that answer appears in the records it names

  Scenario: Naming another customer's account in the question does not reach it
    Given a policyholder whose balance is 1328.00
    When they ask the assistant about another customer's account instead
    Then every record the answer names belongs to that policyholder

  Scenario: A question the records cannot answer gets no answer rather than an invented one
    Given a policyholder whose balance is 1328.00
    When they ask the assistant something the billing records cannot answer
    Then the platform gives no answer
    And the answer names no records

  Scenario: An answer says which assistant produced it
    Given a policyholder whose balance is 1328.00
    When they ask the assistant why their balance is what it is
    Then the answer says which assistant produced it

  Scenario: A question about an account that does not exist is refused
    When somebody asks the assistant about an account that does not exist
    Then the platform says there is no such account
