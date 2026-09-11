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

    // Held on to deliberately: its staleness is what proves the browser actually left this page.
    WebElement submitButton = visible("submit-payment-button");
    submitButton.click();
    waitForPageReplacement(submitButton);
    return this;
  }

  public InvoiceDetailsPage payWith(String amount) {
    return payWith(amount, "CARD", "UI-AUTO");
  }

  /**
   * Waits until the submitted page has genuinely been replaced.
   *
   * <p>The obvious wait here is wrong, and was the first thing this suite got caught by: waiting for
   * "the balance is displayed, or a banner is present" is satisfied by the page that is *already on
   * screen*, because the balance is displayed there too. The wait returned immediately, the test read
   * pre-submit values, and the next interaction hit a DOM the browser was in the middle of replacing,
   * producing StaleElementReferenceException in whichever test happened to submit twice.
   *
   * <p>Waiting for staleness of an element from the submitted page fixes it at the root: an element
   * goes stale only once the browser has actually discarded the document that contained it. This holds
   * for both outcomes, since a success redirects and a rejection re-renders, and in both cases the old
   * document is gone. Only then is it safe to wait for the new page to finish rendering.
   *
   * <p>No sleep and no retry loop: both would mask the race rather than remove it, and would leave the
   * suite passing for timing reasons that could change on any machine.
   */
  private void waitForPageReplacement(WebElement elementFromPreviousPage) {
    wait.until(ExpectedConditions.stalenessOf(elementFromPreviousPage));
    waitUntilLoaded();
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
