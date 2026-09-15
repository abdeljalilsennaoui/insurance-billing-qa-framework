package com.insurancebilling.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.bdd.support.ScenarioContext;
import com.insurancebilling.qa.ui.pages.AgentConsolePage;
import com.insurancebilling.qa.ui.pages.TermsPage;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.List;

/**
 * Glue for the term screens, on both consoles.
 *
 * <p>Delegates entirely to the page objects in {@code qa-ui-tests}: no locator and no {@code WebDriver}
 * reference appears in this module, so markup changes are picked up by the Selenium suite and this one
 * from the same edit.
 *
 * <p>The scenarios that compare the two consoles keep the first screen's figures in the shared context
 * and then open the second. Reading both at once is not possible - there is one browser - and holding
 * the first reading is what lets a scenario assert the two agree rather than merely assert each one
 * separately and hope.
 */
public class BillingConsoleUiSteps {

  private final ScenarioContext context;
  private TermsPage policyholderPage;
  private AgentConsolePage agentPage;

  public BillingConsoleUiSteps(ScenarioContext context) {
    this.context = context;
  }

  // ------------------------------------------------------------ opening up

  @When("the policyholder opens their payment schedule")
  public void thePolicyholderOpensTheirSchedule() {
    policyholderPage = new TermsPage().open(context.account().accountReference(), "schedule");
    context.setAmountsOnFirstScreen(policyholderPage.amountsDue());
  }

  @When("the policyholder opens their transaction history")
  public void thePolicyholderOpensTheirTransactionHistory() {
    policyholderPage = new TermsPage().open(context.account().accountReference(), "transactions");
    context.setAmountsOnFirstScreen(policyholderPage.ledgerBalances());
  }

  @When("the agent opens the console")
  public void theAgentOpensTheConsole() {
    agentPage = new AgentConsolePage().open();
  }

  @When("the agent opens the console in {string}")
  public void theAgentOpensTheConsoleIn(String language) {
    agentPage = new AgentConsolePage().open().switchLanguageTo(codeFor(language));
  }

  @When("the agent opens the same term's payment schedule")
  public void theAgentOpensTheSameTermsSchedule() {
    agentPage = new AgentConsolePage().open(context.term().termReference(), "schedule");
  }

  @When("the agent opens the same term's transaction history")
  public void theAgentOpensTheSameTermsTransactionHistory() {
    agentPage = new AgentConsolePage().open(context.term().termReference(), "transactions");
  }

  @When("the agent switches the console to {string}")
  public void theAgentSwitchesTheConsoleTo(String language) {
    agentPage = agentPage.switchLanguageTo(codeFor(language));
  }

  // ------------------------------------------------- the policyholder's view

  @Then("{word} installments are listed")
  public void installmentsAreListed(String count) {
    assertThat(policyholderPage.installmentCount()).isEqualTo(countOf(count));
  }

  @Then("the schedule on screen adds up to {word}")
  public void theScheduleOnScreenAddsUpTo(String amount) {
    List<String> amounts =
        agentPage == null ? policyholderPage.amountsDue() : agentPage.amountsDue();

    assertThat(sum(amounts))
        .as("a schedule that does not add up to the term is wrong in front of the customer")
        .isEqualByComparingTo(amount);
  }

  @Then("the returned payment and its fee are both shown")
  public void theReturnedPaymentAndItsFeeAreBothShown() {
    assertThat(policyholderPage.ledgerTypes())
        .as("a policyholder who cannot see the fee cannot query it")
        .contains("PAYMENT_RETURNED", "NSF_FEE");
  }

  @Then("the balance on screen is {word}")
  public void theBalanceOnScreenIs(String amount) {
    assertThat(policyholderPage.ledgerBalanceAfterType("NSF_FEE")).isEqualTo(amount);
  }

  // -------------------------------------------------------- the agent's view

  @Then("the term is listed on the portfolio with a balance of {word}")
  public void theTermIsListedOnThePortfolio(String amount) {
    String termReference = context.term().termReference();

    assertThat(agentPage.termReferencesOnGrid()).contains(termReference);
    assertThat(agentPage.balanceOn(termReference)).isEqualTo(amount);
  }

  @Then("the portfolio total is the sum of the balances shown")
  public void thePortfolioTotalIsTheSumOfTheBalancesShown() {
    assertThat(new BigDecimal(agentPage.total()))
        .as("a total that disagrees with its own column is the error a reader catches by hand")
        .isEqualByComparingTo(sum(agentPage.balancesOnGrid()));
  }

  @Then("the product is written as {string}")
  public void theProductIsWrittenAs(String expected) {
    assertThat(agentPage.productLabelOn(context.term().termReference()))
        .as("the product as the reader's language writes it")
        .isEqualTo(expected);
  }

  @Then("the agent is still reading the same term's schedule")
  public void theAgentIsStillReadingTheSameTerm() {
    assertThat(agentPage.termReference())
        .as("switching language must not send the agent back to the first term")
        .isEqualTo(context.term().termReference());
    assertThat(agentPage.showsSchedule()).as("nor back to the summary tab").isTrue();
  }

  // -------------------------------------------- the two screens against each other

  @Then("both screens show the same installment amounts")
  public void bothScreensShowTheSameInstallmentAmounts() {
    assertThat(agentPage.amountsDue())
        .as("an agent quoting a figure the customer cannot see on their own screen is the failure")
        .isEqualTo(context.amountsOnFirstScreen())
        .isNotEmpty();
  }

  @Then("both screens show the same running balances")
  public void bothScreensShowTheSameRunningBalances() {
    assertThat(agentPage.ledgerBalances())
        .as("the ledger is one ledger; two readings of it that differ mean one of them is wrong")
        .isEqualTo(context.amountsOnFirstScreen())
        .isNotEmpty();
  }

  // ---------------------------------------------------------------- helpers

  private static BigDecimal sum(List<String> amounts) {
    return amounts.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static int countOf(String word) {
    return switch (word) {
      case "eleven" -> 11;
      case "twelve" -> 12;
      default -> throw new IllegalArgumentException("No number is known for '" + word + "'");
    };
  }

  /**
   * The feature file says "English" and "French" rather than "en" and "fr" because a feature file is
   * written in the language of the business, not of the implementation.
   */
  private static String codeFor(String language) {
    return switch (language) {
      case "English" -> "en";
      case "French" -> "fr";
      default -> throw new IllegalArgumentException("The console is not published in " + language);
    };
  }
}
