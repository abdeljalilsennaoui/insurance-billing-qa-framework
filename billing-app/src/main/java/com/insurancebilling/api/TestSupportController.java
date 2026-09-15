package com.insurancebilling.api;

import com.insurancebilling.config.SeedDataLoader.SeedDataWriter;
import com.insurancebilling.repository.CustomerRepository;
import com.insurancebilling.repository.BillingAccountRepository;
import com.insurancebilling.repository.InvoiceRepository;
import com.insurancebilling.repository.PaymentRepository;
import com.insurancebilling.repository.PolicyRepository;
import com.insurancebilling.repository.PolicyTermRepository;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * QA-only endpoint that restores the database to the seeded baseline.
 *
 * <p>Gated by {@code qa.test-support.enabled}, which defaults to {@code false}. The bean is not merely
 * hidden when the property is off, it is never created, so a deployment that does not opt in has no
 * route that can wipe its data. CI and the local run scripts switch it on explicitly.
 *
 * <p>Suites use this between runs rather than between individual tests: per-test resets would serialise
 * the whole suite and make parallel execution impossible. Individual tests that mutate data create
 * their own records instead.
 */
@RestController
@RequestMapping("/api/test-support")
@ConditionalOnProperty(name = "qa.test-support.enabled", havingValue = "true")
public class TestSupportController {

  private final CustomerRepository customers;
  private final PolicyRepository policies;
  private final InvoiceRepository invoices;
  private final PaymentRepository payments;
  private final BillingAccountRepository accounts;
  private final PolicyTermRepository terms;
  private final SeedDataWriter seedDataWriter;

  public TestSupportController(
      CustomerRepository customers,
      PolicyRepository policies,
      InvoiceRepository invoices,
      PaymentRepository payments,
      BillingAccountRepository accounts,
      PolicyTermRepository terms,
      SeedDataWriter seedDataWriter) {
    this.customers = customers;
    this.policies = policies;
    this.invoices = invoices;
    this.payments = payments;
    this.accounts = accounts;
    this.terms = terms;
    this.seedDataWriter = seedDataWriter;
  }

  /**
   * Deletes all data and reloads the baseline.
   *
   * <p>Deletion runs child-first because the foreign keys point upwards; deleting customers first would
   * fail on the invoice and payment references.
   */
  @PostMapping("/reset")
  @Transactional
  public Map<String, Object> reset() {
    payments.deleteAllInBatch();
    // Invoices reference installments, so they go before the accounts those installments hang from.
    invoices.deleteAllInBatch();

    // deleteAll rather than deleteAllInBatch: an account cascades to its terms, and each term to its
    // schedule and ledger, which reference each other. Hibernate knows the order those have to go in;
    // a bulk delete would not, and would trip a foreign key. The flush is not optional - the batch
    // deletes below issue SQL immediately, while deleteAll only queues entity deletions, so without it
    // the policies statement runs before the terms pointing at them are gone.
    accounts.deleteAll();
    accounts.flush();

    policies.deleteAllInBatch();
    customers.deleteAllInBatch();
    seedDataWriter.load();
    return Map.of(
        "status", "reset",
        "customers", customers.count(),
        "policies", policies.count(),
        "invoices", invoices.count(),
        "accounts", accounts.count(),
        "terms", terms.count());
  }
}
