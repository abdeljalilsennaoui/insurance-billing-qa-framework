package com.insurancebilling.qa.ui.pages;

/**
 * A policyholder's account summary: what is owed, what falls due next, and how it is collected.
 *
 * <p>Money and counts are read from data attributes rather than from the rendered text, because the
 * console is bilingual and the rendered text is display copy. {@link #displayedAccountNumber()} is the
 * exception and is deliberate: the masking of a bank account number is about what the reader sees, so
 * that one assertion has to read what the reader sees.
 */
public class AccountSummaryPage extends BasePage {

  public AccountSummaryPage open(String accountReference) {
    navigateTo("/accounts/" + accountReference);
    return waitUntilLoaded();
  }

  AccountSummaryPage waitUntilLoaded() {
    visible("total-balance");
    return this;
  }

  public String insuredName() {
    return textOf("context-insured");
  }

  public String accountReference() {
    return textOf("context-account");
  }

  public String totalBalance() {
    return attributeOf("total-balance", "data-amount");
  }

  public String displayedTotalBalance() {
    return textOf("total-balance");
  }

  public String unappliedAmount() {
    return attributeOf("unapplied-amount", "data-amount");
  }

  public String nextPaymentAmount() {
    return attributeOf("next-payment-amount", "data-amount");
  }

  public String nextPaymentDate() {
    return attributeOf("next-payment-date", "data-date");
  }

  public int nsfCount() {
    return Integer.parseInt(textOf("nsf-count"));
  }

  public int returnedPaymentCount() {
    return Integer.parseInt(textOf("returned-payment-count"));
  }

  public String paymentPlan() {
    return attributeOf("payment-plan", "data-plan");
  }

  public String paymentMethod() {
    return attributeOf("payment-method", "data-method");
  }

  public String bankAccountHolder() {
    return textOf("bank-account-holder");
  }

  /** The account number exactly as the reader sees it. The point of this page object method. */
  public String displayedAccountNumber() {
    return textOf("bank-account-number");
  }

  public String displayedInstitutionNumber() {
    return textOf("bank-institution");
  }

  public String displayedBranchNumber() {
    return textOf("bank-branch");
  }

  public boolean showsNoPaymentMethod() {
    return isPresent("no-payment-method");
  }

  /**
   * Opens a reference that may not exist.
   *
   * <p>Separate from {@link #open} because that one waits for a balance, which a not-found page has
   * none of - the wait would time out before the test could assert on what it came to see.
   */
  public AccountSummaryPage openExpectingNotFound(String accountReference) {
    navigateTo("/accounts/" + accountReference);
    return this;
  }

  public boolean showsNotFoundMessage() {
    return isPresent("not-found-message");
  }

  /** Follows the left navigation through to the terms screen. */
  public TermsPage openTerms() {
    markCurrentDocument();
    click("nav-account-terms");
    waitForNewDocument();
    return new TermsPage().waitUntilLoaded();
  }

  public AccountSummaryPage switchLanguageTo(String language) {
    markCurrentDocument();
    click("lang-toggle-" + language);
    waitForNewDocument();
    return waitUntilLoaded();
  }
}
