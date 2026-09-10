package com.insurancebilling.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for the monetary values used throughout billing.
 *
 * <p>Every amount in this application is a {@link BigDecimal} scaled to 2 decimal places. No monetary
 * value is ever held in a {@code double}: repeated partial payments against an invoice have to add up
 * to the invoice total exactly, and binary floating point cannot guarantee that.
 */
public final class Money {

  /** Number of decimal places every stored monetary amount is held at. */
  public static final int SCALE = 2;

  public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);

  private Money() {}

  /** Returns the amount at the canonical scale, rejecting any value that needs rounding. */
  public static BigDecimal normalise(BigDecimal amount) {
    return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  /** Returns the amount at the canonical scale, rounding half-up. Used for derived display values. */
  public static BigDecimal round(BigDecimal amount) {
    return amount.setScale(SCALE, RoundingMode.HALF_UP);
  }

  /** True when the amount carries no more precision than the platform stores. */
  public static boolean hasValidScale(BigDecimal amount) {
    return amount.stripTrailingZeros().scale() <= SCALE;
  }

  /** True when the amount is strictly greater than zero. */
  public static boolean isPositive(BigDecimal amount) {
    return amount.compareTo(BigDecimal.ZERO) > 0;
  }
}
