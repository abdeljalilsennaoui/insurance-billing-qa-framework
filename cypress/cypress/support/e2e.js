// Shared helpers for the smoke suite.
//
// Locators go through data-testid exactly as the Selenium suite does, so a markup change breaks both
// suites in the same place rather than one of them silently passing against stale assumptions.

/** Selects by the stable test hook. */
Cypress.Commands.add('byTestId', (testId) => cy.get(`[data-testid="${testId}"]`));

/**
 * Creates a customer, an active policy and an unpaid invoice through the REST API.
 *
 * The suite builds its own data rather than using the seeded SEED- records, for the same reason the
 * Java suites do: a test that mutates shared data passes alone and fails alongside others, and fails
 * on a second run because the first consumed the balance.
 */
Cypress.Commands.add('createUnpaidInvoice', (totalAmount) => {
  const unique = Date.now() + '-' + Math.floor(Math.random() * 100000);

  return cy
    .request('POST', '/api/customers', {
      firstName: 'Cypress',
      lastName: 'Smoke',
      email: `cypress-${unique}@example.com`,
    })
    .then((customer) =>
      cy.request('POST', '/api/policies', {
        customerId: customer.body.id,
        type: 'AUTO',
        annualPremium: 1200.0,
        startDate: new Date().toISOString().slice(0, 10),
        endDate: new Date(Date.now() + 365 * 86400000).toISOString().slice(0, 10),
      })
    )
    .then((policy) =>
      cy.request('POST', '/api/invoices', {
        policyId: policy.body.id,
        totalAmount: totalAmount,
        issueDate: new Date().toISOString().slice(0, 10),
        dueDate: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10),
      })
    )
    .then((invoice) => invoice.body);
});
