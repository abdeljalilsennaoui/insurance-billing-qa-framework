package com.insurancebilling.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A payment submitted against a policy term.
 *
 * <p>As in {@link PaymentRequest}, the amount is checked here only for presence. Whether it is positive,
 * whether it carries too much precision and whether it exceeds the balance are billing rules, and a
 * request that breaks a billing rule is a well-formed request the platform refuses — 422, not 400. Bean
 * Validation would answer 400 and lose that distinction.
 */
public record TermPaymentRequest(
    @NotNull(message = "amount is required") BigDecimal amount,
    @Size(max = 80, message = "description must be at most 80 characters") String description) {}
