package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.api.model.PaymentDto;
import org.testng.annotations.Test;

/**
 * Happy-path payment behaviour through the REST API.
 *
 * <p>Every test creates its own invoice, so these can run in parallel and in any order, and the suite
 * produces the same result on a second run against the same application instance.
 */
public class InvoicePaymentApiIT extends BaseApiTest {

  @Test(groups = {"smoke", "regression"})
  public void newInvoiceStartsUnpaidWithFullBalanceOutstanding() {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    assertThat(invoice.status()).isEqualTo("UNPAID");
    assertThat(invoice.amountPaid()).isEqualByComparingTo("0.00");
    assertThat(invoice.outstandingBalance()).isEqualByComparingTo("450.00");
    assertThat(invoice.overdue()).isFalse();
    assertThat(invoice.invoiceNumber()).startsWith("INV-");
    assertThat(invoice.payments()).isEmpty();
  }

  @Test(groups = {"smoke", "regression"})
  public void partialPaymentReducesTheOutstandingBalance() {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    PaymentDto payment = invoices.pay(invoice.id(), "150.00");

    assertThat(payment.amount()).isEqualByComparingTo("150.00");
    assertThat(payment.id()).isNotNull();

    InvoiceDto updated = invoices.get(invoice.id());
    assertThat(updated.status()).isEqualTo("PARTIALLY_PAID");
    assertThat(updated.amountPaid()).isEqualByComparingTo("150.00");
    assertThat(updated.outstandingBalance()).isEqualByComparingTo("300.00");
  }

  @Test(groups = {"smoke", "regression"})
  public void payingTheOutstandingBalanceSettlesTheInvoice() {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    invoices.pay(invoice.id(), "450.00");

    InvoiceDto settled = invoices.get(invoice.id());
    assertThat(settled.status()).isEqualTo("PAID");
    assertThat(settled.outstandingBalance()).isEqualByComparingTo("0.00");
  }

  @Test(groups = "regression")
  public void sequentialPartialPaymentsSettleTheInvoiceExactly() {
    InvoiceDto invoice = testData.unpaidInvoice("100.00");

    invoices.pay(invoice.id(), "33.33");
    invoices.pay(invoice.id(), "33.33");
    invoices.pay(invoice.id(), "33.34");

    InvoiceDto settled = invoices.get(invoice.id());
    assertThat(settled.amountPaid()).isEqualByComparingTo("100.00");
    assertThat(settled.outstandingBalance()).isEqualByComparingTo("0.00");
    assertThat(settled.status()).isEqualTo("PAID");
    assertThat(settled.payments()).hasSize(3);
  }

  @Test(groups = "regression")
  public void paymentsAreReturnedInTheOrderTheyWereReceived() {
    InvoiceDto invoice = testData.unpaidInvoice("300.00");

    invoices.payRaw(
        invoice.id(),
        new com.insurancebilling.qa.api.model.PaymentRequestBody("100.00", "CARD", "FIRST"))
        .then()
        .statusCode(201);
    invoices.payRaw(
        invoice.id(),
        new com.insurancebilling.qa.api.model.PaymentRequestBody("50.00", "BANK_TRANSFER", "SECOND"))
        .then()
        .statusCode(201);

    assertThat(invoices.payments(invoice.id()))
        .extracting(PaymentDto::reference)
        .containsExactly("FIRST", "SECOND");
  }

  @Test(groups = "regression")
  public void eachAcceptedPaymentMethodIsRecorded() {
    for (String method : new String[] {"CARD", "BANK_TRANSFER", "DIRECT_DEBIT", "CHEQUE"}) {
      InvoiceDto invoice = testData.unpaidInvoice("100.00");

      invoices
          .payRaw(
              invoice.id(),
              com.insurancebilling.qa.api.model.PaymentRequestBody.of("25.00", method))
          .then()
          .statusCode(201);

      assertThat(invoices.payments(invoice.id()).get(0).method())
          .as("payment method should be recorded as sent")
          .isEqualTo(method);
    }
  }

  @Test(groups = "regression")
  public void anInvoicePastItsDueDateIsReportedOverdue() {
    InvoiceDto invoice = testData.overdueInvoice("200.00");

    InvoiceDto fetched = invoices.get(invoice.id());
    assertThat(fetched.overdue()).isTrue();
    assertThat(fetched.status()).isEqualTo("OVERDUE");
  }

  @Test(groups = "regression")
  public void payingAnOverdueInvoiceInFullClearsTheOverdueFlag() {
    InvoiceDto invoice = testData.overdueInvoice("200.00");

    invoices.pay(invoice.id(), "200.00");

    InvoiceDto settled = invoices.get(invoice.id());
    assertThat(settled.status()).isEqualTo("PAID");
    assertThat(settled.overdue()).as("a settled invoice is never overdue, however late it was paid").isFalse();
  }

  @Test(groups = "regression")
  public void theInvoiceListCanBeFilteredByStatus() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("500.00", "250.00");

    assertThat(invoices.listByStatus("PARTIALLY_PAID"))
        .anySatisfy(listed -> assertThat(listed.id()).isEqualTo(invoice.id()))
        .allSatisfy(listed -> assertThat(listed.status()).isEqualTo("PARTIALLY_PAID"));
  }

  @Test(groups = "regression")
  public void theInvoiceListIncludesDerivedBalanceFigures() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("400.00", "100.00");

    assertThat(invoices.list())
        .filteredOn(listed -> listed.id().equals(invoice.id()))
        .singleElement()
        .satisfies(
            listed -> {
              assertThat(listed.amountPaid()).isEqualByComparingTo("100.00");
              assertThat(listed.outstandingBalance()).isEqualByComparingTo("300.00");
              assertThat(listed.customerName()).isNotBlank();
              assertThat(listed.policyNumber()).startsWith("POL-");
            });
  }
}
