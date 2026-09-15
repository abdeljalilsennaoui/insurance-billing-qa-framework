package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One row of a payment schedule. */
public record InstallmentDto(
    String reference,
    int sequenceNumber,
    LocalDate scheduledDate,
    LocalDate dueDate,
    BigDecimal premiumAmount,
    BigDecimal taxAmount,
    BigDecimal feeAmount,
    BigDecimal amountDue,
    String status) {}
