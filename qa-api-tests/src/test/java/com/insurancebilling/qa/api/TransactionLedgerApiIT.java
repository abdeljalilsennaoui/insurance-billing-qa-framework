package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.BillingTransactionDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import com.insurancebilling.qa.api.model.TermPaymentBody;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.List;
import org.testng.annotations.Test;

/**
 * The billing ledger over HTTP: what a payment posts, and what the running balance says.
 *
 * <p>The ledger is returned newest first, which is how it is read. The arithmetic runs the other way, so
 * the invariant tests walk the list in reverse.
 */
public class TransactionLedgerApiIT extends BaseApiTest {

  @Test(groups = {"smoke", "regression"})
  public void bindingATermPostsTheWholeTermAsOneLine() {
    PolicyTermDto term = testData.boundTerm();

    List<BillingTransactionDto> ledger = billing.ledger(term.termReference());

    assertThat(ledger).singleElement().satisfies(line -> {
      assertThat(line.type()).isEqualTo("NEW_BUSINESS");
      assertThat(line.premiumAmount()).isEqualByComparingTo("1440.00");
      assertThat(line.taxAmount()).isEqualByComparingTo("129.60");
      assertThat(line.feeAmount()).as("eleven installment fees of 2.00").isEqualByComparingTo("22.00");
      assertThat(line.balanceAfter()).isEqualByComparingTo("1591.60");
    });
  }

  @Test(groups = {"smoke", "regression"})
  public void aPaymentReducesTheBalanceAndSettlesAnInstallment() {
    PolicyTermDto term = testData.boundTerm();

    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");

    assertThat(payment.type()).isEqualTo("PAYMENT");
    assertThat(payment.amount()).isEqualByComparingTo("-130.80");
    assertThat(payment.balanceAfter()).isEqualByComparingTo("1460.80");
    assertThat(billing.term(term.termReference()).installmentsRemaining()).isEqualTo(11);
  }

  @Test(groups = "regression")
  public void aPaymentIsSplitByTheInstallmentItSettles() {
    PolicyTermDto term = testData.boundTerm();

    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");

    assertThat(payment.premiumAmount()).isEqualByComparingTo("-120.00");
    assertThat(payment.taxAmount()).isEqualByComparingTo("-10.80");
    assertThat(payment.feeAmount()).as("the down payment carries no fee").isEqualByComparingTo("0.00");
    assertThat(payment.suspenseAmount()).isEqualByComparingTo("0.00");
  }

  @Test(groups = "regression")
  public void everyLedgerLineReconcilesAgainstItsOwnColumns() {
    PolicyTermDto term = testData.boundTerm();
    billing.pay(term.termReference(), "130.80");
    billing.pay(term.termReference(), "150.00");

    assertThat(billing.ledger(term.termReference()))
        .allSatisfy(
            line ->
                assertThat(line.amount())
                    .as("line %s does not reconcile against its own columns", line.reference())
                    .isEqualByComparingTo(
                        line.premiumAmount()
                            .add(line.taxAmount())
                            .add(line.feeAmount())
                            .add(line.suspenseAmount())));
  }

  @Test(groups = "regression")
  public void theRunningBalanceIsTheOrderedSumOfTheLinesBelowIt() {
    PolicyTermDto term = testData.boundTerm();
    billing.pay(term.termReference(), "130.80");
    billing.pay(term.termReference(), "132.80");

    List<BillingTransactionDto> newestFirst = billing.ledger(term.termReference());
    List<BillingTransactionDto> oldestFirst = new java.util.ArrayList<>(newestFirst);
    java.util.Collections.reverse(oldestFirst);

    BigDecimal accumulated = BigDecimal.ZERO;
    for (BillingTransactionDto line : oldestFirst) {
      accumulated = accumulated.add(line.amount());
      assertThat(line.balanceAfter())
          .as("the running balance shown against %s", line.reference())
          .isEqualByComparingTo(accumulated);
    }
    assertThat(newestFirst.get(0).balanceAfter())
        .as("the newest line's running balance is what the term now owes")
        .isEqualByComparingTo(billing.term(term.termReference()).balance());
  }

  @Test(groups = "regression")
  public void settlingEveryInstallmentLeavesAnUnevenTermAtZero() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.unevenTerm(account.accountReference());

