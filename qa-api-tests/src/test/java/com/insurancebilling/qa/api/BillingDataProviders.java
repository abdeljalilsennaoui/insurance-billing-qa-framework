package com.insurancebilling.qa.api;

import org.testng.annotations.DataProvider;

/**
 * Rows for the billing suite's data-driven refusals.
 *
 * <p>Every row pairs an input with the specific error code it must produce, never with "some 422". A
 * provider that only asserted the status would keep passing if two rules were swapped, which is the one
 * thing these tests exist to notice.
 */
public final class BillingDataProviders {

  private BillingDataProviders() {}

  @DataProvider(name = "invalidTermPaymentAmounts")
  public static Object[][] invalidTermPaymentAmounts() {
    return new Object[][] {
      {"0.00", "AMOUNT_NOT_POSITIVE", "zero is not a sum of money"},
      {"-1.00", "AMOUNT_NOT_POSITIVE", "a negative payment"},
      {"-130.80", "AMOUNT_NOT_POSITIVE", "a negative payment the size of an installment"},
      {"10.001", "AMOUNT_SCALE_INVALID", "more precision than money has"},
      {"0.005", "AMOUNT_SCALE_INVALID", "half a cent"},
      {"1591.61", "EXCEEDS_OUTSTANDING_BALANCE", "one cent beyond the balance"},
      {"99999.00", "EXCEEDS_OUTSTANDING_BALANCE", "far beyond the balance"}
    };
  }

  @DataProvider(name = "malformedTermPaymentBodies")
  public static Object[][] malformedTermPaymentBodies() {
    return new Object[][] {
      {"{\"amount\":}", "MALFORMED_REQUEST", "truncated JSON"},
      {"not json at all", "MALFORMED_REQUEST", "not JSON"},
      {"{\"amount\":\"abc\"}", "MALFORMED_REQUEST", "an amount that is not a number"},
      {"{}", "VALIDATION_FAILED", "no amount at all"},
      {"{\"description\":\"no amount\"}", "VALIDATION_FAILED", "a description but no amount"}
    };
  }

  @DataProvider(name = "returnReasonsAndTheirNsfEffect")
  public static Object[][] returnReasonsAndTheirNsfEffect() {
    return new Object[][] {
      {"INSUFFICIENT_FUNDS", 1, "a funding problem counts against both tallies"},
      {"ACCOUNT_CLOSED", 0, "a closed account is a payment problem, not a funding one"},
      {"PAYMENT_STOPPED", 0, "a stopped payment is the policyholder's instruction"},
      {"INVALID_ACCOUNT", 0, "wrong details are not missing money"}
    };
  }
}
