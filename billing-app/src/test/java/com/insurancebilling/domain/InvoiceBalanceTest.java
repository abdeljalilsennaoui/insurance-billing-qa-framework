package com.insurancebilling.domain;

import static com.insurancebilling.domain.DomainFixtures.RECEIVED_AT;
import static com.insurancebilling.domain.DomainFixtures.invoiceOnActivePolicy;
import static com.insurancebilling.domain.DomainFixtures.money;
import static com.insurancebilling.domain.DomainFixtures.overdueInvoice;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Balance arithmetic and status transitions on {@link Invoice}.
 *
 * <p>These are plain unit tests: no Spring context, no database. That is the point of keeping the
 * rules on the entity rather than in a service.
 */
class InvoiceBalanceTest {

  @Test
  @DisplayName("a new invoice is unpaid with the full amount outstanding")
  void newInvoiceIsUnpaidWithFullBalanceOutstanding() {
    Invoice invoice = invoiceOnActivePolicy("450.00");

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0.00");
    assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("450.00");
    assertThat(invoice.isSettled()).isFalse();
  }

  @Test
  @DisplayName("a partial payment reduces the balance and moves the invoice to partially paid")
  void partialPaymentReducesBalance() {
    Invoice invoice = invoiceOnActivePolicy("450.00");

    invoice.applyPayment(money("150.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("150.00");
    assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("300.00");
    assertThat(invoice.isSettled()).isFalse();
  }

  @Test
  @DisplayName("a payment equal to the outstanding balance settles the invoice")
  void fullPaymentSettlesInvoice() {
    Invoice invoice = invoiceOnActivePolicy("450.00");

    invoice.applyPayment(money("450.00"), PaymentMethod.BANK_TRANSFER, "REF-1", RECEIVED_AT);

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("0.00");
    assertThat(invoice.isSettled()).isTrue();
  }

  @Test
  @DisplayName("sequential partial payments settle the invoice exactly")
  void sequentialPartialPaymentsSettleInvoiceExactly() {
    Invoice invoice = invoiceOnActivePolicy("100.00");

    invoice.applyPayment(money("33.33"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);
    invoice.applyPayment(money("33.33"), PaymentMethod.CARD, "REF-2", RECEIVED_AT);
    invoice.applyPayment(money("33.34"), PaymentMethod.CARD, "REF-3", RECEIVED_AT);

    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("100.00");
    assertThat(invoice.getOutstandingBalance()).isEqualByComparingTo("0.00");
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    assertThat(invoice.getPayments()).hasSize(3);
  }

  @Test
  @DisplayName("the paid amount is the sum of the recorded payments, in order")
  void amountPaidIsSumOfPayments() {
    Invoice invoice = invoiceOnActivePolicy("900.00");

    invoice.applyPayment(money("100.50"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);
    invoice.applyPayment(money("200.25"), PaymentMethod.DIRECT_DEBIT, "REF-2", RECEIVED_AT);

    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("300.75");
    assertThat(invoice.getPayments())
        .extracting(Payment::getReference)
        .containsExactly("REF-1", "REF-2");
  }

  @Test
  @DisplayName("the payment list cannot be modified from outside the invoice")
  void paymentListIsUnmodifiable() {
    Invoice invoice = invoiceOnActivePolicy("100.00");

    assertThat(invoice.getPayments()).isUnmodifiable();
  }

  @Test
  @DisplayName("an unpaid invoice past its due date is overdue")
  void unpaidInvoicePastDueDateIsOverdue() {
    Invoice invoice = overdueInvoice("200.00");

    assertThat(invoice.isOverdue(LocalDate.now())).isTrue();
  }

  @Test
  @DisplayName("a settled invoice is never overdue, however late it was paid")
  void settledInvoiceIsNotOverdue() {
    Invoice invoice = overdueInvoice("200.00");

    invoice.applyPayment(money("200.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);

    assertThat(invoice.isOverdue(LocalDate.now())).isFalse();
  }

  @Test
  @DisplayName("a cancelled invoice is not reported as overdue")
  void cancelledInvoiceIsNotOverdue() {
    Invoice invoice = overdueInvoice("200.00");

    invoice.cancel();

    assertThat(invoice.isOverdue(LocalDate.now())).isFalse();
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
  }

  @Test
  @DisplayName("an untouched past-due invoice is promoted to overdue status")
  void untouchedPastDueInvoiceIsPromotedToOverdue() {
    Invoice invoice = overdueInvoice("200.00");

    invoice.markOverdueIfDue(LocalDate.now());

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
  }

  @Test
  @DisplayName("a part-paid past-due invoice keeps partially paid status but still reads as overdue")
  void partPaidPastDueInvoiceKeepsPartiallyPaidStatus() {
    Invoice invoice = overdueInvoice("200.00");
    invoice.applyPayment(money("50.00"), PaymentMethod.CARD, "REF-1", RECEIVED_AT);

    invoice.markOverdueIfDue(LocalDate.now());

    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    assertThat(invoice.isOverdue(LocalDate.now())).isTrue();
  }

  @Test
  @DisplayName("an invoice within its due date is not overdue")
  void invoiceWithinDueDateIsNotOverdue() {
    Invoice invoice = invoiceOnActivePolicy("200.00");

    invoice.markOverdueIfDue(LocalDate.now());

    assertThat(invoice.isOverdue(LocalDate.now())).isFalse();
    assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
  }
}
