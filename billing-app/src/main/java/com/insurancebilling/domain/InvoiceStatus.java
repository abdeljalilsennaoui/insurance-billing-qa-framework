package com.insurancebilling.domain;

/**
 * Lifecycle state of an invoice.
 *
 * <p>{@code UNPAID}, {@code PARTIALLY_PAID} and {@code PAID} are derived from the payments recorded
 * against the invoice and are never set directly by a caller. {@code OVERDUE} is derived from the due
 * date, and {@code CANCELLED} is the one state an operator sets explicitly.
 */
public enum InvoiceStatus {
  UNPAID,
  PARTIALLY_PAID,
  PAID,
  OVERDUE,
  CANCELLED
}
