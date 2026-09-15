package com.insurancebilling.api.dto;

import com.insurancebilling.domain.BillingType;
import com.insurancebilling.domain.PaymentPlan;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Binds a policy term: generates its payment schedule and posts it to the ledger.
 *
 * <p>Amounts are checked for shape only — present, positive, at most two decimal places. Whether the
 * resulting schedule reconciles is not a request-validation question, it is an invariant of the
 * generator, and asserting it here would answer 400 for something that cannot happen.
 */
public record PolicyTermRequest(
    @NotNull(message = "policyId is required") Long policyId,
    @NotNull(message = "effectiveDate is required") LocalDate effectiveDate,
    @NotNull(message = "paymentPlan is required") PaymentPlan paymentPlan,
    @NotNull(message = "termPremium is required")
        @DecimalMin(value = "0.01", message = "termPremium must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "termPremium must have at most 2 decimal places")
        BigDecimal termPremium,
    @NotNull(message = "termTax is required")
        @DecimalMin(value = "0.00", message = "termTax cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "termTax must have at most 2 decimal places")
        BigDecimal termTax,
    @NotNull(message = "installmentFee is required")
        @DecimalMin(value = "0.00", message = "installmentFee cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "installmentFee must have at most 2 decimal places")
        BigDecimal installmentFee,
    BillingType billingType) {}
