package com.insurancebilling.qa.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A policy as the API publishes it.
 *
 * <p>{@code type} and {@code status} are Strings rather than enums shared with the application. The
 * automation modules deliberately have no dependency on {@code billing-app}: if both sides shared an
 * enum, renaming a constant would change the test and the application together and the suite would
 * still pass while the published contract had silently broken.
 */
public record PolicyDto(
    Long id,
    String policyNumber,
    Long customerId,
    String customerName,
    String type,
    String status,
    BigDecimal annualPremium,
    LocalDate startDate,
    LocalDate endDate) {}
