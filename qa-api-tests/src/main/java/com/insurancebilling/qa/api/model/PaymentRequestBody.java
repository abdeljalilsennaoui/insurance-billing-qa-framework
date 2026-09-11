package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;

/**
 * Payment payload sent to the API.
 *
 * <p>The amount is a String so a test can send values a typed field could not express: an
 * over-precise {@code 10.001}, an empty amount, or outright nonsense. Negative testing needs to be
 * able to send a malformed request on purpose.
 */
public record PaymentRequestBody(String amount, String method, String reference) {

  public static PaymentRequestBody of(String amount) {
    return new PaymentRequestBody(amount, "CARD", "QA-AUTO");
  }

  public static PaymentRequestBody of(BigDecimal amount) {
    return of(amount.toPlainString());
  }

  public static PaymentRequestBody of(String amount, String method) {
    return new PaymentRequestBody(amount, method, "QA-AUTO");
  }
}
