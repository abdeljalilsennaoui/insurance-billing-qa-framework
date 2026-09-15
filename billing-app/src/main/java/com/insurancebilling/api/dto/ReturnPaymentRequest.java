package com.insurancebilling.api.dto;

import com.insurancebilling.domain.ReturnReason;
import jakarta.validation.constraints.NotNull;

/**
 * Notification that a bank refused a payment.
 *
 * <p>The reason is required and is an enum, so an unknown value is a malformed request — 400 — rather
 * than a refused one. That is the right answer: the caller has sent something this platform has no
 * meaning for, not something it disagrees with.
 */
public record ReturnPaymentRequest(@NotNull(message = "reason is required") ReturnReason reason) {}
