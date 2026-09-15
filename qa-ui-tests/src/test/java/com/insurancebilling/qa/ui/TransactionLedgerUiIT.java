package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import com.insurancebilling.qa.ui.pages.TermsPage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.testng.annotations.Test;

/**
 * The term screen's transaction history in a browser.
 *
 * <p>The ledger is shown newest first, which is how it is read, while its running balance accumulates
 * from the oldest line. Checking that the two agree on screen is the whole point: it is the one place a
 * policyholder can audit the platform's arithmetic themselves.
 */
public class TransactionLedgerUiIT extends BaseUiTest {

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void bindingATermPostsOneLineToTheLedger() {
    BillingAccountDto account = testData.account();
    testData.evenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "transactions");

    assertThat(page.showsLedger()).isTrue();
    assertThat(page.ledgerRowCount()).isEqualTo(1);
    assertThat(page.ledgerTypes()).containsExactly("NEW_BUSINESS");
    assertThat(page.ledgerBalanceAfterType("NEW_BUSINESS")).isEqualTo("1591.60");
  }

  @Test(groups = "ui-regression")
  public void theLedgerIsShownNewestFirst() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    billing.pay(term.termReference(), "130.80");

    TermsPage page = new TermsPage().open(account.accountReference(), "transactions");

    assertThat(page.ledgerTypes())
        .as("the most recent thing that happened is the thing a reader looks for first")
        .containsExactly("PAYMENT", "NEW_BUSINESS");
  }

  @Test(groups = "ui-regression")
  public void theRunningBalanceOnScreenIsTheSumOfTheLinesBelowIt() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    billing.pay(term.termReference(), "130.80");
    billing.pay(term.termReference(), "132.80");

    TermsPage page = new TermsPage().open(account.accountReference(), "transactions");

    List<String> amountsNewestFirst = page.ledgerAmounts();
    List<String> balancesNewestFirst = page.ledgerBalances();
    List<String> amountsOldestFirst = new ArrayList<>(amountsNewestFirst);
    List<String> balancesOldestFirst = new ArrayList<>(balancesNewestFirst);
    Collections.reverse(amountsOldestFirst);
    Collections.reverse(balancesOldestFirst);

    BigDecimal accumulated = BigDecimal.ZERO;
    for (int line = 0; line < amountsOldestFirst.size(); line++) {
      accumulated = accumulated.add(new BigDecimal(amountsOldestFirst.get(line)));
      assertThat(new BigDecimal(balancesOldestFirst.get(line)))
          .as("the balance shown on line %d does not follow from the lines above it", line + 1)
          .isEqualByComparingTo(accumulated);
    }
  }

  @Test(groups = "ui-regression")
  public void aPaymentIsSplitAcrossTheColumnsItSettled() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    billing.pay(term.termReference(), "130.80");

    TermsPage page = new TermsPage().open(account.accountReference(), "transactions");

    assertThat(page.ledgerAmountOfType("PAYMENT")).isEqualTo("-130.80");
    assertThat(page.ledgerBalanceAfterType("PAYMENT")).isEqualTo("1460.80");
  }

  @Test(groups = "ui-regression")
  public void aReturnedPaymentAndItsFeeBothAppearOnTheLedger() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    String payment = billing.pay(term.termReference(), "130.80").reference();
    billing.returnPayment(payment, "INSUFFICIENT_FUNDS");

    TermsPage page = new TermsPage().open(account.accountReference(), "transactions");

    assertThat(page.ledgerTypes())
        .containsExactly("NSF_FEE", "PAYMENT_RETURNED", "PAYMENT", "NEW_BUSINESS");
    assertThat(page.ledgerAmountOfType("PAYMENT_RETURNED"))
        .as("the reversal is the exact opposite of what it reverses")
        .isEqualTo("130.80");
    assertThat(page.ledgerAmountOfType("NSF_FEE")).isEqualTo("25.00");
    assertThat(page.ledgerBalanceAfterType("NSF_FEE")).isEqualTo("1616.60");
  }

  @Test(groups = "ui-regression")
  public void theTabsOpenTheirOwnPanelAndHaveAUrlEach() {
    BillingAccountDto account = testData.account();
    testData.evenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference());
    assertThat(page.showsSummary()).as("the summary is where a term opens").isTrue();

    page.openTab("transactions");
    assertThat(page.showsLedger()).isTrue();
    assertThat(page.showsSummary()).isFalse();
    assertThat(page.currentUrl()).contains("tab=transactions");

    page.openTab("schedule");
    assertThat(page.showsSchedule()).isTrue();
    assertThat(page.currentUrl()).contains("tab=schedule");
  }

  @Test(groups = "ui-regression")
  public void theTermHeaderNamesTheTermsDatesAndState() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference());

    assertThat(page.termReference()).isEqualTo(term.termReference());
    assertThat(page.status()).isEqualTo("IN_FORCE");
    assertThat(page.billingType()).isEqualTo("DIRECT_BILL");
    assertThat(page.effectiveDate()).isEqualTo(term.effectiveDate().toString());
    assertThat(page.expiryDate()).isEqualTo(term.expiryDate().toString());
  }

  @Test(groups = "ui-regression")
  public void theTermSummaryBreaksTheBalanceIntoWhatMakesItUp() {
    BillingAccountDto account = testData.account();
    testData.evenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "summary");

    assertThat(page.termPremium()).isEqualTo("1440.00");
    assertThat(page.termTax()).isEqualTo("129.60");
    assertThat(page.termFees()).as("eleven installment fees of 2.00").isEqualTo("22.00");
    assertThat(page.scheduledTotal()).isEqualTo("1591.60");
    assertThat(page.balance()).isEqualTo("1591.60");
  }
}
