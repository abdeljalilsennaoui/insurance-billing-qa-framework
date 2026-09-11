package com.insurancebilling.api.dto;

import com.insurancebilling.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Inbound payload for paying an invoice.
 *
 * <p>Only structural validation is applied here: the amount and method must be present. Whether the
 * amount is positive, correctly scaled, or within the outstanding balance is a billing rule, enforced
 * by the domain and reported as 422 with a named reason. Constraining those here would collapse
 * "you sent nonsense" and "the platform refused your payment" into the same 400, which the API tests
 * need to tell apart.
 */
public record PaymentRequest(
    @NotNull(message = "amount is required") BigDecimal amount,
    @NotNull(message = "method is required") PaymentMethod method,
    @Size(max = 40, message = "reference must be at most 40 characters") String reference) {}
