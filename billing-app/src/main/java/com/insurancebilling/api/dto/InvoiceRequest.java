package com.insurancebilling.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Inbound payload for raising an invoice against a policy.
 *
 * <p>The due date is deliberately not constrained to the future: automated suites need to create
 * already-overdue invoices in order to test the overdue behaviour.
 */
public record InvoiceRequest(
    @NotNull(message = "policyId is required") Long policyId,
    @NotNull(message = "totalAmount is required")
        @DecimalMin(value = "0.01", message = "totalAmount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "totalAmount must have at most 2 decimal places")
        BigDecimal totalAmount,
    @NotNull(message = "issueDate is required") LocalDate issueDate,
    @NotNull(message = "dueDate is required") LocalDate dueDate) {}
