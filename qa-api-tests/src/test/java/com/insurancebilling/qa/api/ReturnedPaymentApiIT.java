package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.BillingTransactionDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import io.restassured.response.Response;
import org.testng.annotations.Test;

/**
 * What the platform does when a bank refuses a payment it has already recorded.
 *
 * <p>Three things move at once — the ledger, the schedule and the account's counters — so every scenario
 * here checks all three rather than whichever one is convenient.
 */
public class ReturnedPaymentApiIT extends BaseApiTest {

  @Test(groups = {"smoke", "regression"})
  public void aReturnedPaymentRestoresTheBalanceAndChargesAFee() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.unevenTerm(account.accountReference());
    BillingTransactionDto payment = billing.pay(term.termReference(), "90.87");
    assertThat(payment.balanceAfter()).isEqualByComparingTo("1021.13");

    BillingTransactionDto reversal =
        billing.returnPayment(payment.reference(), "INSUFFICIENT_FUNDS");

    assertThat(reversal.type()).isEqualTo("PAYMENT_RETURNED");
    assertThat(reversal.balanceAfter())
        .as("reversing an uneven payment must put the balance back exactly where it was")
        .isEqualByComparingTo("1112.00");
    assertThat(billing.term(term.termReference()).balance())
        .as("1112.00 restored plus the 25.00 fee")
        .isEqualByComparingTo("1137.00");
  }

  @Test(groups = "regression")
  public void theReversalNegatesTheOriginalPaymentColumnForColumn() {
    PolicyTermDto term = testData.boundTerm();
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");

    BillingTransactionDto reversal =
        billing.returnPayment(payment.reference(), "INSUFFICIENT_FUNDS");

    assertThat(reversal.premiumAmount()).isEqualByComparingTo(payment.premiumAmount().negate());
    assertThat(reversal.taxAmount()).isEqualByComparingTo(payment.taxAmount().negate());
    assertThat(reversal.feeAmount()).isEqualByComparingTo(payment.feeAmount().negate());
  }

  @Test(groups = "regression")
  public void theInstallmentThePaymentSettledIsMarkedReversedNotUnpaid() {
    PolicyTermDto term = testData.boundTerm();
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");
    assertThat(billing.term(term.termReference()).installmentsRemaining()).isEqualTo(11);

    billing.returnPayment(payment.reference(), "INSUFFICIENT_FUNDS");

    assertThat(billing.schedule(term.termReference()).get(0).status())
        .as("an installment that bounced is not the same as one that was never paid")
        .isEqualTo("REVERSED");
    assertThat(billing.term(term.termReference()).installmentsRemaining()).isEqualTo(12);
  }

  @Test(groups = "regression")
  public void theNsfFeeIsPostedAsAFeeAndNotAsPremium() {
    PolicyTermDto term = testData.boundTerm();
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");

    billing.returnPayment(payment.reference(), "INSUFFICIENT_FUNDS");

    assertThat(billing.ledger(term.termReference()))
        .filteredOn(line -> "NSF_FEE".equals(line.type()))
        .singleElement()
        .satisfies(
            fee -> {
              assertThat(fee.feeAmount()).isEqualByComparingTo("25.00");
              assertThat(fee.premiumAmount())
                  .as("a fee is not premium and must never reach the premium column")
                  .isEqualByComparingTo("0.00");
              assertThat(fee.taxAmount()).isEqualByComparingTo("0.00");
            });
  }

  @Test(
      groups = "regression",
      dataProvider = "returnReasonsAndTheirNsfEffect",
      dataProviderClass = BillingDataProviders.class)
  public void onlyAFundingFailureCountsAgainstTheNsfTally(
      String reason, int expectedNsfCount, String description) {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");

    billing.returnPayment(payment.reference(), reason);

    BillingAccountDto after = billing.account(account.accountReference());
    assertThat(after.returnedPaymentCount())
        .as("every refused payment is a returned payment")
        .isEqualTo(1);
    assertThat(after.nsfCount()).as(description).isEqualTo(expectedNsfCount);
  }

  @Test(groups = "regression")
  public void aReturnThatIsNotThePolicyholdersDoingCarriesNoFee() {
    PolicyTermDto term = testData.boundTerm();
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");

    billing.returnPayment(payment.reference(), "ACCOUNT_CLOSED");

    assertThat(billing.ledger(term.termReference()))
        .as("an account the bank closed is not an account the policyholder failed to fund")
        .noneSatisfy(line -> assertThat(line.type()).isEqualTo("NSF_FEE"));
    assertThat(billing.term(term.termReference()).balance()).isEqualByComparingTo("1591.60");
  }

  @Test(groups = {"negative", "regression"})
  public void theSamePaymentCannotBeReturnedTwice() {
    PolicyTermDto term = testData.boundTerm();
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");
    billing.returnPayment(payment.reference(), "INSUFFICIENT_FUNDS");

    Response second = billing.returnPaymentRaw(payment.reference(), "INSUFFICIENT_FUNDS");

    assertThat(second.statusCode()).isEqualTo(422);
    assertThat(second.jsonPath().getString("code")).isEqualTo("PAYMENT_ALREADY_RETURNED");
  }

  @Test(groups = {"negative", "regression"})
  public void asecondReturnAttemptLeavesTheCountersWhereTheFirstLeftThem() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");
    billing.returnPayment(payment.reference(), "INSUFFICIENT_FUNDS");

    billing.returnPaymentRaw(payment.reference(), "INSUFFICIENT_FUNDS");

    BillingAccountDto after = billing.account(account.accountReference());
    assertThat(after.nsfCount()).isEqualTo(1);
    assertThat(after.returnedPaymentCount()).isEqualTo(1);
  }

  @Test(groups = {"negative", "regression"})
  public void aLineThatIsNotAPaymentCannotBeReturned() {
    PolicyTermDto term = testData.boundTerm();
    String newBusiness = billing.ledger(term.termReference()).get(0).reference();

    Response response = billing.returnPaymentRaw(newBusiness, "INSUFFICIENT_FUNDS");

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.jsonPath().getString("code")).isEqualTo("PAYMENT_ALREADY_RETURNED");
  }

  @Test(groups = {"negative", "regression"})
  public void returningALineThatDoesNotExistIsNotFound() {
    Response response = billing.returnPaymentRaw("TXN-DOES-NOT-EXIST", "INSUFFICIENT_FUNDS");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.jsonPath().getString("code")).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void aReasonThePlatformHasNoMeaningForIsAMalformedRequest() {
    PolicyTermDto term = testData.boundTerm();
    BillingTransactionDto payment = billing.pay(term.termReference(), "130.80");

    Response response = billing.returnPaymentRaw(payment.reference(), "BECAUSE_I_SAID_SO");

    assertThat(response.statusCode())
        .as("a value this platform has no meaning for is the caller's shape being wrong, not a refusal")
        .isEqualTo(400);
    assertThat(response.jsonPath().getString("code")).isEqualTo("MALFORMED_REQUEST");
  }
}
