package com.insurancebilling.domain;

import static com.insurancebilling.domain.BillingFixtures.PROCESSED_AT;
import static com.insurancebilling.domain.BillingFixtures.TERM_START;
import static com.insurancebilling.domain.BillingFixtures.boundTerm;
import static com.insurancebilling.domain.BillingFixtures.evenTerm;
import static com.insurancebilling.domain.BillingFixtures.money;
import static com.insurancebilling.domain.BillingFixtures.payNextInstallment;
import static com.insurancebilling.domain.BillingFixtures.unboundTerm;
import static com.insurancebilling.domain.BillingFixtures.unevenTerm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The billing ledger: what is posted, what it does to the balance, and what a payment settles.
 *
 * <p>The running balance is derived from the ledger rather than stored, so the tests that matter most
 * here are the ones that would catch it drifting — a balance recomputed from the lines can only be
 * wrong if the lines are wrong.
 */
class PolicyTermLedgerTest {

  @Test
  @DisplayName("binding a term posts the whole term to the ledger and puts it in force")
  void bindingPostsTheWholeTermAndPutsItInForce() {
    PolicyTerm term = unboundTerm("1440.00", "129.60", "2.00", PaymentPlan.MONTHLY);
    assertThat(term.getStatus()).isEqualTo(TermStatus.PENDING);

    term.postNewBusiness("TXN-1", "New business", TERM_START, PROCESSED_AT);

    assertThat(term.getStatus()).isEqualTo(TermStatus.IN_FORCE);
    assertThat(term.getTransactions()).singleElement().satisfies(entry -> {
      assertThat(entry.getType()).isEqualTo(TransactionType.NEW_BUSINESS);
      assertThat(entry.getPremiumAmount()).isEqualByComparingTo("1440.00");
      assertThat(entry.getTaxAmount()).isEqualByComparingTo("129.60");
      assertThat(entry.getFeeAmount()).as("eleven installment fees of 2.00").isEqualByComparingTo("22.00");
    });
    assertThat(term.getBalance()).isEqualByComparingTo("1591.60");
  }

  @Test
  @DisplayName("the term is posted for exactly what its schedule will collect")
  void thePostedTermMatchesWhatTheScheduleCollects() {
    PolicyTerm term = unevenTerm();

    assertThat(term.getBalance())
        .as("a schedule that collects more or less than the ledger posted would never settle")
        .isEqualByComparingTo(term.getScheduledTotal());
  }

