package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.ui.pages.InvoiceDetailsPage;
import com.insurancebilling.qa.ui.pages.InvoiceListPage;
import org.testng.annotations.Test;

/**
 * The console in each of its two languages, in a real browser.
 *
 * <p>The application-level tests already prove the bundles agree and that no page renders an unresolved
 * key. What only a browser can show is that the switch works end to end: that it keeps the reader on the
 * page they were on, that the choice survives navigating somewhere else, and that what changes is the
 * words rather than the figures.
 */
public class BilingualConsoleUiIT extends BaseUiTest {

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void theConsoleCanBeReadInFrench() {
    InvoiceListPage list = new InvoiceListPage().open();
    assertThat(list.pageHeading()).isEqualTo("Invoices");

    list.switchLanguageTo("fr");

    assertThat(list.pageHeading()).isEqualTo("Factures");
  }

  @Test(groups = "ui-regression")
  public void switchingLanguageChangesTheWordsAndNotTheFigures() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("400.00", "100.00");
    InvoiceListPage list = new InvoiceListPage().open();

    String balanceBefore = list.outstandingBalanceOf(invoice.invoiceNumber());
    String statusBefore = list.statusOf(invoice.invoiceNumber());

    list.switchLanguageTo("fr");

    assertThat(list.outstandingBalanceOf(invoice.invoiceNumber()))
        .as("the amount owed cannot depend on the language it is read in")
        .isEqualTo(balanceBefore);
    assertThat(list.statusOf(invoice.invoiceNumber()))
        .as("the state is a code, not a word, and does not translate")
        .isEqualTo(statusBefore);
  }

  @Test(groups = "ui-regression")
  public void aStatusIsLabelledInTheReadersLanguageButStillReportsItsCode() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("400.00", "100.00");

    InvoiceDetailsPage details =
        new InvoiceListPage().open().openInvoice(invoice.invoiceNumber());
    assertThat(details.displayedStatusLabel()).isEqualTo("Partially paid");
    assertThat(details.status()).isEqualTo("PARTIALLY_PAID");

    details.switchLanguageTo("fr");

    assertThat(details.displayedStatusLabel()).isEqualTo("Partiellement payée");
    assertThat(details.status())
        .as("this is exactly why automation reads the attribute and not the label")
        .isEqualTo("PARTIALLY_PAID");
  }

  @Test(groups = "ui-regression")
  public void moneyIsWrittenTheWayTheReadersLanguageWritesIt() {
    InvoiceDto invoice = testData.unpaidInvoice("1450.00");

    InvoiceDetailsPage details =
        new InvoiceDetailsPage().openById(invoice.id());
    assertThat(details.displayedOutstandingBalance()).isEqualTo("$1,450.00");

    details.switchLanguageTo("fr");

    assertThat(details.displayedOutstandingBalance())
        .as("a French-Canadian statement writes the symbol last and groups with a space")
        .isEqualTo("1 450,00 $");
    assertThat(details.outstandingBalance())
        .as("the underlying figure is unchanged")
        .isEqualTo("1450.00");
  }

  @Test(groups = "ui-regression")
  public void theLanguageSwitchKeepsTheReaderOnThePageTheyWereOn() {
    InvoiceDto invoice = testData.unpaidInvoice("250.00");

    InvoiceDetailsPage details = new InvoiceDetailsPage().openById(invoice.id());
    details.switchLanguageTo("fr");

    assertThat(details.currentUrl())
        .as("a switch that sent everyone back to the list would lose their place")
        .contains("/invoices/" + invoice.id());
    assertThat(details.invoiceNumber()).isEqualTo(invoice.invoiceNumber());
  }

  @Test(groups = "ui-regression")
  public void theChosenLanguageSurvivesNavigatingSomewhereElse() {
    InvoiceDto invoice = testData.unpaidInvoice("250.00");

    new InvoiceListPage().open().switchLanguageTo("fr");
    InvoiceDetailsPage details = new InvoiceDetailsPage().openById(invoice.id());

    assertThat(details.outstandingLabel())
        .as("the choice is held in a cookie, so it applies to the next page as well")
        .isEqualTo("Solde");
  }

  @Test(groups = "ui-regression")
  public void theConsoleCanBeSwitchedBackToEnglish() {
    InvoiceListPage list = new InvoiceListPage().open();

    list.switchLanguageTo("fr");
    assertThat(list.pageHeading()).isEqualTo("Factures");

    list.switchLanguageTo("en");
    assertThat(list.pageHeading()).isEqualTo("Invoices");
  }
}
