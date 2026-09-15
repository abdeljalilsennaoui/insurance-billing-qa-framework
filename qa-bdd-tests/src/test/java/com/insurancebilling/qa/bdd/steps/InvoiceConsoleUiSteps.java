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

  /**
   * Opens the console in a named language.
   *
   * <p>The feature file says "English" and "French" rather than "en" and "fr" because a feature file is
   * written in the language of the business, not of the implementation. Mapping one to the other is this
   * layer's job.
   */
  @Given("the billing console is open in {string}")
  public void theBillingConsoleIsOpenIn(String language) {
    listPage = new InvoiceListPage().open().switchLanguageTo(codeFor(language));
  }

  @When("I switch the console to {string}")
  public void iSwitchTheConsoleTo(String language) {
    detailsPage = detailsPage.switchLanguageTo(codeFor(language));
  }

  @Then("the balance is written as {string}")
  public void theBalanceIsWrittenAs(String expected) {
    assertThat(detailsPage.displayedOutstandingBalance())
        .as("the balance as the reader's language writes it")
        .isEqualTo(expected);
  }

  private static String codeFor(String language) {
    return switch (language) {
      case "English" -> "en";
      case "French" -> "fr";
      default -> throw new IllegalArgumentException(
          "The console is not published in " + language);
    };
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

  /**
   * Asserts that the payment was refused, without naming the words used to refuse it.
   *
   * <p>Deliberately not asserting the sentence: this step runs in both languages, and the sentence is
   * different in each. What is the same in both is that the payment did not go through and the reader
   * was told why. {@code BilingualConsoleUiIT} covers the wording itself.
   */
  @Then("the console refuses the payment with an explanation")
  public void theConsoleRefusesThePaymentWithAnExplanation() {
    assertThat(detailsPage.hasErrorBanner())
        .as("expected a refusal to be explained, but the console showed: %s", currentBannerText())
        .isTrue();
    assertThat(detailsPage.errorMessage()).as("a refusal with no explanation is not an explanation").isNotBlank();
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
