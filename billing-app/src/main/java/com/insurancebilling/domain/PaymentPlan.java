package com.insurancebilling.domain;

/**
 * How a policy term's total is spread across the term.
 *
 * <p>The constant carries the number of installments rather than leaving it to the caller, so a term
 * cannot be created on a monthly plan with four installments. The schedule generator reads the count
 * from here and nowhere else.
 */
public enum PaymentPlan {
  MONTHLY(12),
  QUARTERLY(4),
  SEMI_ANNUAL(2),
  ANNUAL(1);

  private final int installmentCount;

  PaymentPlan(int installmentCount) {
    this.installmentCount = installmentCount;
  }

  /** Number of installments a full term on this plan is billed in. */
  public int installmentCount() {
    return installmentCount;
  }

  /** Number of months between one scheduled installment and the next. */
  public int monthsBetweenInstallments() {
    return 12 / installmentCount;
  }
}
