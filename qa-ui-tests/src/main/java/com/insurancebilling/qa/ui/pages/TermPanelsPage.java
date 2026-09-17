package com.insurancebilling.qa.ui.pages;

import java.util.List;
import java.util.Optional;
import org.openqa.selenium.WebElement;

/**
 * A policy term's header figures and the three panels behind them, wherever they are rendered.
 *
 * <p>The policyholder's terms screen and the agent console show the same panels, from the same
 * Thymeleaf fragment, so they are read by the same page object. Two copies of these accessors would
 * drift, and the pair of screens most worth comparing would be the pair no test could compare.
 *
 * <p>The panels are tabs with a URL each rather than JavaScript, so {@link #openTab} navigates and
 * waits for a new document exactly as any other link does. That is also why a test can open one
 * directly without clicking through.
 *
 * <p>Rows are addressed by their installment number or transaction reference, never by position. A
 * schedule reordered by a defect would still satisfy an index-based assertion.
 *
 * <p>The billing assistant panel is read from here for the same reason the term panels are: it is one
 * Thymeleaf fragment rendered on both screens, and accessors kept in two places would drift until the
 * two consoles could no longer be compared.
 */
public abstract class TermPanelsPage extends BasePage {

  /** Waits until the page this object represents has rendered. */
  abstract TermPanelsPage waitUntilLoaded();

  // ----------------------------------------------------------------- header

  public String termReference() {
    return attributeOf("term-header", "data-term");
  }

  public String effectiveDate() {
    return attributeOf("term-effective-date", "data-date");
  }

  public String expiryDate() {
    return attributeOf("term-expiry-date", "data-date");
  }

  public String billingType() {
    return attributeOf("term-billing-type", "data-billing-type");
  }

  public String status() {
    return attributeOf("term-status", "data-status");
  }

  public String displayedStatusLabel() {
    return textOf("term-status");
  }

  public int installmentsRemaining() {
    return Integer.parseInt(textOf("installments-remaining"));
  }

  // ------------------------------------------------------------------- tabs

  public TermPanelsPage openTab(String tab) {
    markCurrentDocument();
    click("tab-" + tab);
    waitForNewDocument();
    return waitUntilLoaded();
  }

  public boolean showsSummary() {
    return isPresent("term-summary");
  }

  public boolean showsSchedule() {
    return isPresent("schedule-table");
  }

  public boolean showsLedger() {
    return isPresent("ledger-table");
  }

  // ---------------------------------------------------------------- summary

  public String termPremium() {
    return attributeOf("term-premium", "data-amount");
  }

  public String termTax() {
    return attributeOf("term-tax", "data-amount");
  }

  public String termFees() {
    return attributeOf("term-fees", "data-amount");
  }

  public String scheduledTotal() {
    return attributeOf("term-scheduled-total", "data-amount");
  }

  public String balance() {
    return attributeOf("term-balance", "data-amount");
  }

  // --------------------------------------------------------------- schedule

  public List<WebElement> installmentRows() {
    return allVisible("installment-row");
  }

  public int installmentCount() {
    return installmentRows().size();
  }

  public String amountDueOn(int sequenceNumber) {
    return installmentCell(sequenceNumber, "installment-amount", "data-amount");
  }

  public String premiumOn(int sequenceNumber) {
    return installmentCell(sequenceNumber, "installment-premium", "data-amount");
  }

  public String feeOn(int sequenceNumber) {
    return installmentCell(sequenceNumber, "installment-fee", "data-amount");
  }

  public String statusOfInstallment(int sequenceNumber) {
    return installmentCell(sequenceNumber, "installment-status", "data-status");
  }

  public List<String> installmentStatuses() {
    return driver.findElements(testId("installment-status")).stream()
        .map(element -> element.getDomAttribute("data-status"))
        .toList();
  }

  public List<String> amountsDue() {
    return driver.findElements(testId("installment-amount")).stream()
        .map(element -> element.getDomAttribute("data-amount"))
        .toList();
  }

  // ----------------------------------------------------------------- ledger

  public List<WebElement> ledgerRows() {
    return allVisible("ledger-row");
  }

