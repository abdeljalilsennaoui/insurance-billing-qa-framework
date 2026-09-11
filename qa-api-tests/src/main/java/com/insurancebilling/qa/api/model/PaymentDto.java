package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/** A recorded payment as the API publishes it. */
public record PaymentDto(
    Long id, BigDecimal amount, String method, String reference, Instant receivedAt) {}
