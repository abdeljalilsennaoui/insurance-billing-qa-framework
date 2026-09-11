package com.insurancebilling.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * The single error shape returned by every failing request.
 *
 * <p>{@code code} is the machine-readable discriminator that automated tests assert on:
 * {@code VALIDATION_FAILED}, {@code NOT_FOUND}, or the name of a
 * {@link com.insurancebilling.domain.PaymentRejectionReason}. Asserting a status code alone would not
 * prove the platform refused a payment for the expected reason.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    Map<String, String> fieldErrors) {

  public static ErrorResponse of(int status, String code, String message, String path) {
    return new ErrorResponse(Instant.now(), status, code, message, path, null);
  }

  public static ErrorResponse validation(
      int status, String message, String path, Map<String, String> fieldErrors) {
    return new ErrorResponse(
        Instant.now(), status, "VALIDATION_FAILED", message, path, fieldErrors);
  }
}
