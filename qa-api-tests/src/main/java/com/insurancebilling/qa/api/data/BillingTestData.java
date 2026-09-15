package com.insurancebilling.qa.api.data;

import com.insurancebilling.qa.api.client.BillingApiClient;
import com.insurancebilling.qa.api.client.CustomerApiClient;
import com.insurancebilling.qa.api.client.InvoiceApiClient;
import com.insurancebilling.qa.api.client.PolicyApiClient;
import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.CustomerDto;
import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.api.model.PolicyDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
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
  private final BillingApiClient billing = new BillingApiClient();

  /** The date every fixture term takes effect: far enough back that its first installments are billed. */
  private static final int TERM_STARTED_MONTHS_AGO = 2;

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

  /**
   * An invoice that is part paid and past its due date.
   *
   * <p>The only state in which the overdue flag carries information: the status reads PARTIALLY_PAID,
   * because hiding payment progress behind OVERDUE would be worse, so the lateness has to be said
   * somewhere else.
   */
  public InvoiceDto partiallyPaidOverdueInvoice(String totalAmount, String alreadyPaid) {
    InvoiceDto invoice =
        invoiceOn(activePolicy().id(), totalAmount, LocalDate.now().minusDays(10));
    invoices.pay(invoice.id(), alreadyPaid);
    return invoices.get(invoice.id());
  }

  /** A new billing account, collected by pre-authorised debit, for a new customer. */
  public BillingAccountDto account() {
    return billing.openAccount(customer().id(), "QA Tester", "742");
  }

  /**
   * A bound term whose premium and tax divide evenly across twelve.
   *
   * <p>1440.00 and 129.60 give a down payment of 130.80 then eleven of 132.80, totalling 1591.60.
   */
  public PolicyTermDto evenTerm(String accountReference) {
    return termOn(accountReference, "1440.00", "129.60");
  }

  /**
   * A bound term whose premium does not divide evenly across twelve.
   *
   * <p>1000.00 over twelve is 83.3333, so the down payment absorbs four cents: 90.87 then eleven of
   * 92.83, totalling 1112.00. This is the fixture for anything about rounding.
   */
  public PolicyTermDto unevenTerm(String accountReference) {
    return termOn(accountReference, "1000.00", "90.00");
  }

  /**
   * A term bound to the given account on a new policy, with a 2.00 installment fee.
   *
   * <p>The policy is raised for the account's own customer rather than a fresh one, so the insured named
   * on the term is the person the account belongs to. A fixture that got that wrong would still pass
   * every arithmetic assertion and quietly make the screens nonsense.
   */
  public PolicyTermDto termOn(String accountReference, String premium, String tax) {
    LocalDate effective = LocalDate.now().minusMonths(TERM_STARTED_MONTHS_AGO);
    long customerId = billing.account(accountReference).customerId();
    PolicyDto policy =
        policies.create(
            customerId, "AUTO", new BigDecimal(premium), effective, effective.plusYears(1));
    return billing.bindTerm(
        accountReference, policy.id(), effective.toString(), premium, tax, "2.00");
  }

  /** An account with one bound term on it, which is what most billing scenarios start from. */
  public PolicyTermDto boundTerm() {
    return evenTerm(account().accountReference());
  }
}