  @Test
  @DisplayName("a term cannot be bound twice")
  void aTermCannotBeBoundTwice() {
    PolicyTerm term = evenTerm();

    assertThatThrownBy(() -> term.postNewBusiness("TXN-2", "New business", TERM_START, PROCESSED_AT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already been bound");
  }

  @Test
  @DisplayName("a payment reduces the balance by exactly what it settles")
  void aPaymentReducesTheBalanceByWhatItSettles() {
    PolicyTerm term = evenTerm();

    payNextInstallment(term, "TXN-PAY-1");

    assertThat(term.getBalance()).isEqualByComparingTo("1460.80");
    assertThat(term.getInstallmentsRemaining()).isEqualTo(11);
  }

  @Test
  @DisplayName("a payment is split across the ledger columns by what the installment was made of")
  void aPaymentIsSplitByTheInstallmentItSettles() {
    PolicyTerm term = evenTerm();

    BillingTransaction payment = payNextInstallment(term, "TXN-PAY-1");

    assertThat(payment.getPremiumAmount()).isEqualByComparingTo("-120.00");
    assertThat(payment.getTaxAmount()).isEqualByComparingTo("-10.80");
    assertThat(payment.getFeeAmount()).as("the down payment carries no fee").isEqualByComparingTo("0.00");
    assertThat(payment.getAmount()).isEqualByComparingTo("-130.80");
  }

  @Test
  @DisplayName("every ledger line's columns add up to the amount it moves")
  void everyLineReconcilesAgainstItsOwnColumns() {
    PolicyTerm term = unevenTerm();
    payNextInstallment(term, "TXN-PAY-1");
    payNextInstallment(term, "TXN-PAY-2");

    assertThat(term.getTransactions()).allSatisfy(entry ->
        assertThat(entry.getAmount())
            .as("line %s does not reconcile against its own columns", entry.getReference())
            .isEqualByComparingTo(
                entry.getPremiumAmount()
                    .add(entry.getTaxAmount())
                    .add(entry.getFeeAmount())
                    .add(entry.getSuspenseAmount())));
  }

  @Test
  @DisplayName("the running balance on each line is the ordered sum of the lines up to it")
  void theRunningBalanceIsTheOrderedSumOfTheLines() {
    PolicyTerm term = unevenTerm();
    payNextInstallment(term, "TXN-PAY-1");
    payNextInstallment(term, "TXN-PAY-2");
    payNextInstallment(term, "TXN-PAY-3");

    List<BillingTransaction> ledger = term.getTransactions();
    BigDecimal accumulated = Money.ZERO;
    for (BillingTransaction entry : ledger) {
      accumulated = accumulated.add(entry.getAmount());
      assertThat(term.balanceAfter(entry))
          .as("the running balance shown against %s", entry.getReference())
          .isEqualByComparingTo(accumulated);
    }
    assertThat(term.balanceAfter(ledger.get(ledger.size() - 1)))
        .as("the last line's running balance is the term balance")
        .isEqualByComparingTo(term.getBalance());
  }

  @Test
  @DisplayName("settling every installment leaves the term at a zero balance")
  void settlingEveryInstallmentLeavesNothingOwed() {
    PolicyTerm term = unevenTerm();

    for (int installment = 0; installment < 12; installment++) {
      payNextInstallment(term, "TXN-PAY-" + installment);
    }

    assertThat(term.getBalance())
        .as("an uneven term that does not settle to zero has lost the rounding remainder")
        .isEqualByComparingTo("0.00");
    assertThat(term.getInstallmentsRemaining()).isZero();
    assertThat(term.nextUnpaidInstallment()).isEmpty();
  }

  @Test
  @DisplayName("a payment settles the oldest unpaid installment first")
  void aPaymentSettlesTheOldestUnpaidInstallmentFirst() {
    PolicyTerm term = evenTerm();

    payNextInstallment(term, "TXN-PAY-1");

    assertThat(term.getInstallments().get(0).getStatus()).isEqualTo(InstallmentStatus.PAID);
    assertThat(term.getInstallments().get(1).getStatus()).isNotEqualTo(InstallmentStatus.PAID);
    assertThat(term.nextUnpaidInstallment())
        .get()
        .satisfies(next -> assertThat(next.getSequenceNumber()).isEqualTo(2));
  }

  @Test
  @DisplayName("one payment covering several installments settles them in order")
  void onePaymentCoveringSeveralInstallmentsSettlesThemInOrder() {
    PolicyTerm term = evenTerm();

    term.recordPayment("TXN-PAY-1", money("396.40"), "Payment", TERM_START, PROCESSED_AT);

    assertThat(term.getInstallments().subList(0, 3))
        .allSatisfy(installment -> assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.PAID));
    assertThat(term.getInstallmentsRemaining()).isEqualTo(9);
    assertThat(term.getBalance()).isEqualByComparingTo("1195.20");
  }

  @Test
  @DisplayName("money that falls short of the next installment is held in suspense")
  void moneyShortOfAnInstallmentIsHeldInSuspense() {
    PolicyTerm term = evenTerm();

    BillingTransaction payment = term.recordPayment("TXN-PAY-1", money("100.00"), "Payment", TERM_START, PROCESSED_AT);

    assertThat(payment.getSuspenseAmount())
        .as("100.00 does not cover the 130.80 down payment, so none of it is applied")
        .isEqualByComparingTo("-100.00");
    assertThat(payment.getPremiumAmount()).isEqualByComparingTo("0.00");
    assertThat(term.getInstallmentsRemaining()).as("nothing is settled by a short payment").isEqualTo(12);
    assertThat(term.getBalance())
        .as("the money still reduces what is owed even though no installment claimed it")
        .isEqualByComparingTo("1491.60");
  }

  @Test
  @DisplayName("the part of a payment beyond a whole installment goes to suspense")
  void theRemainderOfAPaymentGoesToSuspense() {
    PolicyTerm term = evenTerm();

    BillingTransaction payment = term.recordPayment("TXN-PAY-1", money("150.00"), "Payment", TERM_START, PROCESSED_AT);

    assertThat(payment.getPremiumAmount()).isEqualByComparingTo("-120.00");
    assertThat(payment.getTaxAmount()).isEqualByComparingTo("-10.80");
    assertThat(payment.getSuspenseAmount()).isEqualByComparingTo("-19.20");
    assertThat(payment.getAmount()).isEqualByComparingTo("-150.00");
    assertThat(term.getInstallmentsRemaining()).isEqualTo(11);
  }

  @Test
  @DisplayName("a payment beyond the balance is refused with the reason that names it")
  void aPaymentBeyondTheBalanceIsRefused() {
    PolicyTerm term = evenTerm();

    assertThatThrownBy(() -> term.recordPayment("TXN-PAY-1", money("5000.00"), "Payment", TERM_START, PROCESSED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(thrown -> ((PaymentRejectedException) thrown).getReason())
        .isEqualTo(PaymentRejectionReason.EXCEEDS_OUTSTANDING_BALANCE);
  }

  @Test
  @DisplayName("a payment of zero or less is refused before anything else is considered")
  void aNonPositivePaymentIsRefusedFirst() {
    PolicyTerm term = evenTerm();
    term.setStatus(TermStatus.CANCELLED);

    assertThatThrownBy(() -> term.recordPayment("TXN-PAY-1", money("-50.00"), "Payment", TERM_START, PROCESSED_AT))
        .as("the amount is the problem the caller can fix without knowing the term's state")
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(thrown -> ((PaymentRejectedException) thrown).getReason())
        .isEqualTo(PaymentRejectionReason.AMOUNT_NOT_POSITIVE);
  }

  @Test
  @DisplayName("a payment carrying more precision than the platform stores is refused")
  void anOverPrecisePaymentIsRefused() {
    PolicyTerm term = evenTerm();

    assertThatThrownBy(() -> term.recordPayment("TXN-PAY-1", money("130.801"), "Payment", TERM_START, PROCESSED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .extracting(thrown -> ((PaymentRejectedException) thrown).getReason())
        .isEqualTo(PaymentRejectionReason.AMOUNT_SCALE_INVALID);
  }

  @ParameterizedTest
  @EnumSource(value = TermStatus.class, names = {"PENDING", "EXPIRED", "CANCELLED"})
  @DisplayName("a term that is not in force accepts no payment, whichever way it left")
  void aTermNotInForceAcceptsNoPayment(TermStatus status) {
    PolicyTerm term = evenTerm();
    term.setStatus(status);

    assertThatThrownBy(() -> term.recordPayment("TXN-PAY-1", money("130.80"), "Payment", TERM_START, PROCESSED_AT))
        .isInstanceOf(PaymentRejectedException.class)
        .hasMessageContaining(status.name())
        .extracting(thrown -> ((PaymentRejectedException) thrown).getReason())
        .isEqualTo(PaymentRejectionReason.TERM_NOT_IN_FORCE);
  }

  @Test
  @DisplayName("a refused payment leaves no trace on the ledger or the schedule")
  void aRefusedPaymentLeavesNoTrace() {
    PolicyTerm term = evenTerm();
    int linesBefore = term.getTransactions().size();

    assertThatThrownBy(() -> term.recordPayment("TXN-PAY-1", money("5000.00"), "Payment", TERM_START, PROCESSED_AT))
        .isInstanceOf(PaymentRejectedException.class);

    assertThat(term.getTransactions()).hasSize(linesBefore);
    assertThat(term.getInstallmentsRemaining()).isEqualTo(12);
    assertThat(term.getBalance()).isEqualByComparingTo("1591.60");
  }

  @Test
  @DisplayName("the balance of a term nothing has been paid on is what was posted at binding")
  void anUntouchedTermOwesWhatWasPosted() {
    PolicyTerm term = boundTerm("2400.00", "216.00", "3.00", PaymentPlan.MONTHLY);

    assertThat(term.getBalance()).isEqualByComparingTo("2649.00");
    assertThat(term.getScheduledTotal()).isEqualByComparingTo("2649.00");
  }

  @Test
  @DisplayName("a ledger line from another term cannot have its running balance read here")
  void aLineFromAnotherTermIsRefused() {
    PolicyTerm term = evenTerm();
    PolicyTerm other = unevenTerm();

    assertThatThrownBy(() -> term.balanceAfter(other.getTransactions().get(0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not on term");
  }

  @Test
  @DisplayName("the ledger and the schedule cannot be modified through their getters")
  void theLedgerAndScheduleAreNotModifiableByCallers() {
    PolicyTerm term = evenTerm();

    assertThatThrownBy(() -> term.getTransactions().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> term.getInstallments().clear()).isInstanceOf(UnsupportedOperationException.class);
  }
}
