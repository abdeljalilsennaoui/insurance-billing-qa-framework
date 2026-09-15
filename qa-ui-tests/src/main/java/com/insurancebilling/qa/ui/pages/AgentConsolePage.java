package com.insurancebilling.qa.ui.pages;

import java.util.List;
import org.openqa.selenium.WebElement;

/**
 * The agent console: the portfolio grid, and the term panels it drills into.
 *
 * <p>Everything below the grid is inherited from {@link TermPanelsPage} - the same accessors the
 * policyholder's screen is read with, because it is the same markup. A test can therefore open both
 * consoles and compare what they say figure for figure, which is the assertion a second copy of these
 * methods would quietly make impossible.
 *
 * <p>Grid rows are addressed by term reference, never by position. The grid is sorted by policy
 * number, and a defect that re-sorted it would still satisfy an assertion written against "the second
 * row".
 */
public class AgentConsolePage extends TermPanelsPage {

  public AgentConsolePage open() {
    navigateTo("/agent");
    return waitUntilLoaded();
  }

  public AgentConsolePage open(String termReference, String tab) {
    navigateTo("/agent?term=" + termReference + "&tab=" + tab);
    return waitUntilLoaded();
  }

  @Override
  AgentConsolePage waitUntilLoaded() {
    visible("page-title");
    return this;
  }

  @Override
  public AgentConsolePage openTab(String tab) {
    super.openTab(tab);
    return this;
  }

  // ------------------------------------------------------------------- grid

  public List<WebElement> portfolioRows() {
    return allVisible("portfolio-row");
  }

  public int portfolioRowCount() {
    return portfolioRows().size();
  }

  /** Term references in the order the grid shows them. */
  public List<String> termReferencesOnGrid() {
    return portfolioRows().stream().map(row -> row.getDomAttribute("data-term")).toList();
  }

  public List<String> policyNumbersOnGrid() {
    return driver.findElements(testId("portfolio-policy-number")).stream()
        .map(element -> element.getText().trim())
        .toList();
  }

  public List<String> balancesOnGrid() {
    return driver.findElements(testId("portfolio-balance")).stream()
        .map(element -> element.getDomAttribute("data-amount"))
        .toList();
  }

  public String total() {
    return attributeOf("portfolio-total", "data-amount");
  }

  public String insuredOn(String termReference) {
    return cellOf(termReference, "portfolio-insured").getText().trim();
  }

  public String productOn(String termReference) {
    return cellOf(termReference, "portfolio-product").getDomAttribute("data-product");
  }

  /**
   * The product as the reader sees it written.
   *
   * <p>The only accessor here that reads display copy rather than an attribute, and it exists for the
   * one assertion that is about the words: that the grid is translated at all.
   */
  public String productLabelOn(String termReference) {
    return cellOf(termReference, "portfolio-product").getText().trim();
  }

  public String statusOn(String termReference) {
    return cellOf(termReference, "portfolio-status").getDomAttribute("data-status");
  }

  public String balanceOn(String termReference) {
    return cellOf(termReference, "portfolio-balance").getDomAttribute("data-amount");
  }

  public String accountOn(String termReference) {
    return row(termReference).getDomAttribute("data-account");
  }

  /** The term reference of the row the panels below belong to. */
  public String selectedTermOnGrid() {
    return portfolioRows().stream()
        .filter(row -> classesOf(row).contains("current"))
        .map(row -> row.getDomAttribute("data-term"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No row on the grid is marked as selected"));
  }

  /** Opens a term by clicking its row, the way an agent reaches it. */
  public AgentConsolePage selectTerm(String termReference) {
    markCurrentDocument();
    cellOf(termReference, "portfolio-policy-number").click();
    waitForNewDocument();
    return waitUntilLoaded();
  }

  public boolean showsEmptyPortfolioMessage() {
    return isPresent("no-portfolio-message");
  }

  public AgentConsolePage switchLanguageTo(String language) {
    markCurrentDocument();
    click("lang-toggle-" + language);
    waitForNewDocument();
    return waitUntilLoaded();
  }

  private static String classesOf(WebElement row) {
    String classes = row.getDomAttribute("class");
    return classes == null ? "" : classes;
  }

  private WebElement row(String termReference) {
    return portfolioRows().stream()
        .filter(row -> termReference.equals(row.getDomAttribute("data-term")))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "No row for term "
                        + termReference
                        + " on the grid. Terms present: "
                        + termReferencesOnGrid()));
  }

  private WebElement cellOf(String termReference, String cellTestId) {
    return row(termReference).findElement(testId(cellTestId));
  }
}
