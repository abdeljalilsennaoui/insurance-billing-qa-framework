package com.insurancebilling.domain;

/**
 * Lifecycle state of a policy term's billing.
 *
 * <p>A term only accepts payment while it is {@code IN_FORCE}. {@code PENDING} is a bound term whose
 * effective date has not arrived, {@code EXPIRED} one whose term has run out, and {@code CANCELLED}
 * one ended early. The three non-payable states are kept distinct rather than collapsed into a single
 * "not payable" flag so that a refusal can say which one applied.
 */
public enum TermStatus {
  PENDING,
  IN_FORCE,
  EXPIRED,
  CANCELLED
}
