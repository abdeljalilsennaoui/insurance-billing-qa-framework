package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import com.insurancebilling.qa.ui.pages.AccountSummaryPage;
import org.testng.annotations.Test;

/**
 * The policyholder's account summary in a browser.
 *
 * <p>Each scenario builds its own account and term through the API, so nothing here depends on the
 * seeded data or on another test's leftovers. The screenshots in {@code docs/} are taken against the
 * seeded accounts instead, which is why those two data sets exist for different reasons.
 */
public class AccountSummaryUiIT extends BaseUiTest {

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void theSummaryShowsWhatIsOwedAndWhenItFallsDue() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());

    AccountSummaryPage page = new AccountSummaryPage().open(account.accountReference());

    assertThat(page.accountReference()).isEqualTo(account.accountReference());
    assertThat(page.totalBalance())
        .as("the screen must agree with the term it is derived from")
        .isEqualTo(term.balance().toPlainString());
    assertThat(page.nextPaymentAmount()).isEqualTo("130.80");
    assertThat(page.nextPaymentDate()).isNotBlank();
  }

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void theBankAccountNumberIsShownMasked() {
    BillingAccountDto account = testData.account();

    AccountSummaryPage page = new AccountSummaryPage().open(account.accountReference());

    assertThat(page.displayedAccountNumber())
        .as("the screen is the last place an unmasked number could appear, so this reads the rendered text")
        .isEqualTo("****742");
    assertThat(page.displayedInstitutionNumber()).matches("\\*+");
    assertThat(page.displayedBranchNumber()).matches("\\*+");
    assertThat(page.bankAccountHolder()).isEqualTo("QA Tester");
  }

  @Test(groups = "ui-regression")
  public void thePageNeverRendersMoreThanThreeDigitsOfAnAccountNumber() {
    BillingAccountDto account = testData.account();

    AccountSummaryPage page = new AccountSummaryPage().open(account.accountReference());

    assertThat(page.displayedAccountNumber())
        .as("four stars then exactly three digits, and nothing else")
        .matches("\\*{4}\\d{3}");
  }

  @Test(groups = "ui-regression")
  public void anAccountWithNothingReturnedShowsBothTalliesAtZero() {
    BillingAccountDto account = testData.account();
    testData.evenTerm(account.accountReference());

    AccountSummaryPage page = new AccountSummaryPage().open(account.accountReference());

    assertThat(page.nsfCount()).isZero();
    assertThat(page.returnedPaymentCount()).isZero();
  }

  @Test(groups = "ui-regression")
  public void aReturnedPaymentIsVisibleOnTheSummary() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    String paymentReference = billing.pay(term.termReference(), "130.80").reference();
    billing.returnPayment(paymentReference, "INSUFFICIENT_FUNDS");

    AccountSummaryPage page = new AccountSummaryPage().open(account.accountReference());

    assertThat(page.nsfCount()).isEqualTo(1);
    assertThat(page.returnedPaymentCount()).isEqualTo(1);
    assertThat(page.totalBalance())
        .as("the balance restored by the reversal, plus the fee")
        .isEqualTo("1616.60");
  }

  @Test(groups = "ui-regression")
  public void moneyNoInstallmentClaimedIsShownAsACredit() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    billing.pay(term.termReference(), "100.00");

    AccountSummaryPage page = new AccountSummaryPage().open(account.accountReference());

    assertThat(page.unappliedAmount())
        .as("100.00 does not cover the 130.80 down payment, so it sits unapplied")
        .isEqualTo("100.00");
  }

  @Test(groups = "ui-regression")
  public void thePaymentPlanAndMethodAreNamedInTheReadersLanguage() {
    BillingAccountDto account = testData.account();

    AccountSummaryPage page = new AccountSummaryPage().open(account.accountReference());
    assertThat(page.paymentPlan()).isEqualTo("MONTHLY");
    assertThat(page.paymentMethod()).isEqualTo("PRE_AUTHORIZED_DEBIT");
    assertThat(page.displayedTotalBalance()).startsWith("$");

    page.switchLanguageTo("fr");

    assertThat(page.paymentPlan()).as("the code does not translate").isEqualTo("MONTHLY");
    assertThat(page.displayedTotalBalance())
        .as("but the way the figure is written does")
        .endsWith("$")
        .contains(",");
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void anUnknownAccountShowsTheConsolesOwnNotFoundPage() {
    AccountSummaryPage page =
        new AccountSummaryPage().openExpectingNotFound("ACCT-DOES-NOT-EXIST");

    assertThat(page.showsNotFoundMessage())
        .as("a mistyped account number should not produce a JSON error page in a browser")
        .isTrue();
  }
}
