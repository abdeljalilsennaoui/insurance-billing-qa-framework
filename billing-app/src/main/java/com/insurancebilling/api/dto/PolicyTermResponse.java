package com.insurancebilling.api.dto;

import com.insurancebilling.domain.BillingType;
import com.insurancebilling.domain.PaymentPlan;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.domain.TermStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** A policy term's header figures, without its schedule or ledger. */
public record PolicyTermResponse(
    String termReference,
    int termNumber,
    String policyNumber,
    String insuredName,
    String accountReference,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    BillingType billingType,
    TermStatus status,
    PaymentPlan paymentPlan,
    BigDecimal termPremium,
    BigDecimal termTax,
    BigDecimal installmentFee,
    BigDecimal scheduledTotal,
    BigDecimal balance,
    int installmentsRemaining) {

  public static PolicyTermResponse from(PolicyTerm term) {
    return new PolicyTermResponse(
        term.getTermReference(),
        term.getTermNumber(),
        term.getPolicy().getPolicyNumber(),
        term.getPolicy().getCustomer().getFullName(),
        term.getBillingAccount().getAccountReference(),
        term.getEffectiveDate(),
        term.getExpiryDate(),
        term.getBillingType(),
        term.getStatus(),
        term.getPaymentPlan(),
        term.getTermPremium(),
        term.getTermTax(),
        term.getInstallmentFee(),
        term.getScheduledTotal(),
        term.getBalance(),
        term.getInstallmentsRemaining());
  }
}
