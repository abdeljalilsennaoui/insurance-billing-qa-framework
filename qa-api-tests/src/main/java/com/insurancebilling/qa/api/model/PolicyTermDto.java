package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A policy term's header figures.
 *
 * <p>{@code billingType}, {@code status} and {@code paymentPlan} are Strings rather than shared enums,
 * for the reason {@link PolicyDto} gives: importing the application's enum would make a renamed constant
 * change the test and the application together, and the suite would stay green while the published
 * contract broke.
 */
public record PolicyTermDto(
    String termReference,
    int termNumber,
    String policyNumber,
    String insuredName,
    String accountReference,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    String billingType,
    String status,
    String paymentPlan,
    BigDecimal termPremium,
    BigDecimal termTax,
    BigDecimal installmentFee,
    BigDecimal scheduledTotal,
    BigDecimal balance,
    int installmentsRemaining) {}
