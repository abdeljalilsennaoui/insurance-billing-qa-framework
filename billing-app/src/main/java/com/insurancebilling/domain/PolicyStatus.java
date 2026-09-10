package com.insurancebilling.domain;

/** Lifecycle state of an insurance policy. Only an {@code ACTIVE} policy may be billed or paid. */
public enum PolicyStatus {
  ACTIVE,
  LAPSED,
  CANCELLED
}
