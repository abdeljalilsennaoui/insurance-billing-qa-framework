package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The account summary as the API returns it. */
public record BillingAccountDto(
    String accountReference,
    Long customerId,
    String customerName,
    BigDecimal totalBalance,
    BigDecimal unappliedAmount,
    LocalDate nextPaymentDate,
    BigDecimal nextPaymentAmount,
    int nsfCount,
    int returnedPaymentCount,
    PaymentInformationDto paymentInformation,
    List<PolicyTermDto> terms) {}
