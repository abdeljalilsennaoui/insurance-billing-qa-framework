/**
 * Cypress smoke suite for the billing screens.
 *
 * One pass per screen, and no more. The Selenium suite in qa-ui-tests carries the coverage: every
 * schedule figure, every ledger invariant, both personas compared against each other, and the whole
 * thing in two languages. Repeating that here would mean maintaining the same scenarios twice, in two
 * languages, and having both go red for the same reason.
 *
 * What this adds is a second toolchain arriving at the same screens. If Cypress passes and Selenium
 * fails, the fault is in the Java suite; if both fail, the fault is in the application. That is the
 * only thing a second browser stack tells you that the first one cannot.
 */
describe('Billing console smoke', () => {
  it('shows a policyholder what they owe and how it will be collected', () => {
    cy.createBoundTerm().then(({ account }) => {
      cy.visit(`/accounts/${account.accountReference}`);

      cy.byTestId('context-account').should('contain', account.accountReference);
      cy.byTestId('total-balance').should('have.attr', 'data-amount', '1112.00');

      // The masked digits are the point of the screen: no field anywhere holds a full account
      // number, so there is nothing here that could leak one.
      cy.byTestId('bank-account-number').should('have.text', '****742');
      cy.byTestId('bank-institution').should('have.text', '***');
    });
  });

  it('shows the installment schedule adding up to the term exactly', () => {
    cy.createBoundTerm().then(({ account }) => {
      cy.visit(`/accounts/${account.accountReference}/terms?tab=schedule`);

      cy.byTestId('installment-row').should('have.length', 12);

      // 1000.00 over twelve is 83.3333, so the down payment absorbs the remainder. A schedule that
      // did not add back up to 1112.00 would be wrong in front of the customer.
      cy.byTestId('installment-amount').first().should('have.attr', 'data-amount', '90.87');
      cy.sumOfAmounts('installment-amount').should('eq', 1112.0);
    });
  });

  it('shows the ledger with a running balance on every line', () => {
    cy.createBoundTerm().then(({ account, term }) => {
      cy.request('POST', `/api/terms/${term.termReference}/payments`, { amount: '90.87' });

      cy.visit(`/accounts/${account.accountReference}/terms?tab=transactions`);

      cy.byTestId('ledger-row').should('have.length', 2);
      // Newest first, so the payment is the top line and the balance beside it is what is left.
      cy.byTestId('ledger-balance').first().should('have.attr', 'data-amount', '1021.13');
    });
  });

  it('shows an agent the whole book with a total that adds up', () => {
    cy.createBoundTerm().then(({ term }) => {
      cy.visit('/agent');

      cy.get(`[data-testid="portfolio-row"][data-term="${term.termReference}"]`).should('exist');

      cy.sumOfAmounts('portfolio-balance').then((summed) => {
        cy.byTestId('portfolio-total').should('have.attr', 'data-amount', summed.toFixed(2));
      });
    });
  });

  it('reads the agent console in French without changing the figures', () => {
    cy.createBoundTerm().then(({ term }) => {
      cy.visit(`/agent?term=${term.termReference}&tab=schedule&lang=fr`);

      cy.byTestId('page-title').should('contain', "Console de l'agent");
      cy.get(`[data-testid="portfolio-row"][data-term="${term.termReference}"]`)
        .find('[data-testid="portfolio-product"]')
        .should('have.attr', 'data-product', 'AUTO')
        .and('contain', 'Automobile');

      // Same arithmetic, different words for it.
      cy.sumOfAmounts('installment-amount').should('eq', 1112.0);
    });
  });
});
