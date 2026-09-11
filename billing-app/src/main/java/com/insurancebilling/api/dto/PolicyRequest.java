package com.insurancebilling.api.dto;

import com.insurancebilling.domain.PolicyType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Inbound payload for creating a policy for an existing customer. */
public record PolicyRequest(
    @NotNull(message = "customerId is required") Long customerId,
    @NotNull(message = "type is required") PolicyType type,
    @NotNull(message = "annualPremium is required")
        @DecimalMin(value = "0.01", message = "annualPremium must be greater than zero")
        @Digits(
            integer = 10,
            fraction = 2,
            message = "annualPremium must have at most 2 decimal places")
        BigDecimal annualPremium,
    @NotNull(message = "startDate is required") LocalDate startDate,
    @NotNull(message = "endDate is required") LocalDate endDate) {}
