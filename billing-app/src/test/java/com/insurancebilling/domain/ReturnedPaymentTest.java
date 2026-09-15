package com.insurancebilling.domain;

import static com.insurancebilling.domain.BillingFixtures.PROCESSED_AT;
import static com.insurancebilling.domain.BillingFixtures.TERM_START;
import static com.insurancebilling.domain.BillingFixtures.evenTerm;
import static com.insurancebilling.domain.BillingFixtures.money;
import static com.insurancebilling.domain.BillingFixtures.payNextInstallment;
import static com.insurancebilling.domain.BillingFixtures.unevenTerm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What happens when the bank refuses a payment the insurer has already recorded.
 *
 * <p>Three things move together — the ledger, the schedule and the account's counters — which is why
 * they are one method on the entity rather than three calls a service makes in order. These tests exist
 * mostly to prove they cannot come apart.
 */
class ReturnedPaymentTest {

  private static BillingTransaction returnOf(PolicyTerm term, BillingTransaction payment, ReturnReason reason) {
    return term.returnPayment("TXN-RET-1", "TXN-NSF-1", payment, reason, money("25.00"), TERM_START, PROCESSED_AT);
  }

  @Test
  @DisplayName("a returned payment restores the balance to the cent")
  void aReturnedPaymentRestoresTheBalance() {
    PolicyTerm term = unevenTerm();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");
    assertThat(term.getBalance()).isEqualByComparingTo("1021.13");

    term.returnPayment("TXN-RET-1", "TXN-NSF-1", payment, ReturnReason.INSUFFICIENT_FUNDS, Money.ZERO, TERM_START, PROCESSED_AT);

    assertThat(term.getBalance())
        .as("reversing an uneven payment must return the balance exactly to where it was")
        .isEqualByComparingTo("1112.00");
  }

  @Test
  @DisplayName("the reversal negates the original payment column for column")
  void theReversalNegatesTheOriginalColumnForColumn() {
    PolicyTerm term = evenTerm();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");

    BillingTransaction reversal = returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS);

