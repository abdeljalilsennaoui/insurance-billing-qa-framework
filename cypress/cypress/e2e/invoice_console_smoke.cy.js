/**
 * Cypress smoke suite for the invoice console.
 *
 * Scope is deliberately narrow. The Selenium suite in qa-ui-tests carries the full UI coverage: every
 * payment journey, every refusal reason, the payment history and the list filtering. Duplicating that
 * here would mean maintaining the same scenarios twice in two languages and having both fail for the
 * same reason.
 *
 * What this suite adds is a fast, independent check that the console is actually up and its critical
 * path works, from a different tool and a different runtime. If Cypress passes and Selenium fails, the
 * problem is in the Java suite; if both fail, the problem is in the application.
 */
describe('Invoice console smoke', () => {
  it('serves the invoice list from the application root', () => {
    cy.visit('/');
    cy.url().should('include', '/invoices');
    cy.byTestId('page-title').should('contain', 'Invoices');
    cy.byTestId('invoice-table').should('exist');
  });

  it('lists the seeded invoices with their statuses', () => {
    cy.visit('/invoices');
    cy.byTestId('invoice-row').should('have.length.at.least', 6);

    // The seeded baseline deliberately covers every invoice state; this asserts the console can
    // render each of them rather than only the happy one.
    // The console is bilingual, so the words in a status pill are display copy. The state rides on
    // data-status, which is the same in both languages - assert on that, not on the label.
    ['UNPAID', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'].forEach((status) => {
      cy.get(`[data-testid="invoice-status"][data-status="${status}"]`).should('exist');
    });
  });

  it('opens an invoice and shows its balance figures', () => {
    cy.createUnpaidInvoice(450.0).then((invoice) => {
      cy.visit(`/invoices/${invoice.id}`);

      cy.byTestId('invoice-number').should('contain', invoice.invoiceNumber);
      cy.byTestId('invoice-total').should('have.attr', 'data-amount', '450.00');
      cy.byTestId('invoice-outstanding-balance').should('have.attr', 'data-amount', '450.00');
      cy.byTestId('invoice-amount-paid').should('have.attr', 'data-amount', '0.00');
      cy.byTestId('no-payments-message').should('exist');
    });
  });

  it('records a valid payment and updates the balance', () => {
    cy.createUnpaidInvoice(400.0).then((invoice) => {
      cy.visit(`/invoices/${invoice.id}`);

      cy.byTestId('payment-amount-input').clear().type('100.00');
      cy.byTestId('payment-method-select').select('CARD');
      cy.byTestId('payment-reference-input').clear().type('CYPRESS-1');
      cy.byTestId('submit-payment-button').click();

      cy.byTestId('payment-success').should('exist');
      cy.byTestId('invoice-status').should('have.attr', 'data-status', 'PARTIALLY_PAID');
      cy.byTestId('invoice-amount-paid').should('have.attr', 'data-amount', '100.00');
      cy.byTestId('invoice-outstanding-balance').should('have.attr', 'data-amount', '300.00');
      cy.byTestId('payment-row').should('have.length', 1);
    });
  });

  it('refuses an overpayment with a visible message and leaves the balance alone', () => {
    cy.createUnpaidInvoice(100.0).then((invoice) => {
      cy.visit(`/invoices/${invoice.id}`);

      cy.byTestId('payment-amount-input').clear().type('500.00');
      cy.byTestId('submit-payment-button').click();

      cy.byTestId('payment-error').should('contain', 'exceeds the outstanding balance');
      cy.byTestId('invoice-outstanding-balance').should('have.attr', 'data-amount', '100.00');
      cy.byTestId('payment-row').should('not.exist');
    });
  });

  it('explains a non-numeric amount rather than failing silently', () => {
    cy.createUnpaidInvoice(100.0).then((invoice) => {
      cy.visit(`/invoices/${invoice.id}`);

      cy.byTestId('payment-amount-input').clear().type('not-a-number');
      cy.byTestId('submit-payment-button').click();

      cy.byTestId('payment-error').should('contain', 'Enter a valid amount');
    });
  });

  it('shows a not-found page for an unknown invoice instead of a JSON error', () => {
    cy.visit('/invoices/999999', { failOnStatusCode: false });
    cy.byTestId('not-found-message').should('exist');
  });
});
