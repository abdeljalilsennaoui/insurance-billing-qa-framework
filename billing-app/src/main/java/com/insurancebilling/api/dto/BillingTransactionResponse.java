package com.insurancebilling.api.dto;

import com.insurancebilling.domain.BillingTransaction;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.domain.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One line of a billing ledger, with the balance as it stood after it.
 *
 * <p>{@code balanceAfter} is asked of the term rather than read from a column on the transaction,
 * because the term derives it from the lines above. A caller adding up {@code amount} down the list
 * will arrive at the same figures.
 */
public record BillingTransactionResponse(
    String reference,
    TransactionType type,
    String description,
    LocalDate effectiveDate,
    Instant processedAt,
    BigDecimal amount,
    BigDecimal premiumAmount,
    BigDecimal taxAmount,
    BigDecimal feeAmount,
    BigDecimal suspenseAmount,
    BigDecimal balanceAfter) {

  public static BillingTransactionResponse from(BillingTransaction transaction, PolicyTerm term) {
    return new BillingTransactionResponse(
        transaction.getReference(),
        transaction.getType(),
        transaction.getDescription(),
        transaction.getEffectiveDate(),
        transaction.getProcessedAt(),
        transaction.getAmount(),
        transaction.getPremiumAmount(),
        transaction.getTaxAmount(),
        transaction.getFeeAmount(),
        transaction.getSuspenseAmount(),
        term.balanceAfter(transaction));
  }
}
