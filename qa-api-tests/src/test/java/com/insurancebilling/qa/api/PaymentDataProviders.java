package com.insurancebilling.qa.api;

import org.testng.annotations.DataProvider;

/**
 * Data sets for the payment validation tests.
 *
 * <p>Each row pairs an input with the <b>specific</b> rejection code it must produce. That pairing is
 * the point: a provider that only supplied invalid amounts and asserted "some 422" would keep passing
 * if two rules were merged, or if one rule started catching inputs meant for another. Naming the
 * expected reason per row means deleting a rule fails an identifiable row.
 */
public final class PaymentDataProviders {

  private PaymentDataProviders() {}

  /** Amounts the platform must refuse, with the reason each one must be refused for. */
  @DataProvider(name = "invalidPaymentAmounts")
  public static Object[][] invalidPaymentAmounts() {
    return new Object[][] {
      {"0.00", "AMOUNT_NOT_POSITIVE", "zero"},
      {"0", "AMOUNT_NOT_POSITIVE", "unscaled zero"},
      {"-0.01", "AMOUNT_NOT_POSITIVE", "one cent negative"},
      {"-250.00", "AMOUNT_NOT_POSITIVE", "clearly negative"},
      {"10.001", "AMOUNT_SCALE_INVALID", "three decimal places"},
      {"0.005", "AMOUNT_SCALE_INVALID", "sub-cent precision"},
      {"99.9999", "AMOUNT_SCALE_INVALID", "four decimal places"},
      {"450.01", "EXCEEDS_OUTSTANDING_BALANCE", "one cent over the balance"},
      {"1000.00", "EXCEEDS_OUTSTANDING_BALANCE", "far over the balance"},
    };
  }

  /**
   * Invoice states that refuse any payment, whatever the amount.
   *
   * <p>The fixture name is passed rather than a prepared invoice so the invoice is created inside the
   * test method. A provider that built invoices would create them all at collection time, long before
   * the test that uses them runs.
   */
  @DataProvider(name = "unpayableInvoiceStates")
  public static Object[][] unpayableInvoiceStates() {
    return new Object[][] {
      {"settled", "INVOICE_ALREADY_PAID"},
      {"cancelled", "INVOICE_CANCELLED"},
      {"policyLapsed", "POLICY_NOT_ACTIVE"},
      {"policyCancelled", "POLICY_NOT_ACTIVE"},
    };
  }

  /** Bodies that are malformed rather than merely refused: these must be 400, never 422. */
  @DataProvider(name = "malformedPaymentBodies")
  public static Object[][] malformedPaymentBodies() {
    return new Object[][] {
      {"{\"method\":\"CARD\"}", "VALIDATION_FAILED", "amount missing"},
      {"{\"amount\":100.00}", "VALIDATION_FAILED", "method missing"},
      {"{\"amount\": }", "MALFORMED_REQUEST", "unparseable JSON"},
      {"{\"amount\":\"abc\",\"method\":\"CARD\"}", "MALFORMED_REQUEST", "amount not a number"},
      {"{\"amount\":100.00,\"method\":\"TELEPATHY\"}", "MALFORMED_REQUEST", "unknown payment method"},
    };
  }
}
