package com.insurancebilling.domain;

import static com.insurancebilling.domain.BillingFixtures.PROCESSED_AT;
import static com.insurancebilling.domain.BillingFixtures.TERM_START;
import static com.insurancebilling.domain.BillingFixtures.evenTerm;
import static com.insurancebilling.domain.BillingFixtures.money;
import static com.insurancebilling.domain.BillingFixtures.payNextInstallment;
import static com.insurancebilling.domain.BillingFixtures.unevenTerm;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a billing account reports about the terms billed to it.
 *
 * <p>Everything here is derived from the terms rather than stored on the account, so these tests are
 * mostly about the account agreeing with its own ledgers.
 */
class BillingAccountTest {

  @Test
  @DisplayName("a new account has no failed payments behind it")
  void aNewAccountHasNoFailedPayments() {
    BillingAccount account = evenTerm().getBillingAccount();

    assertThat(account.getNsfCount()).isZero();
    assertThat(account.getReturnedPaymentCount()).isZero();
  }

  @Test
  @DisplayName("the account balance is the sum of the balances of the terms billed to it")
  void theAccountBalanceIsTheSumOfItsTerms() {
    PolicyTerm term = evenTerm();
    BillingAccount account = term.getBillingAccount();

    assertThat(account.getTotalBalance()).isEqualByComparingTo(term.getBalance());

    payNextInstallment(term, "TXN-PAY-1");

    assertThat(account.getTotalBalance())
        .as("an account total that does not follow its terms is a second source of truth")
        .isEqualByComparingTo("1460.80");
  }

  @Test
  @DisplayName("the next payment due is the earliest unsettled installment")
  void theNextPaymentDueIsTheEarliestUnsettledInstallment() {
    PolicyTerm term = evenTerm();
    BillingAccount account = term.getBillingAccount();

    assertThat(account.nextPaymentAmount()).get().satisfies(amount -> assertThat(amount).isEqualByComparingTo("130.80"));
    assertThat(account.nextPaymentDate()).contains(term.getInstallments().get(0).getDueDate());

    payNextInstallment(term, "TXN-PAY-1");

    assertThat(account.nextPaymentAmount()).get().satisfies(amount -> assertThat(amount).isEqualByComparingTo("132.80"));
    assertThat(account.nextInstallmentDue()).get().satisfies(next -> assertThat(next.getSequenceNumber()).isEqualTo(2));
  }

  @Test
  @DisplayName("an account whose schedule is finished has no next payment")
  void anAccountWithNothingLeftToPayHasNoNextPayment() {
    PolicyTerm term = unevenTerm();
    for (int installment = 0; installment < 12; installment++) {
      payNextInstallment(term, "TXN-PAY-" + installment);
    }

    BillingAccount account = term.getBillingAccount();

    assertThat(account.nextPaymentDate()).isEmpty();
    assertThat(account.nextPaymentAmount()).isEmpty();
    assertThat(account.getTotalBalance()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("money no installment claimed is reported as a credit, not as a negative")
  void unclaimedMoneyIsReportedAsACredit() {
    PolicyTerm term = evenTerm();
    BillingAccount account = term.getBillingAccount();

    term.recordPayment("TXN-PAY-1", money("150.00"), "Payment", TERM_START, PROCESSED_AT);

    assertThat(account.getUnappliedAmount())
        .as("a policyholder reading their statement expects a credit shown as a positive number")
        .isEqualByComparingTo("19.20");
  }

  @Test
  @DisplayName("an account with nothing in suspense reports no unapplied amount")
  void anAccountWithNothingInSuspenseReportsZero() {
    PolicyTerm term = evenTerm();
    payNextInstallment(term, "TXN-PAY-1");

    assertThat(term.getBillingAccount().getUnappliedAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("the terms billed to an account cannot be modified through the getter")
  void theTermListIsNotModifiableByCallers() {
    BillingAccount account = evenTerm().getBillingAccount();

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> account.getTerms().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("an account carries the payment plan and method its schedule is collected by")
  void anAccountCarriesItsPaymentPlanAndMethod() {
    BillingAccount account = evenTerm().getBillingAccount();

    assertThat(account.getPaymentPlan()).isEqualTo(PaymentPlan.MONTHLY);
    assertThat(account.getPaymentMethod()).isEqualTo(PaymentMethod.PRE_AUTHORIZED_DEBIT);
    assertThat(account.getBankAccount().getMaskedAccountNumber()).isEqualTo("****204");
  }
}
