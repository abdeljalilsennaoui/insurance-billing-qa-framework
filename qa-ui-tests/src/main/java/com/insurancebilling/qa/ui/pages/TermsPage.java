package com.insurancebilling.qa.ui.pages;

/**
 * The policyholder's own view of a term, reached from their billing account.
 *
 * <p>Adds only the way in. Everything below the term header is inherited from {@link TermPanelsPage},
 * which the agent console reads through as well.
 */
public class TermsPage extends TermPanelsPage {

  public TermsPage open(String accountReference) {
    navigateTo("/accounts/" + accountReference + "/terms");
    return waitUntilLoaded();
  }

  public TermsPage open(String accountReference, String tab) {
    navigateTo("/accounts/" + accountReference + "/terms?tab=" + tab);
    return waitUntilLoaded();
  }

  @Override
  TermsPage waitUntilLoaded() {
    visible("page-title");
    return this;
  }

  public boolean showsNoTermsMessage() {
    return isPresent("no-terms-message");
  }

  @Override
  public TermsPage openTab(String tab) {
    super.openTab(tab);
    return this;
  }

  public TermsPage switchLanguageTo(String language) {
    markCurrentDocument();
    click("lang-toggle-" + language);
    waitForNewDocument();
    return waitUntilLoaded();
  }
}
