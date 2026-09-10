package com.insurancebilling.domain;

/** How a payment reached the billing platform. */
public enum PaymentMethod {
  CARD,
  BANK_TRANSFER,
  DIRECT_DEBIT,
  CHEQUE
}
