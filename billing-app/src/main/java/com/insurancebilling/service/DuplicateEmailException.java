package com.insurancebilling.service;

/**
 * Thrown when a customer is created with an email another customer already uses.
 *
 * <p>Mapped to 409 rather than 400: the request is well formed, it conflicts with existing state. The
 * API suite relies on that distinction when it re-runs a creation scenario.
 */
public class DuplicateEmailException extends RuntimeException {

  public DuplicateEmailException(String email) {
    super("A customer with email " + email + " already exists");
  }
}
