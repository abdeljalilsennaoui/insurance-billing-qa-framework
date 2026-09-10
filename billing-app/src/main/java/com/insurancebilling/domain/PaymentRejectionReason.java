package com.insurancebilling.domain;

/**
 * The distinct reasons a payment can be refused.
 *
 * <p>Each reason exists as its own constant so that a test can assert an invoice was rejected for the
 * right reason. A single generic "invalid payment" error would still pass if one of the underlying
 * rules were removed.
 */
public enum PaymentRejectionReason {
  AMOUNT_NOT_POSITIVE,
  AMOUNT_SCALE_INVALID,
  EXCEEDS_OUTSTANDING_BALANCE,
  INVOICE_ALREADY_PAID,
  INVOICE_CANCELLED,
  POLICY_NOT_ACTIVE
}
