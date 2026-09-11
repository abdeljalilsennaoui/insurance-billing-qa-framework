package com.insurancebilling.qa.api.data;

import com.insurancebilling.qa.api.client.CustomerApiClient;
import com.insurancebilling.qa.api.client.InvoiceApiClient;
import com.insurancebilling.qa.api.client.PolicyApiClient;
import com.insurancebilling.qa.api.model.CustomerDto;
import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.api.model.PolicyDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds the data a scenario needs, through the public API.
 *
 * <p>This type exists to enforce one rule: <b>a test that mutates data owns that data.</b> Every
 * fixture here creates a brand new customer, policy and invoice. Nothing reads or changes the seeded
 * {@code SEED-} records, so no two tests can interfere through shared state, tests can run in parallel,
 * and the suite gives the same result whether it runs first, last, or twice.
 *
 * <p>The alternative — pointing tests at a known seeded invoice — is the most common cause of suites
 * that pass alone and fail together, and of suites that pass once and fail on re-run because the first
 * run consumed the balance.
 *
 * <p>Emails are UUID-suffixed because the application enforces a unique-email constraint, and a fixed
 * address would make the second run of any suite fail with a conflict.
 */
public class BillingTestData {

  private final CustomerApiClient customers = new CustomerApiClient();
  private final PolicyApiClient policies = new PolicyApiClient();
  private final InvoiceApiClient invoices = new InvoiceApiClient();

  /** A new customer with a guaranteed-unique email. */
  public CustomerDto customer() {
    return customers.create("QA", "Tester", uniqueEmail());
  }

  public String uniqueEmail() {
    return "qa-" + UUID.randomUUID() + "@example.com";
  }

  /** A new active policy for a new customer. */
  public PolicyDto activePolicy() {
    LocalDate start = LocalDate.now().minusMonths(1);
    return policies.create(customer().id(), "AUTO", new BigDecimal("1200.00"), start, start.plusYears(1));
  }

  /** A new policy in the given state, for the refusal scenarios. */
  public PolicyDto policyWithStatus(String status) {
    PolicyDto policy = activePolicy();
    return policies.changeStatus(policy.id(), status);
  }

  /** An unpaid invoice for the given total, on its own new active policy. */
  public InvoiceDto unpaidInvoice(String totalAmount) {
    return invoiceOn(activePolicy().id(), totalAmount, LocalDate.now().plusDays(30));
  }

  /** An invoice already past its due date with nothing paid. */
  public InvoiceDto overdueInvoice(String totalAmount) {
    return invoiceOn(activePolicy().id(), totalAmount, LocalDate.now().minusDays(10));
  }

  /** An invoice with a payment already recorded against it. */
  public InvoiceDto partiallyPaidInvoice(String totalAmount, String alreadyPaid) {
    InvoiceDto invoice = unpaidInvoice(totalAmount);
    invoices.pay(invoice.id(), alreadyPaid);
    return invoices.get(invoice.id());
  }

  /** An invoice settled in full. */
  public InvoiceDto settledInvoice(String totalAmount) {
    InvoiceDto invoice = unpaidInvoice(totalAmount);
    invoices.pay(invoice.id(), totalAmount);
    return invoices.get(invoice.id());
  }

  /** A cancelled invoice. */
  public InvoiceDto cancelledInvoice(String totalAmount) {
    InvoiceDto invoice = unpaidInvoice(totalAmount);
    return invoices.cancel(invoice.id());
  }

  /** An invoice whose owning policy is no longer active. */
  public InvoiceDto invoiceOnInactivePolicy(String totalAmount, String policyStatus) {
    PolicyDto policy = activePolicy();
    InvoiceDto invoice = invoiceOn(policy.id(), totalAmount, LocalDate.now().plusDays(30));
    policies.changeStatus(policy.id(), policyStatus);
    return invoice;
  }

  public InvoiceDto invoiceOn(long policyId, String totalAmount, LocalDate dueDate) {
    return invoices.create(policyId, new BigDecimal(totalAmount), LocalDate.now(), dueDate);
  }
}
