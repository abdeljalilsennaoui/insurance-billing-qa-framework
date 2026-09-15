package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.ui.pages.InvoiceDetailsPage;
import com.insurancebilling.qa.ui.pages.InvoiceListPage;
import org.testng.annotations.Test;

/** The invoice list: rendering, status filtering, and navigation through to a detail page. */
public class InvoiceListUiIT extends BaseUiTest {

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void theInvoiceListShowsAnInvoiceWithItsBalanceAndStatus() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("400.00", "100.00");

    InvoiceListPage list = new InvoiceListPage().open();

    assertThat(list.hasInvoice(invoice.invoiceNumber())).isTrue();
    assertThat(list.statusOf(invoice.invoiceNumber())).isEqualTo("PARTIALLY_PAID");
    assertThat(list.outstandingBalanceOf(invoice.invoiceNumber())).isEqualTo("300.00");
    assertThat(list.customerOf(invoice.invoiceNumber())).isEqualTo("QA Tester");
  }

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void anInvoiceCanBeOpenedFromTheList() {
    InvoiceDto invoice = testData.unpaidInvoice("250.00");

    InvoiceDetailsPage details = new InvoiceListPage().open().openInvoice(invoice.invoiceNumber());

    assertThat(details.invoiceNumber()).isEqualTo(invoice.invoiceNumber());
    assertThat(details.total()).isEqualTo("250.00");
    assertThat(details.currentUrl()).endsWith("/invoices/" + invoice.id());
  }

  @Test(groups = "ui-regression")
  public void theListCanBeFilteredToASingleStatus() {
    testData.partiallyPaidInvoice("400.00", "100.00");

    InvoiceListPage list = new InvoiceListPage().open().filterByStatus("PARTIALLY_PAID");

    assertThat(list.displayedStatuses())
        .isNotEmpty()
        .allSatisfy(status -> assertThat(status).isEqualTo("PARTIALLY_PAID"));
  }

  @Test(groups = "ui-regression")
  public void filteringToAStatusWithNoInvoicesShowsAnEmptyStateRatherThanABlankTable() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    InvoiceListPage list = new InvoiceListPage().open().filterByStatus("PAID");

    // The filter must genuinely exclude the unpaid invoice this test created.
    assertThat(list.hasInvoice(invoice.invoiceNumber())).isFalse();
    assertThat(list.displayedStatuses())
        .allSatisfy(status -> assertThat(status).isEqualTo("PAID"));
  }

  @Test(groups = "ui-regression")
  public void anOverdueInvoiceSaysSoInItsStatusWithoutRepeatingItself() {
    InvoiceDto invoice = testData.overdueInvoice("200.00");

    InvoiceListPage list = new InvoiceListPage().open();

    assertThat(list.statusOf(invoice.invoiceNumber())).isEqualTo("OVERDUE");
    assertThat(list.isFlaggedOverdue(invoice.invoiceNumber()))
        .as("the status already says Overdue; a flag beside it would say it twice")
        .isFalse();
  }

  @Test(groups = "ui-regression")
  public void aPartPaidInvoiceThatIsLateIsFlaggedBecauseItsStatusCannotSaySo() {
    InvoiceDto invoice = testData.partiallyPaidOverdueInvoice("200.00", "50.00");

    InvoiceListPage list = new InvoiceListPage().open();

    assertThat(list.statusOf(invoice.invoiceNumber()))
        .as("payment progress is not hidden behind OVERDUE")
        .isEqualTo("PARTIALLY_PAID");
    assertThat(list.isFlaggedOverdue(invoice.invoiceNumber()))
        .as("which is exactly why the lateness has to be flagged separately")
        .isTrue();
  }

  @Test(groups = "ui-regression")
  public void aPaymentMadeOnTheDetailPageIsReflectedBackInTheList() {
    InvoiceDto invoice = testData.unpaidInvoice("200.00");

    InvoiceListPage list = new InvoiceListPage().open();
    assertThat(list.outstandingBalanceOf(invoice.invoiceNumber())).isEqualTo("200.00");

    list.openInvoice(invoice.invoiceNumber()).payWith("75.00").backToList().open();

    InvoiceListPage refreshed = new InvoiceListPage().open();
    assertThat(refreshed.outstandingBalanceOf(invoice.invoiceNumber())).isEqualTo("125.00");
    assertThat(refreshed.statusOf(invoice.invoiceNumber())).isEqualTo("PARTIALLY_PAID");
  }
}
