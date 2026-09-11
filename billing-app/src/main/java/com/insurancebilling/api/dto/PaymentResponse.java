package com.insurancebilling.api.dto;

import com.insurancebilling.domain.Payment;
import com.insurancebilling.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;

/** Outbound representation of a recorded payment. */
public record PaymentResponse(
    Long id, BigDecimal amount, PaymentMethod method, String reference, Instant receivedAt) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getAmount(),
        payment.getMethod(),
        payment.getReference(),
        payment.getReceivedAt());
  }
}
