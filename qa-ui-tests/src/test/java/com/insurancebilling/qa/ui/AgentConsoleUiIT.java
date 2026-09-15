package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import com.insurancebilling.qa.ui.pages.AgentConsolePage;
import com.insurancebilling.qa.ui.pages.TermsPage;
import java.math.BigDecimal;
import java.util.List;
import org.testng.annotations.Test;

/**
 * The agent console in a browser.
 *
 * <p>Every assertion here finds its own term by reference. The grid shows the whole book, so it also
 * shows the terms every other test in the suite created while this one was running — an assertion
 * about how many rows there are, or about which row is third, would fail for reasons that have nothing
 * to do with the agent console.
 *
 * <p>The comparison against the policyholder's screen is the test worth having. Both consoles render
 * the same Thymeleaf fragment, so it should be impossible for them to disagree; this is what proves
 * the "should" — and it is the assertion that would catch someone copying the panels back out into a
 * second template.
 */
public class AgentConsoleUiIT extends BaseUiTest {

  private static BigDecimal sum(List<String> amounts) {
    return amounts.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void theGridShowsATermTheMomentItIsBound() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());

    AgentConsolePage page = new AgentConsolePage().open();

    assertThat(page.termReferencesOnGrid()).contains(term.termReference());
    assertThat(page.accountOn(term.termReference())).isEqualTo(account.accountReference());
    assertThat(page.productOn(term.termReference())).isEqualTo("AUTO");
    assertThat(page.statusOn(term.termReference())).isEqualTo("IN_FORCE");
    assertThat(page.balanceOn(term.termReference()))
        .as("the whole term is posted at new business, so nothing paid means the whole term is owed")
        .isEqualTo("1591.60");
  }

  @Test(groups = "ui-regression")
  public void theTotalAddsUpTheColumnAboveIt() {
    testData.evenTerm(testData.account().accountReference());

    AgentConsolePage page = new AgentConsolePage().open();

    assertThat(new BigDecimal(page.total()))
        .as("a total that disagrees with its own column is the error a reader will catch by hand")
        .isEqualByComparingTo(sum(page.balancesOnGrid()));
  }

  @Test(groups = "ui-regression")
  public void theGridIsOrderedByPolicyNumber() {
    testData.evenTerm(testData.account().accountReference());

    AgentConsolePage page = new AgentConsolePage().open();

    assertThat(page.policyNumbersOnGrid())
        .as("insertion order is an accident of the data; a reader scans down for a policy number")
        .isSorted();
  }

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void clickingAPolicyOpensThatTermsFigures() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());

    AgentConsolePage page = new AgentConsolePage().open().selectTerm(term.termReference());

    assertThat(page.termReference()).isEqualTo(term.termReference());
    assertThat(page.showsSummary()).isTrue();
    assertThat(page.balance()).isEqualTo("1591.60");
  }

  @Test(groups = "ui-regression")
  public void theSelectedRowStaysMarkedWhileItsPanelsAreRead() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());

    AgentConsolePage page = new AgentConsolePage().open().selectTerm(term.termReference());
    assertThat(page.selectedTermOnGrid()).isEqualTo(term.termReference());

    page.openTab("schedule");

    assertThat(page.selectedTermOnGrid())
        .as("the panels belong to one row; changing tab must not lose which")
        .isEqualTo(term.termReference());
    assertThat(page.showsSchedule()).isTrue();
  }

  @Test(groups = "ui-regression")
  public void everyTabOpensFromTheAgentConsoleWithoutLeavingIt() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());

    AgentConsolePage page = new AgentConsolePage().open(term.termReference(), "summary");

    page.openTab("transactions");
    assertThat(page.showsLedger()).isTrue();
    assertThat(page.currentUrl()).contains("/agent");

    page.openTab("schedule");
    assertThat(page.showsSchedule()).isTrue();
    assertThat(page.currentUrl()).contains("/agent");
  }

  @Test(groups = "ui-regression")
  public void theAgentAndThePolicyholderReadTheSameSchedule() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.unevenTerm(account.accountReference());

    AgentConsolePage agent = new AgentConsolePage().open(term.termReference(), "schedule");
    List<String> asTheAgentSeesThem = agent.amountsDue();

    TermsPage policyholder = new TermsPage().open(account.accountReference(), "schedule");
    List<String> asTheCustomerSeesThem = policyholder.amountsDue();

    assertThat(asTheAgentSeesThem)
        .as("an agent quoting a figure the customer cannot see on their own screen is the failure")
        .isEqualTo(asTheCustomerSeesThem)
        .isNotEmpty();
  }

  @Test(groups = "ui-regression")
  public void aReturnedPaymentIsVisibleToTheAgentAsItIsToTheCustomer() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    billing.returnPayment(
        billing.pay(term.termReference(), "130.80").reference(), "INSUFFICIENT_FUNDS");

    AgentConsolePage page = new AgentConsolePage().open(term.termReference(), "transactions");

    assertThat(page.ledgerTypes())
        .as("the reversal and the fee are both postings; neither may be silently netted off")
        .contains("PAYMENT_RETURNED", "NSF_FEE");
    assertThat(page.ledgerAmountOfType("NSF_FEE")).isEqualTo("25.00");
  }

  @Test(groups = "ui-regression")
  public void theConsoleReadsInFrenchWithoutLosingTheTermOrTheTab() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());

    AgentConsolePage page =
        new AgentConsolePage().open(term.termReference(), "schedule").switchLanguageTo("fr");

    assertThat(page.termReference())
        .as("switching language must not send the agent back to the first term")
        .isEqualTo(term.termReference());
    assertThat(page.showsSchedule())
        .as("nor back to the summary tab")
        .isTrue();
    assertThat(page.productOn(term.termReference()))
        .as("the attribute is the same in both languages; only the words change")
        .isEqualTo("AUTO");
    assertThat(page.amountDueOn(1))
        .as("the figures are the same figures, whatever they are written in")
        .isEqualTo("130.80");
  }
}
