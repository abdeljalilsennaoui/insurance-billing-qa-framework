package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.ApiError;
import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.api.model.PaymentRequestBody;
import io.restassured.response.Response;
import org.testng.annotations.Test;

/**
 * Negative payment coverage: what the platform must refuse, and for which stated reason.
 *
 * <p>Every assertion checks the {@code code} in the error body, not just the status. Four of these
 * scenarios are all 422, so a status-only assertion would pass even if the platform started refusing
 * overpayments for the wrong reason — or stopped distinguishing them at all.
 */
public class PaymentValidationApiIT extends BaseApiTest {

  @Test(
      groups = {"negative", "regression"},
      dataProvider = "invalidPaymentAmounts",
      dataProviderClass = PaymentDataProviders.class)
  public void invalidAmountsAreRefusedWithTheirOwnReason(
      String amount, String expectedCode, String description) {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    Response response = invoices.payRaw(invoice.id(), PaymentRequestBody.of(amount));

    assertThat(response.statusCode())
        .as("a well-formed request refused by a billing rule is 422 (%s)", description)
        .isEqualTo(422);
    ApiError error = response.as(ApiError.class);
    assertThat(error.code()).as("rejection reason for %s", description).isEqualTo(expectedCode);
    assertThat(error.message()).isNotBlank();

    InvoiceDto unchanged = invoices.get(invoice.id());
    assertThat(unchanged.outstandingBalance())
        .as("a refused payment must not alter the balance")
        .isEqualByComparingTo("450.00");
    assertThat(unchanged.payments()).as("a refused payment must not be recorded").isEmpty();
  }

  @Test(
      groups = {"negative", "regression"},
      dataProvider = "unpayableInvoiceStates",
      dataProviderClass = PaymentDataProviders.class)
  public void invoicesInAnUnpayableStateRefusePayment(String fixture, String expectedCode) {
    InvoiceDto invoice = invoiceInState(fixture);

    Response response = invoices.payRaw(invoice.id(), PaymentRequestBody.of("10.00"));

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.as(ApiError.class).code())
        .as("rejection reason for a %s invoice", fixture)
        .isEqualTo(expectedCode);
  }

  @Test(
      groups = {"negative", "regression"},
      dataProvider = "malformedPaymentBodies",
      dataProviderClass = PaymentDataProviders.class)
  public void malformedBodiesAreRejectedAsBadRequestsNotRuleViolations(
      String rawBody, String expectedCode, String description) {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    Response response = invoices.payWithRawBody(invoice.id(), rawBody);

    assertThat(response.statusCode())
        .as("a request the platform cannot understand is 400, never 422 (%s)", description)
        .isEqualTo(400);
    assertThat(response.as(ApiError.class).code()).isEqualTo(expectedCode);
  }

  @Test(groups = {"negative", "regression"})
  public void overpaymentIsMeasuredAgainstTheRemainingBalanceNotTheInvoiceTotal() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("450.00", "400.00");

    Response response = invoices.payRaw(invoice.id(), PaymentRequestBody.of("60.00"));

    assertThat(response.statusCode()).isEqualTo(422);
    assertThat(response.as(ApiError.class).code()).isEqualTo("EXCEEDS_OUTSTANDING_BALANCE");
    assertThat(invoices.get(invoice.id()).amountPaid()).isEqualByComparingTo("400.00");
  }

  @Test(groups = {"negative", "regression"})
  public void payingAnUnknownInvoiceIsNotFound() {
    Response response = invoices.payRaw(999_999_999L, PaymentRequestBody.of("10.00"));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.as(ApiError.class).code()).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void anUnknownStatusFilterIsRejected() {
    Response response = invoices.listByStatusRaw("NOT_A_REAL_STATUS");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.as(ApiError.class).code()).isEqualTo("MALFORMED_REQUEST");
  }

  @Test(groups = {"negative", "regression"})
  public void aRefusedPaymentLeavesEarlierPaymentsIntact() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("450.00", "50.00");

    invoices.payRaw(invoice.id(), PaymentRequestBody.of("10000.00")).then().statusCode(422);

    InvoiceDto after = invoices.get(invoice.id());
    assertThat(after.payments()).hasSize(1);
    assertThat(after.amountPaid()).isEqualByComparingTo("50.00");
    assertThat(after.status()).isEqualTo("PARTIALLY_PAID");
  }

  /** Resolves a fixture name from the data provider into a freshly built invoice. */
  private InvoiceDto invoiceInState(String fixture) {
    return switch (fixture) {
      case "settled" -> testData.settledInvoice("100.00");
      case "cancelled" -> testData.cancelledInvoice("100.00");
      case "policyLapsed" -> testData.invoiceOnInactivePolicy("100.00", "LAPSED");
      case "policyCancelled" -> testData.invoiceOnInactivePolicy("100.00", "CANCELLED");
      default -> throw new IllegalArgumentException("Unknown invoice fixture: " + fixture);
    };
  }
}
