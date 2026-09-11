package com.insurancebilling.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Generates human-readable, collision-free business references.
 *
 * <p>A random suffix is used rather than an incrementing counter because the performance suite submits
 * concurrent requests: a counter read-then-write would hand the same number to two threads and fail on
 * the unique constraint.
 */
@Component
public class ReferenceGenerator {

  public String policyNumber() {
    return "POL-" + randomSuffix();
  }

  public String invoiceNumber() {
    return "INV-" + randomSuffix();
  }

  private String randomSuffix() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
