package com.insurancebilling.qa.ui.pages;

import java.util.List;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** An invoice's detail page: summary figures, payment history, and the payment form. */
public class InvoiceDetailsPage extends BasePage {

  public InvoiceDetailsPage openById(long invoiceId) {
    navigateTo("/invoices/" + invoiceId);
    return waitUntilLoaded();
  }

  InvoiceDetailsPage waitUntilLoaded() {
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("invoice-outstanding-balance")));
    return this;
  }

  public String invoiceNumber() {
    return textOf("invoice-number");
  }

  public String status() {
    return textOf("invoice-status");
  }

  public String total() {
    return textOf("invoice-total");
  }

  public String amountPaid() {
    return textOf("invoice-amount-paid");
  }

  public String outstandingBalance() {
    return textOf("invoice-outstanding-balance");
  }

  public boolean isFlaggedOverdue() {
    return isPresent("invoice-overdue-flag");
  }

  public List<WebElement> paymentRows() {
    return driver.findElements(testId("payment-row"));
  }

  public int paymentCount() {
    return paymentRows().size();
  }

  public List<String> paymentAmounts() {
    return driver.findElements(testId("payment-amount")).stream()
        .map(element -> element.getText().trim())
        .toList();
  }

  public List<String> paymentReferences() {
    return driver.findElements(testId("payment-reference")).stream()
        .map(element -> element.getText().trim())
        .toList();
  }

  public boolean showsNoPaymentsMessage() {
    return isPresent("no-payments-message");
  }

  public boolean showsFullyPaidMessage() {
    return isPresent("fully-paid-message");
  }

  public boolean showsCancelledMessage() {
    return isPresent("cancelled-message");
  }

  /**
   * Fills in and submits the payment form.
   *
   * <p>Returns this page object for both outcomes, because both outcomes land on this page: a success
   * redirects back to it and a rejection re-renders it. The test decides which it expected by asking
   * for the success banner or the error banner — the page object does not assert on the caller's behalf.
   */
  public InvoiceDetailsPage payWith(String amount, String method, String reference) {
    type("payment-amount-input", amount);
    if (method != null) {
      selectOption("payment-method-select", method);
    }
    if (reference != null) {
      type("payment-reference-input", reference);
    }

    click("submit-payment-button");
    waitForFormOutcome();
    return this;
  }

  public InvoiceDetailsPage payWith(String amount) {
    return payWith(amount, "CARD", "UI-AUTO");
  }

  /**
   * Waits for the submission to resolve one way or the other.
   */
  private void waitForFormOutcome() {
    wait.until(
        driver ->
            !driver.findElements(testId("payment-success")).isEmpty()
                || !driver.findElements(testId("payment-error")).isEmpty()
                || !driver.findElements(testId("invoice-outstanding-balance")).isEmpty());
  }

  public boolean hasSuccessBanner() {
    return isPresent("payment-success");
  }

  public boolean hasErrorBanner() {
    return isPresent("payment-error");
  }

  public String errorMessage() {
    return textOf("payment-error");
  }

  public String successMessage() {
    return textOf("payment-success");
  }

  public InvoiceListPage backToList() {
    click("back-to-list-link");
    return new InvoiceListPage();
  }
}
