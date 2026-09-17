/**
 * Cypress smoke suite for the billing assistant.
 *
 * One pass, and no more. The Selenium suite carries the coverage: both consoles compared against each
 * other, both languages, the trace read call by call, and the grounding check against the account's
 * own figures. Repeating that here would mean maintaining it twice and having both go red for the
 * same reason.
 *
 * What this adds is a second toolchain arriving at the same panel. If Cypress passes and Selenium
 * fails, the fault is in the Java suite; if both fail, the fault is in the application.
 *
 * Nothing here asserts the assistant's wording. An answer phrased three ways is still the same
 * answer, and a smoke test pinned to one phrasing would go red on a correct one. What is asserted is
 * that a person can ask, that something grounded comes back, and that the screen says where it came
 * from.
 */
describe('Billing assistant smoke', () => {
  const SEEDED_ACCOUNT = 'ACCT-100001';
  const RECORDED_QUESTION = 'Why is my balance 1,328.00?';

  it('answers a question and shows the billing records behind the answer', () => {
    cy.visit(`/accounts/${SEEDED_ACCOUNT}/terms`);

    cy.byTestId('assistant-panel').should('exist');
    cy.byTestId('assistant-answer').should('not.exist');

    cy.byTestId('assistant-question').type(RECORDED_QUESTION);
    cy.byTestId('assistant-submit').click();

    cy.byTestId('assistant-answer').should('have.attr', 'data-available', 'true');
    cy.byTestId('assistant-answer-text').should('contain', '1328.00');

    // The provenance, read from the attributes rather than the translated labels beside them.
    cy.byTestId('assistant-trace-row').should('have.length', 2);
    cy.byTestId('assistant-trace-row').eq(0).should('have.attr', 'data-tool', 'find_account');
    cy.byTestId('assistant-trace-row').eq(1).should('have.attr', 'data-tool', 'get_ledger');
  });

  it('tells the reader whether an answer was produced or replayed', () => {
    cy.visit(`/accounts/${SEEDED_ACCOUNT}/terms`);

    // Not asserted as 'replay': which provider answers is configuration, and a smoke test that went
    // red when the application was pointed at a live model would be testing the configuration. What
    // has to hold is that the screen says which one it was.
    cy.byTestId('assistant-panel')
      .should('have.attr', 'data-provider')
      .and('not.be.empty');
  });

  it('says it has no answer rather than inventing one', () => {
    cy.visit(`/accounts/${SEEDED_ACCOUNT}/terms`);

    cy.byTestId('assistant-question').type('What is the airspeed velocity of an unladen swallow?');
    cy.byTestId('assistant-submit').click();

    cy.byTestId('assistant-answer').should('have.attr', 'data-available', 'false');
    cy.byTestId('assistant-trace-row').should('not.exist');
  });
});
