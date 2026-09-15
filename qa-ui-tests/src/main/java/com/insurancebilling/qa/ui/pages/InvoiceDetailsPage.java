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

  /**
   * The invoice's state, read from its data attribute rather than from its translated label.
   *
   * <p>See {@link BasePage#attributeOf} for why. The same applies to every money figure below.
   */
  public String status() {
    return attributeOf("invoice-status", "data-status");
  }

  /** The status exactly as it is written on screen, for the tests that are about the words. */
  public String displayedStatusLabel() {
    return textOf("invoice-status");
  }

  public String total() {
    return attributeOf("invoice-total", "data-amount");
  }

  public String amountPaid() {
    return attributeOf("invoice-amount-paid", "data-amount");
  }

  public String outstandingBalance() {
    return attributeOf("invoice-outstanding-balance", "data-amount");
  }

  /** The balance exactly as it is rendered, currency symbol and all. */
  public String displayedOutstandingBalance() {
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
        .map(element -> element.getDomAttribute("data-amount"))
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

    markCurrentDocument();
    click("submit-payment-button");
    waitForNewDocument();
    waitUntilLoaded();
    return this;
  }

  public InvoiceDetailsPage payWith(String amount) {
    return payWith(amount, "CARD", "UI-AUTO");
  }

  /** Switches the console to the given language and waits for the reloaded page. */
  public InvoiceDetailsPage switchLanguageTo(String language) {
    markCurrentDocument();
    click("lang-toggle-" + language);
    waitForNewDocument();
    return waitUntilLoaded();
  }

  /** The label shown beside the outstanding figure, which is display copy and changes with language. */
  public String outstandingLabel() {
    return driver
        .findElements(org.openqa.selenium.By.cssSelector(".summary dt"))
        .stream()
        .map(element -> element.getText().trim())
        .reduce((first, last) -> last)
        .orElseThrow(() -> new AssertionError("The summary has no labels at all"));
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
