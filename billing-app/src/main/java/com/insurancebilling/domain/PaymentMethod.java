package com.insurancebilling.domain;

/** How a payment reached the billing platform. */
public enum PaymentMethod {
  CARD,
  BANK_TRANSFER,
  DIRECT_DEBIT,
  CHEQUE,
  /**
   * A recurring debit the insurer pulls from a bank account the policyholder authorised once.
   *
   * <p>This is the method an installment schedule is normally collected by, and the only one whose
   * bank details the platform holds. See {@link BankAccountReference} for what "holds" means here.
   */
  PRE_AUTHORIZED_DEBIT
}
