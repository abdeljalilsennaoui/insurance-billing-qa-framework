package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** An invoice as the API publishes it, including the derived balance figures. */
public record InvoiceDto(
    Long id,
    String invoiceNumber,
    Long policyId,
    String policyNumber,
    String customerName,
    BigDecimal totalAmount,
    BigDecimal amountPaid,
    BigDecimal outstandingBalance,
    String status,
    boolean overdue,
    LocalDate issueDate,
    LocalDate dueDate,
    List<PaymentDto> payments) {}
