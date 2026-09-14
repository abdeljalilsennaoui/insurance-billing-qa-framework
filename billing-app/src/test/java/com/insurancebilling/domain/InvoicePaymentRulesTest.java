package com.insurancebilling.domain;

import static com.insurancebilling.domain.DomainFixtures.RECEIVED_AT;
import static com.insurancebilling.domain.DomainFixtures.invoiceOnActivePolicy;
import static com.insurancebilling.domain.DomainFixtures.invoiceOnPolicyWithStatus;
import static com.insurancebilling.domain.DomainFixtures.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * One test per payment rejection rule.
 *
 * <p>Every case asserts the specific {@link PaymentRejectionReason}, not merely that an exception was
 * thrown. A test that only checked "some exception" would keep passing if a rule were deleted and
 * another rule happened to catch the same input.
 */
class InvoicePaymentRulesTest {

  @ParameterizedTest(name = "an amount of {0} is rejected as not positive")
  @ValueSource(strings = {"0.00", "0", "-0.01", "-150.00"})
  void nonPositiveAmountIsRejected(String amount) {
    Invoice invoice = invoiceOnActivePolicy("450.00");

    assertThatThrownBy(() -> invoice.applyPayment(money(amount), PaymentMethod.CARD, "REF-1", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.AMOUNT_NOT_POSITIVE);

    assertThat(invoice.getPayments()).isEmpty();
    assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("450.00");
  }

  @ParameterizedTest(name = "an amount of {0} is rejected for excess precision")
  @ValueSource(strings = {"10.001", "0.005", "99.9999"})
  void amountWithTooManyDecimalPlacesIsRejected(String amount) {
    Invoice invoice = invoiceOnActivePolicy("450.00");

    assertThatThrownBy(() -> invoice.applyPayment(money(amount), PaymentMethod.CARD, "REF-1", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.AMOUNT_SCALE_INVALID);

    assertThat(invoice.getPayments()).isEmpty();
  }

  @Test
  @DisplayName("a payment larger than the outstanding balance is rejected as overpayment")
  void overpaymentIsRejected() {
    Invoice invoice = invoiceOnActivePolicy("450.00");

    assertThatThrownBy(() -> invoice.applyPayment(money("450.01"), PaymentMethod.CARD, "REF-1", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.EXCEEDS_OUTSTANDING_BALANCE);

    assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("450.00");
  }

  @Test
  @DisplayName("overpayment is measured against the remaining balance, not the invoice total")
  void overpaymentIsMeasuredAgainstRemainingBalance() {
    Invoice invoice = invoiceOnActivePolicy("450.00");
    invoice.applyPayment(money("400.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);

    assertThatThrownBy(() -> invoice.applyPayment(money("60.00"), PaymentMethod.CARD, "REF-2", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.EXCEEDS_OUTSTANDING_BALANCE);

    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("400.00");
  }

  @Test
  @DisplayName("a payment against a fully paid invoice is rejected")
  void paymentAgainstSettledInvoiceIsRejected() {
    Invoice invoice = invoiceOnActivePolicy("450.00");
    invoice.applyPayment(money("450.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);

    assertThatThrownBy(() -> invoice.applyPayment(money("10.00"), PaymentMethod.CARD, "REF-2", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.INVOICE_ALREADY_PAID);

    assertThat(invoice.getPayments()).hasSize(1);
  }

  @Test
  @DisplayName("a payment against a cancelled invoice is rejected")
  void paymentAgainstCancelledInvoiceIsRejected() {
    Invoice invoice = invoiceOnActivePolicy("450.00");
    invoice.cancel();

    assertThatThrownBy(() -> invoice.applyPayment(money("10.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.INVOICE_CANCELLED);

    assertThat(invoice.getPayments()).isEmpty();
  }

  @ParameterizedTest(name = "a payment on a {0} policy is rejected")
  @EnumSource(value = PolicyStatus.class, names = {"LAPSED", "CANCELLED"})
  void paymentOnInactivePolicyIsRejected(PolicyStatus policyStatus) {
    Invoice invoice = invoiceOnPolicyWithStatus("450.00", policyStatus);

    assertThatThrownBy(() -> invoice.applyPayment(money("100.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.POLICY_NOT_ACTIVE);

    assertThat(invoice.getPayments()).isEmpty();
  }

  @Test
  @DisplayName("an invalid amount is reported ahead of an invalid invoice state")
  void amountIsValidatedBeforeInvoiceState() {
    Invoice invoice = invoiceOnActivePolicy("450.00");
    invoice.cancel();

    // Both the amount and the invoice state are wrong. The caller is told about the amount,
    // because that is the part it can correct without further information.
    assertThatThrownBy(() -> invoice.applyPayment(money("-50.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(e -> ((PaymentRejectedException) e).getReason())
        .isEqualTo(PaymentRejectionReason.AMOUNT_NOT_POSITIVE);
  }

  @Test
  @DisplayName("a rejected payment leaves no trace on the invoice")
  void rejectedPaymentLeavesInvoiceUnchanged() {
    Invoice invoice = invoiceOnActivePolicy("450.00");
    invoice.applyPayment(money("50.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);

    InvoiceStatus statusBefore = invoice.getStatus();

    assertThatThrownBy(() -> invoice.applyPayment(money("1000.00"), PaymentMethod.CARD, "REF-2", RECEIVED_AT))
        .isInstanceOf(PaymentRejectedException.class);

    assertThat(invoice.getStatus()).isEqualTo(statusBefore);
    assertThat(invoice.getPayments()).hasSize(1);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("50.00");
  }
}