    billing.pay(term.termReference(), "90.87");
    for (int installment = 0; installment < 11; installment++) {
      billing.pay(term.termReference(), "92.83");
    }

    PolicyTermDto settled = billing.term(term.termReference());
    assertThat(settled.balance())
        .as("a term that does not settle to zero has lost the rounding remainder somewhere")
        .isEqualByComparingTo("0.00");
    assertThat(settled.installmentsRemaining()).isZero();
  }

  @Test(groups = "regression")
  public void onePaymentCoveringSeveralInstallmentsSettlesThemInOrder() {
    PolicyTermDto term = testData.boundTerm();

    billing.pay(term.termReference(), "396.40");

    assertThat(billing.term(term.termReference()).installmentsRemaining()).isEqualTo(9);
    assertThat(billing.schedule(term.termReference()).subList(0, 3))
        .allSatisfy(installment -> assertThat(installment.status()).isEqualTo("PAID"));
  }

  @Test(groups = "regression")
  public void moneyThatSettlesNoInstallmentIsHeldInSuspense() {
    PolicyTermDto term = testData.boundTerm();

    BillingTransactionDto payment = billing.pay(term.termReference(), "100.00");

    assertThat(payment.suspenseAmount())
        .as("100.00 does not cover the 130.80 down payment, so no installment claims any of it")
        .isEqualByComparingTo("-100.00");
    assertThat(billing.term(term.termReference()).installmentsRemaining()).isEqualTo(12);
    assertThat(billing.account(term.accountReference()).unappliedAmount())
        .as("a policyholder reading their statement expects a credit shown as a positive number")
        .isEqualByComparingTo("100.00");
  }

  @Test(groups = {"negative", "regression"})
  public void aPaymentBeyondTheBalanceIsRefusedWithTheReasonThatNamesIt() {
    PolicyTermDto term = testData.boundTerm();

    Response response = billing.payRaw(term.termReference(), TermPaymentBody.of("99999.00"));

    assertThat(response.statusCode())
        .as("a well-formed request refused by a billing rule is 422, not 400")
        .isEqualTo(422);
    assertThat(response.jsonPath().getString("code")).isEqualTo("EXCEEDS_OUTSTANDING_BALANCE");
    assertThat(billing.ledger(term.termReference()))
        .as("a refused payment must not reach the ledger")
        .hasSize(1);
  }

  @Test(
      groups = {"negative", "regression"},
      dataProvider = "invalidTermPaymentAmounts",
      dataProviderClass = BillingDataProviders.class)
  public void invalidAmountsAreRefusedWithTheirOwnReason(
      String amount, String expectedCode, String description) {
    PolicyTermDto term = testData.boundTerm();

    Response response = billing.payRaw(term.termReference(), TermPaymentBody.of(amount));

    assertThat(response.statusCode())
        .as("a well-formed request refused by a billing rule is 422 (%s)", description)
        .isEqualTo(422);
    assertThat(response.jsonPath().getString("code"))
        .as("rejection reason for %s", description)
        .isEqualTo(expectedCode);
    assertThat(billing.term(term.termReference()).balance())
        .as("a refused payment must not alter the balance")
        .isEqualByComparingTo("1591.60");
  }

  @Test(
      groups = {"negative", "regression"},
      dataProvider = "malformedTermPaymentBodies",
      dataProviderClass = BillingDataProviders.class)
  public void malformedBodiesAreRejectedAsBadRequestsNotRuleViolations(
      String rawJson, String expectedCode, String description) {
    PolicyTermDto term = testData.boundTerm();

    Response response = billing.payWithRawBody(term.termReference(), rawJson);

    assertThat(response.statusCode())
        .as("the platform cannot judge a request it cannot read (%s)", description)
        .isEqualTo(400);
    assertThat(response.jsonPath().getString("code")).isEqualTo(expectedCode);
  }

  @Test(groups = {"negative", "regression"})
  public void anUnknownTermIsNotFoundOnTheLedgerToo() {
    Response response = billing.ledgerRaw("TERM-DOES-NOT-EXIST");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.jsonPath().getString("code")).isEqualTo("NOT_FOUND");
  }
}
