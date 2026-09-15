package com.insurancebilling.domain;

/**
 * Lifecycle state of a single installment on a payment schedule.
 *
 * <p>{@code SCHEDULED} is a future installment, {@code BILLED} one whose scheduled date has passed,
 * {@code PAID} one settled by a payment, {@code OVERDUE} one still unpaid past its due date, and
 * {@code REVERSED} one whose settling payment was later returned unpaid.
 *
 * <p>{@code REVERSED} is deliberately not the same as {@code BILLED}: an installment that was paid
 * and then bounced is a different thing from one that was never paid, and the account's returned
 * payment counter depends on telling them apart.
 */
public enum InstallmentStatus {
  SCHEDULED,
  BILLED,
  PAID,
  OVERDUE,
  REVERSED
}