    assertThat(reversal.getType()).isEqualTo(TransactionType.PAYMENT_RETURNED);
    assertThat(reversal.getPremiumAmount()).isEqualByComparingTo(payment.getPremiumAmount().negate());
    assertThat(reversal.getTaxAmount()).isEqualByComparingTo(payment.getTaxAmount().negate());
    assertThat(reversal.getFeeAmount()).isEqualByComparingTo(payment.getFeeAmount().negate());
    assertThat(reversal.getSuspenseAmount()).isEqualByComparingTo(payment.getSuspenseAmount().negate());
    assertThat(reversal.getReversalOf()).isSameAs(payment);
  }

  @Test
  @DisplayName("the installment the payment settled goes back to unsettled, marked reversed")
  void theSettledInstallmentGoesBackToUnsettled() {
    PolicyTerm term = evenTerm();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");
    assertThat(term.getInstallmentsRemaining()).isEqualTo(11);

    returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS);

    Installment first = term.getInstallments().get(0);
    assertThat(first.getStatus())
        .as("an installment that bounced is not the same as one never paid")
        .isEqualTo(InstallmentStatus.REVERSED);
    assertThat(first.getSettledBy()).isNull();
    assertThat(term.getInstallmentsRemaining()).isEqualTo(12);
    assertThat(term.nextUnpaidInstallment()).get().satisfies(next -> assertThat(next.getSequenceNumber()).isEqualTo(1));
  }

  @Test
  @DisplayName("every installment a single payment settled is reversed together")
  void everyInstallmentThatPaymentSettledIsReversedTogether() {
    PolicyTerm term = evenTerm();
    BillingTransaction payment = term.recordPayment("TXN-PAY-1", money("396.40"), "Payment", TERM_START, PROCESSED_AT);
    assertThat(term.getInstallmentsRemaining()).isEqualTo(9);

    returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS);

    assertThat(term.getInstallments().subList(0, 3))
        .allSatisfy(installment -> assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.REVERSED));
    assertThat(term.getInstallmentsRemaining()).isEqualTo(12);
  }

  @Test
  @DisplayName("a returned payment raises a fee that is charged on top of the restored balance")
  void aReturnedPaymentRaisesAFeeOnTopOfTheRestoredBalance() {
    PolicyTerm term = evenTerm();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");

    returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS);

    assertThat(term.getBalance()).isEqualByComparingTo("1616.60");
    assertThat(term.getTransactions())
        .filteredOn(entry -> entry.getType() == TransactionType.NSF_FEE)
        .singleElement()
        .satisfies(fee -> {
          assertThat(fee.getFeeAmount()).isEqualByComparingTo("25.00");
          assertThat(fee.getPremiumAmount()).as("a fee is not premium and must not reach the premium column").isEqualByComparingTo("0.00");
          assertThat(fee.getTaxAmount()).isEqualByComparingTo("0.00");
        });
  }

  @Test
  @DisplayName("a waived fee posts no line at all rather than a line for nothing")
  void aWaivedFeePostsNoLine() {
    PolicyTerm term = evenTerm();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");

    term.returnPayment("TXN-RET-1", "TXN-NSF-1", payment, ReturnReason.INVALID_ACCOUNT, Money.ZERO, TERM_START, PROCESSED_AT);

    assertThat(term.getTransactions()).noneMatch(entry -> entry.getType() == TransactionType.NSF_FEE);
    assertThat(term.getBalance()).isEqualByComparingTo("1591.60");
  }

  @Test
  @DisplayName("a payment refused for want of money counts against both tallies")
  void insufficientFundsCountsAgainstBothTallies() {
    PolicyTerm term = evenTerm();
    BillingAccount account = term.getBillingAccount();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");

    returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS);

    assertThat(account.getNsfCount()).isEqualTo(1);
    assertThat(account.getReturnedPaymentCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(value = ReturnReason.class, names = {"ACCOUNT_CLOSED", "PAYMENT_STOPPED", "INVALID_ACCOUNT"})
  @DisplayName("a payment refused for any other reason is returned but is not an NSF")
  void otherReasonsAreReturnedButNotNsf(ReturnReason reason) {
    PolicyTerm term = evenTerm();
    BillingAccount account = term.getBillingAccount();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");

    returnOf(term, payment, reason);

    assertThat(account.getReturnedPaymentCount()).isEqualTo(1);
    assertThat(account.getNsfCount())
        .as("%s is a payment problem, not a funding problem, and collections treats it differently", reason)
        .isZero();
  }

  @Test
  @DisplayName("the same payment cannot be returned twice")
  void theSamePaymentCannotBeReturnedTwice() {
    PolicyTerm term = evenTerm();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");
    returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS);

    assertThatThrownBy(() ->
            term.returnPayment("TXN-RET-2", "TXN-NSF-2", payment, ReturnReason.INSUFFICIENT_FUNDS, money("25.00"), TERM_START, PROCESSED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .hasMessageContaining("already been returned")
        .extracting(thrown -> ((PaymentRejectedException) thrown).getReason())
        .isEqualTo(PaymentRejectionReason.PAYMENT_ALREADY_RETURNED);
  }

  @Test
  @DisplayName("a second return attempt leaves the counters where the first one left them")
  void aSecondReturnAttemptDoesNotMoveTheCounters() {
    PolicyTerm term = evenTerm();
    BillingAccount account = term.getBillingAccount();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");
    returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS);

    assertThatThrownBy(() -> returnOf(term, payment, ReturnReason.INSUFFICIENT_FUNDS))
        .isInstanceOf(PaymentRejectedException.class);

    assertThat(account.getReturnedPaymentCount()).isEqualTo(1);
    assertThat(account.getNsfCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("a line that is not a payment cannot be returned")
  void aLineThatIsNotAPaymentCannotBeReturned() {
    PolicyTerm term = evenTerm();
    BillingTransaction newBusiness = term.getTransactions().get(0);

    assertThatThrownBy(() ->
            term.returnPayment("TXN-RET-1", "TXN-NSF-1", newBusiness, ReturnReason.INSUFFICIENT_FUNDS, Money.ZERO, TERM_START, PROCESSED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .hasMessageContaining("is not a payment on term");
  }

  @Test
  @DisplayName("a payment on another term cannot be returned here")
  void aPaymentOnAnotherTermCannotBeReturnedHere() {
    PolicyTerm term = evenTerm();
    PolicyTerm other = unevenTerm();
    BillingTransaction otherPayment = payNextInstallment(other, "TXN-PAY-OTHER");

    assertThatThrownBy(() ->
            term.returnPayment("TXN-RET-1", "TXN-NSF-1", otherPayment, ReturnReason.INSUFFICIENT_FUNDS, Money.ZERO, TERM_START, PROCESSED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .hasMessageContaining("is not a payment on term");
  }

  @Test
  @DisplayName("a reversed installment can be paid again and the term still settles to zero")
  void aReversedInstallmentCanBePaidAgain() {
    PolicyTerm term = unevenTerm();
    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");
    term.returnPayment("TXN-RET-1", "TXN-NSF-1", payment, ReturnReason.INSUFFICIENT_FUNDS, Money.ZERO, TERM_START, PROCESSED_AT);

    for (int attempt = 0; attempt < 12; attempt++) {
      payNextInstallment(term, "TXN-RETRY-" + attempt);
    }

    assertThat(term.getBalance()).isEqualByComparingTo("0.00");
    assertThat(term.getInstallmentsRemaining()).isZero();
  }
}
