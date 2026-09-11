package com.insurancebilling.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.client.InvoiceApiClient;
import com.insurancebilling.qa.api.data.BillingTestData;
import com.insurancebilling.qa.api.model.ApiError;
import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.api.model.PaymentRequestBody;
import com.insurancebilling.qa.bdd.support.ScenarioContext;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import java.util.List;

/**
 * Glue for the API scenarios.
 *
 * <p>This class contains no automation logic of its own: the clients and fixtures come from
 * {@code qa-api-tests}, which is a plain module dependency. Reimplementing HTTP calls here would mean
 * the BDD layer and the API suite could drift apart and disagree about the same endpoint, and a
 * contract change would have to be made twice.
 *
 * <p>The context object is constructor-injected by Cucumber and recreated per scenario, so nothing
 * leaks between scenarios.
 */
public class InvoicePaymentApiSteps {

  private final InvoiceApiClient invoices = new InvoiceApiClient();
  private final BillingTestData testData = new BillingTestData();
  private final ScenarioContext context;

  public InvoicePaymentApiSteps(ScenarioContext context) {
    this.context = context;
  }

  @Given("an unpaid invoice for {word}")
  public void anUnpaidInvoiceFor(String amount) {
    context.setInvoice(testData.unpaidInvoice(amount));
  }

  @Given("a fully settled invoice for {word}")
  public void aFullySettledInvoiceFor(String amount) {
    context.setInvoice(testData.settledInvoice(amount));
  }

  @Given("a cancelled invoice for {word}")
  public void aCancelledInvoiceFor(String amount) {
    context.setInvoice(testData.cancelledInvoice(amount));
  }

  @Given("an invoice for {word} on a {word} policy")
  public void anInvoiceOnAPolicyInState(String amount, String policyState) {
    context.setInvoice(testData.invoiceOnInactivePolicy(amount, policyState));
  }

  @Given("an invoice for {word} that is past its due date")
  public void anOverdueInvoice(String amount) {
    context.setInvoice(testData.overdueInvoice(amount));
  }

  @Given("{word} has already been paid")
  public void hasAlreadyBeenPaid(String amount) {
    invoices.pay(context.invoice().id(), amount);
    refresh();
  }

  @When("a payment of {word} is submitted")
  public void aPaymentIsSubmitted(String amount) {
    Response response = invoices.payRaw(context.invoice().id(), PaymentRequestBody.of(amount));
    context.setLastStatusCode(response.statusCode());
    context.setLastErrorCode(
        response.statusCode() >= 400 ? response.as(ApiError.class).code() : null);
    refresh();
  }

  @When("payments of {word}, {word} and {word} are submitted")
  public void threePaymentsAreSubmitted(String first, String second, String third) {
    for (String amount : List.of(first, second, third)) {
      aPaymentIsSubmitted(amount);
    }
  }

  @Then("the payment is accepted")
  public void thePaymentIsAccepted() {
    assertThat(context.lastStatusCode())
        .as("expected the payment to be accepted, but the API refused it with %s", context.lastErrorCode())
        .isEqualTo(201);
  }

  @Then("the payment is refused because {string}")
  public void thePaymentIsRefusedBecause(String expectedReason) {
    assertThat(context.lastStatusCode())
        .as("a well-formed request refused by a billing rule should be 422")
        .isEqualTo(422);
    assertThat(context.lastErrorCode()).isEqualTo(expectedReason);
  }

  @Then("the invoice shows {word} paid and {word} outstanding")
  public void theInvoiceShowsPaidAndOutstanding(String paid, String outstanding) {
    InvoiceDto invoice = context.invoice();
    assertThat(invoice.amountPaid()).isEqualByComparingTo(paid);
    assertThat(invoice.outstandingBalance()).isEqualByComparingTo(outstanding);
  }

  @Then("the invoice still shows {word} outstanding")
  public void theInvoiceStillShowsOutstanding(String outstanding) {
    assertThat(context.invoice().outstandingBalance()).isEqualByComparingTo(outstanding);
  }

  @Then("the invoice is {string}")
  public void theInvoiceIs(String status) {
    assertThat(context.invoice().status()).isEqualTo(status);
  }

  @Then("the invoice is flagged overdue")
  public void theInvoiceIsFlaggedOverdue() {
    assertThat(context.invoice().overdue()).isTrue();
  }

  @Then("the invoice is not flagged overdue")
  public void theInvoiceIsNotFlaggedOverdue() {
    assertThat(context.invoice().overdue()).isFalse();
  }

  /** Re-reads the invoice so later assertions see server state rather than a stale local copy. */
  private void refresh() {
    context.setInvoice(invoices.get(context.invoice().id()));
  }
}
