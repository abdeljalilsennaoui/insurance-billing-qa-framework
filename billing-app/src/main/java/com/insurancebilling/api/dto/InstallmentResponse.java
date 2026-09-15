package com.insurancebilling.api.dto;

import com.insurancebilling.domain.Installment;
import com.insurancebilling.domain.InstallmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of a payment schedule.
 *
 * <p>Carries the premium, tax and fee split as well as the total, so a caller can reconcile the
 * schedule column by column without re-deriving it.
 */
public record InstallmentResponse(
    String reference,
    int sequenceNumber,
    LocalDate scheduledDate,
    LocalDate dueDate,
    BigDecimal premiumAmount,
    BigDecimal taxAmount,
    BigDecimal feeAmount,
    BigDecimal amountDue,
    InstallmentStatus status) {

  public static InstallmentResponse from(Installment installment) {
    return new InstallmentResponse(
        installment.getReference(),
        installment.getSequenceNumber(),
        installment.getScheduledDate(),
        installment.getDueDate(),
        installment.getPremiumAmount(),
        installment.getTaxAmount(),
        installment.getFeeAmount(),
        installment.getAmountDue(),
        installment.getStatus());
  }
}
