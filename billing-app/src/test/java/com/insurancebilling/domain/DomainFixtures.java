package com.insurancebilling.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Builders for domain objects used by the unit tests.
 *
 * <p>Each test builds its own object graph here rather than sharing a field, so that a test mutating
 * an invoice cannot affect the next one.
 */
final class DomainFixtures {

  private DomainFixtures() {}

  /**
   * The instant every fixture payment is recorded at.
   *
   * <p>Fixed rather than {@code Instant.now()}: {@link Invoice#applyPayment} takes the receipt instant
   * as an argument, and a test that passed it wall-clock time would be asserting against a value it
   * could not name. The payment list keeps insertion order in memory, so several payments sharing this
   * instant do not disturb the ordering assertions.
   */
  static final Instant RECEIVED_AT = Instant.parse("2026-01-14T15:00:00Z");

  static BigDecimal money(String amount) {
    return new BigDecimal(amount);
  }

  /** An invoice due in 30 days, attached to an active policy held by a customer. */
  static Invoice invoiceOnActivePolicy(String totalAmount) {
    return invoiceOnPolicyWithStatus(totalAmount, PolicyStatus.ACTIVE);
  }

  /** An invoice attached to a policy in the given state, for the policy-status rule tests. */
  static Invoice invoiceOnPolicyWithStatus(String totalAmount, PolicyStatus policyStatus) {
    LocalDate today = LocalDate.now();
    Invoice invoice =
        new Invoice("INV-TEST-001", money(totalAmount), today, today.plusDays(30));
    attachToPolicy(invoice, policyStatus);
    return invoice;
  }

  /** An invoice whose due date has already passed, for the overdue tests. */
  static Invoice overdueInvoice(String totalAmount) {
    LocalDate today = LocalDate.now();
    Invoice invoice =
        new Invoice("INV-TEST-OVERDUE", money(totalAmount), today.minusDays(60), today.minusDays(30));
    attachToPolicy(invoice, PolicyStatus.ACTIVE);
    return invoice;
  }

  private static void attachToPolicy(Invoice invoice, PolicyStatus policyStatus) {
    Customer customer = new Customer("Test", "Customer", "test.customer@example.com");
    Policy policy =
        new Policy(
            "POL-TEST-001",
            PolicyType.AUTO,
            money("1200.00"),
            LocalDate.now().minusMonths(1),
            LocalDate.now().plusMonths(11));
    policy.setStatus(policyStatus);
    customer.addPolicy(policy);
    policy.addInvoice(invoice);
  }
}
