package com.insurancebilling.qa.ui.pages;

import java.util.List;
import java.util.Optional;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

/** The invoice list: every invoice, with status filtering and links to the detail pages. */
public class InvoiceListPage extends BasePage {

  public InvoiceListPage open() {
    navigateTo("/invoices");
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("page-title")));
    return this;
  }

  public List<WebElement> rows() {
    return driver.findElements(testId("invoice-row"));
  }

  public int rowCount() {
    return rows().size();
  }

  /**
   * The row for a given invoice number.
   *
   * <p>Addressed by the invoice number carried on the row rather than by position. Row order is not
   * part of the contract, and an index-based locator would break the moment the listing order changed
   * or another test added an invoice.
   */
  public Optional<WebElement> rowFor(String invoiceNumber) {
    return rows().stream()
        .filter(row -> invoiceNumber.equals(row.getDomAttribute("data-invoice-number")))
        .findFirst();
  }

  public boolean hasInvoice(String invoiceNumber) {
    return rowFor(invoiceNumber).isPresent();
  }

  /** The invoice's state, read from the row's data attribute rather than from its translated label. */
  public String statusOf(String invoiceNumber) {
    return cellAttribute(invoiceNumber, "invoice-status", "data-status");
  }

  /** The balance as a plain decimal, not as the currency string the reader's language would write. */
  public String outstandingBalanceOf(String invoiceNumber) {
    return cellAttribute(invoiceNumber, "invoice-balance", "data-amount");
  }

  /** The balance exactly as it is rendered, for the tests that are about the rendering. */
  public String displayedBalanceOf(String invoiceNumber) {
    return cellText(invoiceNumber, "invoice-balance");
  }

  public String customerOf(String invoiceNumber) {
    return cellText(invoiceNumber, "invoice-customer");
  }

  public boolean isFlaggedOverdue(String invoiceNumber) {
    return !requireRow(invoiceNumber)
        .findElements(testId("invoice-overdue-flag"))
        .isEmpty();
  }

  /** Follows the row's link through to the detail page. */
  public InvoiceDetailsPage openInvoice(String invoiceNumber) {
    requireRow(invoiceNumber).findElement(testId("invoice-link")).click();
    return new InvoiceDetailsPage().waitUntilLoaded();
  }

  /**
   * Applies the status filter and waits for the reloaded list.
   *
   * <p>Waiting for the page title would be satisfied by the page already on screen, since the title is
   * present on both. The document marker is the reliable signal — see
   * {@link BasePage#waitForNewDocument()} for why element staleness was not.
   */
  public InvoiceListPage filterByStatus(String status) {
    selectOption("status-filter", status);
    markCurrentDocument();
    click("apply-filter-button");
    waitForNewDocument();
    wait.until(ExpectedConditions.visibilityOfElementLocated(testId("page-title")));
    return this;
  }

  public boolean showsNoInvoicesMessage() {
    return isPresent("no-invoices-message");
  }

  /** Every state on screen, as codes rather than as the words the reader's language uses for them. */
  public List<String> displayedStatuses() {
    return driver.findElements(testId("invoice-status")).stream()
        .map(element -> element.getDomAttribute("data-status"))
        .toList();
  }

  /** Every state on screen exactly as it is written, for the tests that are about the words. */
  public List<String> displayedStatusLabels() {
    return driver.findElements(testId("invoice-status")).stream()
        .map(element -> element.getText().trim())
        .toList();
  }

  /** Switches the console to the given language and waits for the reloaded page. */
  public InvoiceListPage switchLanguageTo(String language) {
    markCurrentDocument();
    click("lang-toggle-" + language);
    waitForNewDocument();
    return this;
  }

  public String pageHeading() {
    return textOf("page-title");
  }

  private String cellText(String invoiceNumber, String cellTestId) {
    return requireRow(invoiceNumber).findElement(testId(cellTestId)).getText().trim();
  }

  private String cellAttribute(String invoiceNumber, String cellTestId, String attribute) {
    return requireRow(invoiceNumber).findElement(testId(cellTestId)).getDomAttribute(attribute);
  }

  private WebElement requireRow(String invoiceNumber) {
    // Waits for the table before resolving the row, so a test that navigates and immediately reads a
    // row does not race the page load.
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-testid='invoice-table']")));
    return rowFor(invoiceNumber)
        .orElseThrow(
            () ->
                new AssertionError(
                    "No row for invoice "
                        + invoiceNumber
                        + ". Rows present: "
                        + rows().stream().map(r -> r.getDomAttribute("data-invoice-number")).toList()));
  }
}
