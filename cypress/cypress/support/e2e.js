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

/**
 * Opens a billing account and binds a term to it, through the REST API.
 *
 * The premium and tax are the ones that do not divide evenly across twelve, so the schedule this
 * builds carries the rounding remainder on its down payment. A fixture whose figures divided cleanly
 * would let a broken allocation pass.
 */
Cypress.Commands.add('createBoundTerm', () => {
  const unique = Date.now() + '-' + Math.floor(Math.random() * 100000);
  const effective = new Date(Date.now() - 60 * 86400000).toISOString().slice(0, 10);

  return cy
    .request('POST', '/api/customers', {
      firstName: 'Cypress',
      lastName: 'Smoke',
      email: `cypress-${unique}@example.com`,
    })
    .then((customer) =>
      cy
        .request('POST', '/api/accounts', {
          customerId: customer.body.id,
          paymentPlan: 'MONTHLY',
          paymentMethod: 'PRE_AUTHORIZED_DEBIT',
          accountHolder: 'Cypress Smoke',
          accountLastDigits: '742',
        })
        .then((account) =>
          cy
            .request('POST', '/api/policies', {
              customerId: customer.body.id,
              type: 'AUTO',
              annualPremium: 1000.0,
              startDate: effective,
              endDate: new Date(Date.now() + 305 * 86400000).toISOString().slice(0, 10),
            })
            .then((policy) =>
              cy
                .request('POST', `/api/accounts/${account.body.accountReference}/terms`, {
                  policyId: policy.body.id,
                  effectiveDate: effective,
                  paymentPlan: 'MONTHLY',
                  termPremium: '1000.00',
                  termTax: '90.00',
                  installmentFee: '2.00',
                })
                .then((term) => ({
                  account: account.body,
                  policy: policy.body,
                  term: term.body,
                }))
            )
        )
    );
});

/** Sums the data-amount attributes of every element carrying the given test hook. */
Cypress.Commands.add('sumOfAmounts', (testId) =>
  cy.get(`[data-testid="${testId}"]`).then((cells) =>
    Cypress._.sumBy(Cypress._.toArray(cells), (cell) =>
      Math.round(parseFloat(cell.getAttribute('data-amount')) * 100)
    ) / 100
  )
);
