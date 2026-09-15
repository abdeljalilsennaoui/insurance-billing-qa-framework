package com.insurancebilling.api.dto;

import com.insurancebilling.domain.PaymentMethod;
import com.insurancebilling.domain.PaymentPlan;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Opens a billing account for a customer.
 *
 * <p>The bank fields are the only ones a caller may send, and {@code accountLastDigits} is constrained
 * to exactly three digits here as well as in the domain. Both checks are deliberate: this one answers
 * 400 for a caller that sent the wrong shape, and the domain's answers for every other route into the
 * platform.
 */
public record BillingAccountRequest(
    @NotNull(message = "customerId is required") Long customerId,
    @NotNull(message = "paymentPlan is required") PaymentPlan paymentPlan,
    @NotNull(message = "paymentMethod is required") PaymentMethod paymentMethod,
    @Size(max = 80, message = "accountHolder must be at most 80 characters") String accountHolder,
    @Pattern(
            regexp = "\\d{3}",
            message = "accountLastDigits must be exactly three digits, never a whole account number")
        String accountLastDigits) {}
