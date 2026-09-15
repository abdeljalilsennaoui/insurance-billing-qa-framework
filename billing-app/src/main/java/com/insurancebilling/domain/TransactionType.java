package com.insurancebilling.domain;

/**
 * What a ledger entry records.
 *
 * <p>The sign convention is fixed and is the ledger's central invariant: a type that increases what
 * the policyholder owes posts a positive amount, one that decreases it posts a negative amount. The
 * running balance is then simply the ordered sum, with no per-type special casing anywhere.
 */
public enum TransactionType {
  /** The whole term's premium and tax, posted when the policy is bound. Positive. */
  NEW_BUSINESS,
  /** Money received. Negative. */
  PAYMENT,
  /** A previously recorded payment that the bank did not honour. Positive, reversing the payment. */
  PAYMENT_RETURNED,
  /** The charge raised when a payment is returned. Positive. */
  NSF_FEE,
  /** The charge for spreading a term over installments rather than paying it in full. Positive. */
  INSTALLMENT_FEE,
  /** The charge for putting a cancelled term back in force. Positive. */
  REINSTATEMENT_FEE,
  /** Premium and tax returned when a term ends early. Negative. */
  CANCELLATION
}