  public int ledgerRowCount() {
    return ledgerRows().size();
  }

  /** Ledger line types in the order they are shown, which is newest first. */
  public List<String> ledgerTypes() {
    return ledgerRows().stream().map(row -> row.getDomAttribute("data-type")).toList();
  }

  public List<String> ledgerAmounts() {
    return driver.findElements(testId("ledger-amount")).stream()
        .map(element -> element.getDomAttribute("data-amount"))
        .toList();
  }

  public List<String> ledgerBalances() {
    return driver.findElements(testId("ledger-balance")).stream()
        .map(element -> element.getDomAttribute("data-amount"))
        .toList();
  }

  /** The first line of the given type, for the assertions that are about one posting. */
  public Optional<WebElement> ledgerLineOfType(String type) {
    return ledgerRows().stream()
        .filter(row -> type.equals(row.getDomAttribute("data-type")))
        .findFirst();
  }

  public String ledgerAmountOfType(String type) {
    return requireLine(type).findElement(testId("ledger-amount")).getDomAttribute("data-amount");
  }

  public String ledgerBalanceAfterType(String type) {
    return requireLine(type).findElement(testId("ledger-balance")).getDomAttribute("data-amount");
  }

  private WebElement requireLine(String type) {
    return ledgerLineOfType(type)
        .orElseThrow(
            () ->
                new AssertionError(
                    "No ledger line of type " + type + ". Types present: " + ledgerTypes()));
  }

  private String installmentCell(int sequenceNumber, String cellTestId, String attribute) {
    return installmentRows().stream()
        .filter(row -> String.valueOf(sequenceNumber).equals(row.getDomAttribute("data-installment")))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "No installment "
                        + sequenceNumber
                        + " on the schedule. Installments present: "
                        + installmentRows().stream()
                            .map(row -> row.getDomAttribute("data-installment"))
                            .toList()))
        .findElement(testId(cellTestId))
        .getDomAttribute(attribute);
  }

  // -------------------------------------------------------------- assistant

  public boolean showsAssistantPanel() {
    return isPresent("assistant-panel");
  }

  /** The panel's heading as displayed. Display copy, so for language tests only. */
  public String assistantHeading() {
    return textOf("assistant-heading");
  }

  /** Which assistant answered: anthropic, replay or disabled. Read, not assumed. */
  public String assistantProvider() {
    return attributeOf("assistant-panel", "data-provider");
  }

  /**
   * Types a question and submits it.
   *
   * <p>The form is an ordinary POST that re-renders the page, so this waits for a new document like
   * every other navigation in this suite rather than polling for text to change. A wait on the text
   * would pass the instant the old page still showed the previous answer.
   */
  public TermPanelsPage askAssistant(String question) {
    type("assistant-question", question);
    markCurrentDocument();
    click("assistant-submit");
    waitForNewDocument();
    waitUntilLoaded();
    return this;
  }

  public boolean showsAssistantAnswer() {
    return isPresent("assistant-answer");
  }

  /** True when the assistant answered, false when it had nothing to say. Read from the attribute. */
  public boolean assistantAnswered() {
    return "true".equals(attributeOf("assistant-answer", "data-available"));
  }

  public String assistantAnswerText() {
    return textOf("assistant-answer-text");
  }

  /**
   * The tools behind the answer, in the order the assistant called them.
   *
   * <p>Empty is a legitimate answer here - an assistant that had nothing to say made no calls - so
   * this must not use the waiting accessors. {@code allVisible} waits for the element to appear, which
   * for an answer with no trace means fifteen seconds and then a timeout dressed up as a failure. A
   * negative check has to be able to observe absence quickly and say so.
   */
  public List<String> assistantTrace() {
    return traceAttribute("data-tool");
  }

  /** The reference each call was made with, so a test can check it stayed inside the account. */
  public List<String> assistantTraceArguments() {
    return traceAttribute("data-argument");
  }

  private List<String> traceAttribute(String attribute) {
    if (!isPresent("assistant-trace-row")) {
      return List.of();
    }
    return allVisible("assistant-trace-row").stream()
        .map(row -> row.getDomAttribute(attribute))
        .toList();
  }
}
