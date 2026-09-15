package com.insurancebilling.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

  /** True when the amount carries no more precision than the platform stores. */
  public static boolean hasValidScale(BigDecimal amount) {
    return amount.stripTrailingZeros().scale() <= SCALE;
  }

  /** True when the amount is strictly greater than zero. */
  public static boolean isPositive(BigDecimal amount) {
    return amount.compareTo(BigDecimal.ZERO) > 0;
  }

  /** Sums a collection of amounts at the canonical scale. Returns {@link #ZERO} when empty. */
  public static BigDecimal sum(List<BigDecimal> amounts) {
    return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  /**
   * Splits a total into {@code parts} amounts that sum back to the total exactly.
   *
   * <p>Most totals do not divide evenly. 1000.00 over twelve installments is 83.3333…, and twelve
   * payments of 83.33 collect 999.96 — four cents short of what the policyholder owes. Something has
   * to absorb the remainder, and <em>which</em> something is a business decision, not a rounding
   * detail.
   *
   * <p>This method puts the whole remainder on the <strong>first</strong> part. Two consequences,
   * both deliberate:
   *
   * <ul>
   *   <li>Parts 2..n are identical. A policyholder on a monthly plan is quoted "a down payment, then
   *       eleven payments of $X", and that sentence stays true.
   *   <li>The odd cents are collected first rather than last. A remainder left to the final
   *       installment can strand a one-cent balance on a term that everyone involved believes is
   *       settled, and a one-cent balance is enough to raise a collection notice.
   * </ul>
   *
   * <p>The arithmetic never asks {@link #normalise} to round. It divides down to the canonical scale,
   * computes what that lost, and adds it back — so every returned amount is already exact and the
   * {@link RoundingMode#UNNECESSARY} contract the rest of this class depends on is never tested.
   *
   * @param total the amount to split; zero or positive, at the canonical scale
   * @param parts how many ways to split it; at least one
   * @return an immutable list of exactly {@code parts} amounts whose sum equals {@code total}
   * @throws IllegalArgumentException if {@code parts} is below one or {@code total} is negative
   * @throws ArithmeticException if {@code total} carries more precision than the platform stores
   */
  public static List<BigDecimal> allocate(BigDecimal total, int parts) {
    if (parts < 1) {
      throw new IllegalArgumentException("Cannot split an amount into " + parts + " parts");
    }
    if (total.signum() < 0) {
      throw new IllegalArgumentException(
          "Cannot split a negative amount: " + total.toPlainString());
    }

    BigDecimal exact = normalise(total);
    BigDecimal divisor = BigDecimal.valueOf(parts);
    BigDecimal each = exact.divide(divisor, SCALE, RoundingMode.DOWN);
    BigDecimal remainder = exact.subtract(each.multiply(divisor));

    List<BigDecimal> allocation = new ArrayList<>(parts);
    allocation.add(each.add(remainder));
    for (int i = 1; i < parts; i++) {
      allocation.add(each);
    }
    return Collections.unmodifiableList(allocation);
  }
}
