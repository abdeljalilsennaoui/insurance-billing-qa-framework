package com.insurancebilling.domain;

/**
 * Why a bank refused a payment the insurer had already recorded.
 *
 * <p>The reason matters because the two counters a billing account keeps are not the same counter.
 * Every refused payment is a returned payment; only {@link #INSUFFICIENT_FUNDS} is an NSF. A policy
 * held on an account closed by the bank has a payment problem, not a funding problem, and collections
 * treats the two differently.
 */
public enum ReturnReason {
  INSUFFICIENT_FUNDS,
  ACCOUNT_CLOSED,
  PAYMENT_STOPPED,
  INVALID_ACCOUNT;

  /** True when this return should also count against the account's NSF tally. */
  public boolean countsAsNsf() {
    return this == INSUFFICIENT_FUNDS;
  }
}
