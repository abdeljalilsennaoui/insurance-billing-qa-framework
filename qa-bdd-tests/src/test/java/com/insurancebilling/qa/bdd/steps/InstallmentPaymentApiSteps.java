package com.insurancebilling.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.client.BillingApiClient;
import com.insurancebilling.qa.api.data.BillingTestData;
import com.insurancebilling.qa.api.model.ApiError;
import com.insurancebilling.qa.api.model.BillingTransactionDto;
import com.insurancebilling.qa.api.model.InstallmentDto;
import com.insurancebilling.qa.bdd.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Glue for the installment and returned-payment scenarios.
 *
 * <p>The fixtures and clients come from {@code qa-api-tests}, as the invoice glue's javadoc explains:
 * reimplementing them here would let the two suites drift apart and disagree about the same endpoint.
 *
 * <p>The {@code Given} steps in this class are shared with the browser feature. A scenario about what
 * is on screen still needs a term to exist first, and building one through the UI would make every
 * console test depend on the correctness of the console it is testing.
 */
public class InstallmentPaymentApiSteps {

  /**
   * Counts written as words, because a scenario reads as English and "12 installments are
   * outstanding" does not.
   */
  private static final Map<String, Integer> COUNTS =
      Map.of("one", 1, "two", 2, "eleven", 11, "twelve", 12);

  private final BillingApiClient billing = new BillingApiClient();
  private final BillingTestData testData = new BillingTestData();
  private final ScenarioContext context;

  public InstallmentPaymentApiSteps(ScenarioContext context) {
    this.context = context;
  }

  // ----------------------------------------------------------------- givens

  @Given("a term of 1591.60 payable over twelve installments")
  public void aTermThatDividesEvenly() {
    context.setAccount(testData.account());
    context.setTerm(testData.evenTerm(context.account().accountReference()));
  }

  @Given("a term of 1112.00 that does not divide evenly")
  public void aTermThatDoesNotDivideEvenly() {
    context.setAccount(testData.account());
    context.setTerm(testData.unevenTerm(context.account().accountReference()));
  }

  @Given("a payment of {word} has been made against the term")
  public void aPaymentHasBeenMade(String amount) {
    aPaymentIsMade(amount);
  }

  @Given("the bank has returned that payment for {string}")
  public void theBankHasReturnedThatPayment(String reason) {
    billing.returnPayment(context.lastPayment().reference(), reason);
  }

  // ------------------------------------------------------------------ whens

  @When("a payment of {word} is made against the term")
  public void aPaymentIsMade(String amount) {
    context.setLastPayment(billing.pay(context.term().termReference(), amount));
  }

  @When("the bank returns that payment for {string}")
  public void theBankReturnsThatPayment(String reason) {
    billing.returnPayment(context.lastPayment().reference(), reason);
  }

  @When("the bank returns that payment again")
  public void theBankReturnsThatPaymentAgain() {
    Response response =
        billing.returnPaymentRaw(context.lastPayment().reference(), "INSUFFICIENT_FUNDS");
    context.setLastStatusCode(response.statusCode());
    context.setLastErrorCode(response.as(ApiError.class).code());
  }

  // ------------------------------------------------------------------ thens

  @Then("the term balance is {word}")
  public void theTermBalanceIs(String amount) {
    assertThat(term().balance()).isEqualByComparingTo(amount);
  }

  @Then("{word} installments are outstanding")
  public void installmentsAreOutstanding(String count) {
    assertThat(term().installmentsRemaining()).isEqualTo(countOf(count));
  }

  @Then("the installments add up to {word} exactly")
  public void theInstallmentsAddUpExactly(String amount) {
    BigDecimal collected =
        schedule().stream().map(InstallmentDto::amountDue).reduce(BigDecimal.ZERO, BigDecimal::add);

    assertThat(collected)
        .as("a schedule that does not collect the term is wrong in front of the customer")
        .isEqualByComparingTo(amount);
  }

  @Then("installment {int} is for {word}")
  public void installmentIsFor(int sequenceNumber, String amount) {
    assertThat(installment(sequenceNumber).amountDue()).isEqualByComparingTo(amount);
  }

  @Then("installment {int} is settled")
  public void installmentIsSettled(int sequenceNumber) {
    assertThat(installment(sequenceNumber).status()).isEqualTo("PAID");
  }

  @Then("installment {int} is still outstanding")
  public void installmentIsStillOutstanding(int sequenceNumber) {
    assertThat(installment(sequenceNumber).status()).isNotEqualTo("PAID");
  }

  @Then("installment {int} is marked as reversed")
  public void installmentIsMarkedAsReversed(int sequenceNumber) {
    assertThat(installment(sequenceNumber).status())
        .as("an installment that bounced is not the same as one that was never paid")
        .isEqualTo("REVERSED");
  }

  @Then("the ledger records a returned payment and a returned payment fee")
  public void theLedgerRecordsBoth() {
    assertThat(ledger().stream().map(BillingTransactionDto::type))
        .as("the reversal and the fee are separate postings; neither may be netted off the other")
        .contains("PAYMENT_RETURNED", "NSF_FEE");
  }

  @Then("the account has been charged {int} returned payment and {int} NSF")
  public void theAccountCountersRead(int returned, int nsf) {
    assertThat(billing.account(context.account().accountReference()).returnedPaymentCount())
        .as("every refused payment is a returned payment")
        .isEqualTo(returned);
    assertThat(billing.account(context.account().accountReference()).nsfCount())
        .as("only a payment refused for want of funds is an NSF")
        .isEqualTo(nsf);
  }

  @Then("the return is refused because {string}")
  public void theReturnIsRefusedBecause(String reason) {
    assertThat(context.lastStatusCode())
        .as("a well-formed request refused by a billing rule is 422, not 400")
        .isEqualTo(422);
    assertThat(context.lastErrorCode()).isEqualTo(reason);
  }

  @Then("each ledger line's balance is the sum of that line and the ones before it")
  public void theRunningBalanceReconciles() {
    // The ledger is served newest first, which is the order it is read in. The arithmetic runs the
    // other way, so the check walks it back to front - deliberately, rather than by reversing the
    // expectation to match whatever the endpoint happened to return.
    BigDecimal running = BigDecimal.ZERO;
    for (BillingTransactionDto line : ledger().reversed()) {
      running = running.add(line.amount());
      assertThat(line.balanceAfter())
          .as("the running balance on %s does not follow from the lines above it", line.reference())
          .isEqualByComparingTo(running);
    }
  }

  // ---------------------------------------------------------------- helpers

  private com.insurancebilling.qa.api.model.PolicyTermDto term() {
    return billing.term(context.term().termReference());
  }

  private List<InstallmentDto> schedule() {
    return billing.schedule(context.term().termReference());
  }

  private List<BillingTransactionDto> ledger() {
    return billing.ledger(context.term().termReference());
  }

  private InstallmentDto installment(int sequenceNumber) {
    return schedule().stream()
        .filter(installment -> installment.sequenceNumber() == sequenceNumber)
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("The schedule has no installment " + sequenceNumber));
  }

  private static int countOf(String word) {
    Integer count = COUNTS.get(word);
    if (count == null) {
      throw new IllegalArgumentException(
          "No number is known for '" + word + "'. Known: " + COUNTS.keySet());
    }
    return count;
  }
}
