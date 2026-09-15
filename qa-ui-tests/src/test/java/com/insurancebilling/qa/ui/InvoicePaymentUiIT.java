package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.ui.pages.InvoiceDetailsPage;
import org.testng.annotations.Test;

/**
 * The invoice payment journeys, driven through a real browser.
 *
 * <p>Each test provisions its own invoice through the API, so these can run in any order and on a
 * second pass against the same application instance.
 */
public class InvoicePaymentUiIT extends BaseUiTest {

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void aPartialPaymentReducesTheOutstandingBalance() {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("150.00");

    assertThat(page.hasSuccessBanner()).as("a valid payment should be confirmed").isTrue();
    assertThat(page.status()).isEqualTo("PARTIALLY_PAID");
    assertThat(page.amountPaid()).isEqualTo("150.00");
    assertThat(page.outstandingBalance()).isEqualTo("300.00");
    assertThat(page.paymentCount()).isEqualTo(1);
  }

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void payingTheOutstandingBalanceSettlesTheInvoice() {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("450.00");

    assertThat(page.status()).isEqualTo("PAID");
    assertThat(page.outstandingBalance()).isEqualTo("0.00");
    assertThat(page.showsFullyPaidMessage())
        .as("a settled invoice should say so rather than silently accepting more payments")
        .isTrue();
  }

  @Test(groups = "ui-regression")
  public void severalPartialPaymentsSettleTheInvoiceThroughTheUi() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id());
    page.payWith("33.33", "CARD", "UI-1");
    page.payWith("33.33", "CARD", "UI-2");
    page.payWith("33.34", "CARD", "UI-3");

    assertThat(page.outstandingBalance()).isEqualTo("0.00");
    assertThat(page.status()).isEqualTo("PAID");
    assertThat(page.paymentCount()).isEqualTo(3);
    assertThat(page.paymentReferences()).containsExactly("UI-1", "UI-2", "UI-3");
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void anOverpaymentIsRefusedAndTheBalanceIsUnchanged() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("500.00");

    assertThat(page.hasErrorBanner()).isTrue();
    assertThat(page.errorMessage()).contains("exceeds the outstanding balance");
    assertThat(page.outstandingBalance())
        .as("a refused payment must not change the balance shown")
        .isEqualTo("100.00");
    assertThat(page.paymentCount()).isZero();
    assertThat(page.showsNoPaymentsMessage()).isTrue();
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void aZeroAmountIsRefusedWithAVisibleMessage() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("0.00");

    assertThat(page.hasErrorBanner()).isTrue();
    // The console explains the refusal in the reader's words. The domain's own sentence ("Payment
    // amount must be greater than zero but was 0.00") still reaches the API, whose reader is an
    // engineer rather than a policyholder.
    assertThat(page.errorMessage()).contains("Enter an amount greater than zero");
    assertThat(page.outstandingBalance()).isEqualTo("100.00");
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void aNegativeAmountIsRefused() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("-50.00");

    assertThat(page.hasErrorBanner()).isTrue();
    assertThat(page.outstandingBalance()).isEqualTo("100.00");
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void aNonNumericAmountIsRefusedWithItsOwnMessage() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("abc");

    assertThat(page.errorMessage())
        .as("the console should tell the user what to type, not show a generic binding failure")
        .isEqualTo("Enter a valid amount, for example 125.00.");
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void anEmptyAmountIsRefused() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("");

    assertThat(page.errorMessage()).isEqualTo("Enter a payment amount.");
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void aCancelledInvoiceRefusesPayment() {
    InvoiceDto invoice = testData.cancelledInvoice("100.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id());
    assertThat(page.showsCancelledMessage()).isTrue();

    page.payWith("10.00");

    assertThat(page.hasErrorBanner()).isTrue();
    assertThat(page.errorMessage()).contains("cancelled");
  }

  @Test(groups = {"ui-negative", "ui-regression"})
  public void anInvoiceOnALapsedPolicyRefusesPayment() {
    InvoiceDto invoice = testData.invoiceOnInactivePolicy("100.00", "LAPSED");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id()).payWith("10.00");

    assertThat(page.hasErrorBanner()).isTrue();
    // Not "LAPSED": a policyholder is told what it means for them, not which constant the platform
    // holds. The API still reports POLICY_NOT_ACTIVE, and PaymentValidationApiIT asserts that.
    assertThat(page.errorMessage()).contains("no longer active");
  }

  @Test(groups = "ui-regression")
  public void thePaymentHistoryListsEveryRecordedPayment() {
    InvoiceDto invoice = testData.unpaidInvoice("300.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id());
    assertThat(page.showsNoPaymentsMessage()).as("a new invoice has no payment history").isTrue();

    page.payWith("100.00", "CARD", "FIRST");
    page.payWith("50.00", "BANK_TRANSFER", "SECOND");

    assertThat(page.paymentAmounts()).containsExactly("100.00", "50.00");
    assertThat(page.paymentReferences()).containsExactly("FIRST", "SECOND");
  }

  @Test(groups = "ui-regression")
  public void anOverdueInvoiceIsFlaggedOnItsDetailPage() {
    InvoiceDto invoice = testData.overdueInvoice("200.00");

    InvoiceDetailsPage page = new InvoiceDetailsPage().openById(invoice.id());

    assertThat(page.isFlaggedOverdue()).isTrue();
    assertThat(page.status()).isEqualTo("OVERDUE");
  }
}
