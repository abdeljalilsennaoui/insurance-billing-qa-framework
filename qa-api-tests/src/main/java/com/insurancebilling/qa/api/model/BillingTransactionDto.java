package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One line of a billing ledger, with the balance as it stood after it. */
public record BillingTransactionDto(
    String reference,
    String type,
    String description,
    LocalDate effectiveDate,
    Instant processedAt,
    BigDecimal amount,
    BigDecimal premiumAmount,
    BigDecimal taxAmount,
    BigDecimal feeAmount,
    BigDecimal suspenseAmount,
    BigDecimal balanceAfter) {}
