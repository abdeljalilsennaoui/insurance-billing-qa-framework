package com.insurancebilling.domain;

/**
 * Thrown when a well-formed payment request violates a billing rule.
 *
 * <p>This is distinct from a malformed request: the API layer maps a malformed body to 400 and this
 * exception to 422, so an automated test can tell "the client sent nonsense" apart from "the platform
 * refused the payment".
 */
public class PaymentRejectedException extends RuntimeException {

  private final PaymentRejectionReason reason;

  public PaymentRejectedException(PaymentRejectionReason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public PaymentRejectionReason getReason() {
    return reason;
  }
}
