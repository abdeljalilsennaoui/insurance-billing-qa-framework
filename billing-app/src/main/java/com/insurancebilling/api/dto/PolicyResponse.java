package com.insurancebilling.api.dto;

import com.insurancebilling.domain.Policy;
import com.insurancebilling.domain.PolicyStatus;
import com.insurancebilling.domain.PolicyType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Outbound representation of a policy. */
public record PolicyResponse(
    Long id,
    String policyNumber,
    Long customerId,
    String customerName,
    PolicyType type,
    PolicyStatus status,
    BigDecimal annualPremium,
    LocalDate startDate,
    LocalDate endDate) {

  public static PolicyResponse from(Policy policy) {
    return new PolicyResponse(
        policy.getId(),
        policy.getPolicyNumber(),
        policy.getCustomer().getId(),
        policy.getCustomer().getFullName(),
        policy.getType(),
        policy.getStatus(),
        policy.getAnnualPremium(),
        policy.getStartDate(),
        policy.getEndDate());
  }
}
