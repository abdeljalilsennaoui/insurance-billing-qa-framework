package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;

/**
 * A payment against a term.
 *
 * <p>{@code amount} is a String for the same reason {@link PaymentRequestBody}'s is: a typed field could
 * not carry {@code "abc"} or {@code 10.001} as written, and those are exactly the values the negative
 * tests need to put on the wire.
 */
public record TermPaymentBody(String amount, String description) {

  public static TermPaymentBody of(String amount) {
    return new TermPaymentBody(amount, "QA-TERM");
  }

  public static TermPaymentBody of(BigDecimal amount) {
    return of(amount.toPlainString());
  }

  public static TermPaymentBody of(String amount, String description) {
    return new TermPaymentBody(amount, description);
  }
}
