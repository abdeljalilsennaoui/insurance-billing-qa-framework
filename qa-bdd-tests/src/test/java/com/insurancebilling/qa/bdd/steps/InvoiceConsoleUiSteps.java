package com.insurancebilling.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.bdd.support.ScenarioContext;
import com.insurancebilling.qa.ui.pages.InvoiceDetailsPage;
import com.insurancebilling.qa.ui.pages.InvoiceListPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Glue for the browser scenarios.
 *
 * <p>Delegates entirely to the page objects in {@code qa-ui-tests}. No locator and no {@code WebDriver}
 * reference appears in this module: if the console's markup changes, the page object is the single place
 * that needs editing, and the Selenium suite and the BDD suite both pick the change up.
 *
 * <p>Invoice fixtures come from the API steps through the shared scenario context, which is why a
 * {@code Given} written for the API feature can be reused verbatim in a UI scenario.
 */
public class InvoiceConsoleUiSteps {

  private final ScenarioContext context;
  private InvoiceDetailsPage detailsPage;
  private InvoiceListPage listPage;

  public InvoiceConsoleUiSteps(ScenarioContext context) {
    this.context = context;
  }

  @Given("the billing console is open")
  public void theBillingConsoleIsOpen() {
    listPage = new InvoiceListPage().open();
  }

  @When("I open that invoice in the console")
  public void iOpenThatInvoiceInTheConsole() {
    detailsPage = new InvoiceDetailsPage().openById(context.invoice().id());
  }

  @When("I record a payment of {word}")
  public void iRecordAPaymentOf(String amount) {
    detailsPage.payWith(amount);
  }

  @When("I look for that invoice in the list")
  public void iLookForThatInvoiceInTheList() {
    listPage = new InvoiceListPage().open();
  }

  @Then("the console confirms the payment")
  public void theConsoleConfirmsThePayment() {
    assertThat(detailsPage.hasSuccessBanner())
        .as("expected a confirmation banner, but the console showed: %s", currentBannerText())
        .isTrue();
  }

  @Then("the console shows {word} paid and {word} outstanding")
  public void theConsoleShowsPaidAndOutstanding(String paid, String outstanding) {
    assertThat(detailsPage.amountPaid()).isEqualTo(paid);
    assertThat(detailsPage.outstandingBalance()).isEqualTo(outstanding);
  }

  @Then("the console shows the invoice as {string}")
  public void theConsoleShowsTheInvoiceAs(String status) {
    assertThat(detailsPage.status()).isEqualTo(status);
  }

  @Then("the console says the invoice is settled in full")
  public void theConsoleSaysTheInvoiceIsSettled() {
    assertThat(detailsPage.showsFullyPaidMessage()).isTrue();
  }

  @Then("the console says the invoice is cancelled")
  public void theConsoleSaysTheInvoiceIsCancelled() {
    assertThat(detailsPage.showsCancelledMessage()).isTrue();
  }

  @Then("the console shows an error containing {string}")
  public void theConsoleShowsAnErrorContaining(String fragment) {
    assertThat(detailsPage.hasErrorBanner())
        .as("expected an error banner on the page")
        .isTrue();
    assertThat(detailsPage.errorMessage()).contains(fragment);
  }

  @Then("the list shows it as {string} with {word} outstanding")
  public void theListShowsItAsWithOutstanding(String status, String outstanding) {
    String invoiceNumber = context.invoice().invoiceNumber();
    assertThat(listPage.hasInvoice(invoiceNumber))
        .as("invoice %s should appear in the list", invoiceNumber)
        .isTrue();
    assertThat(listPage.statusOf(invoiceNumber)).isEqualTo(status);
    assertThat(listPage.outstandingBalanceOf(invoiceNumber)).isEqualTo(outstanding);
  }

  /** Used only to make a failure message say what the page actually showed. */
  private String currentBannerText() {
    if (detailsPage.hasErrorBanner()) {
      return "error: " + detailsPage.errorMessage();
    }
    return "no banner at all";
  }
}
