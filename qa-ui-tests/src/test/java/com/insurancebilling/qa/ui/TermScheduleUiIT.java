package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import com.insurancebilling.qa.ui.pages.TermsPage;
import java.math.BigDecimal;
import java.util.List;
import org.testng.annotations.Test;

/**
 * The term screen's payment schedule in a browser.
 *
 * <p>The schedule is where the arithmetic becomes visible to the policyholder, so these scenarios check
 * that what is on screen adds up — not only that rows appeared.
 */
public class TermScheduleUiIT extends BaseUiTest {

  private static BigDecimal sum(List<String> amounts) {
    return amounts.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void theScheduleShowsEveryInstallmentInOrder() {
    BillingAccountDto account = testData.account();
    testData.evenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");

    assertThat(page.showsSchedule()).isTrue();
    assertThat(page.installmentCount()).isEqualTo(12);
    assertThat(page.amountDueOn(1)).isEqualTo("130.80");
    assertThat(page.amountDueOn(12)).isEqualTo("132.80");
  }

  @Test(groups = "ui-regression")
  public void theScheduleOnScreenCollectsExactlyWhatTheTermIsWorth() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.unevenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");

    assertThat(sum(page.amountsDue()))
        .as("a schedule that does not add up to the term is wrong in front of the customer")
        .isEqualByComparingTo(term.scheduledTotal());
  }

  @Test(groups = "ui-regression")
  public void theRoundingRemainderIsVisibleOnTheDownPayment() {
    BillingAccountDto account = testData.account();
    testData.unevenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");

    assertThat(page.premiumOn(1))
        .as("83.33 twelve times collects four cents less than 1000.00")
        .isEqualTo("83.37");
    assertThat(page.premiumOn(2)).isEqualTo("83.33");
    assertThat(page.amountDueOn(1)).isEqualTo("90.87");
    assertThat(page.amountDueOn(12))
        .as("nothing is stranded on the final installment")
        .isEqualTo("92.83");
  }

  @Test(groups = "ui-regression")
  public void onlyTheDownPaymentEscapesTheInstallmentFee() {
    BillingAccountDto account = testData.account();
    testData.evenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");

    assertThat(page.feeOn(1)).isEqualTo("0.00");
    assertThat(page.feeOn(2)).isEqualTo("2.00");
    assertThat(page.feeOn(12)).isEqualTo("2.00");
  }

  @Test(groups = "ui-regression")
  public void payingAnInstallmentMarksItPaidOnScreen() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    billing.pay(term.termReference(), "130.80");

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");

    assertThat(page.statusOfInstallment(1)).isEqualTo("PAID");
    assertThat(page.statusOfInstallment(2)).isNotEqualTo("PAID");
    assertThat(page.installmentsRemaining()).isEqualTo(11);
  }

  @Test(groups = "ui-regression")
  public void aReturnedPaymentLeavesItsInstallmentMarkedReversed() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.evenTerm(account.accountReference());
    String payment = billing.pay(term.termReference(), "130.80").reference();
    billing.returnPayment(payment, "INSUFFICIENT_FUNDS");

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");

    assertThat(page.statusOfInstallment(1))
        .as("an installment that bounced reads differently from one never paid")
        .isEqualTo("REVERSED");
    assertThat(page.installmentsRemaining()).isEqualTo(12);
  }

  @Test(groups = "ui-regression")
  public void anAgedScheduleSeparatesWhatIsPastFromWhatIsToCome() {
    BillingAccountDto account = testData.account();
    testData.evenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");

    assertThat(page.installmentStatuses())
        .as("a term that started two months ago cannot be entirely unbilled")
        .contains("SCHEDULED")
        .doesNotContain("PAID");
    assertThat(page.installmentStatuses())
        .anySatisfy(status -> assertThat(status).isIn("BILLED", "OVERDUE"));
  }

  @Test(groups = "ui-regression")
  public void theScheduleReadsTheSameFiguresInFrench() {
    BillingAccountDto account = testData.account();
    testData.unevenTerm(account.accountReference());

    TermsPage page = new TermsPage().open(account.accountReference(), "schedule");
    List<String> english = page.amountsDue();

    page.switchLanguageTo("fr");

    assertThat(page.amountsDue())
        .as("the money owed cannot depend on the language it is read in")
        .isEqualTo(english);
    assertThat(page.installmentCount()).isEqualTo(12);
  }
}
