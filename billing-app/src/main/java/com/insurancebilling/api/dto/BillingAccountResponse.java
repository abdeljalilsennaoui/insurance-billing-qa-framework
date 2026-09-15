package com.insurancebilling.api.dto;

import com.insurancebilling.domain.BillingAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The account summary: what is owed, what falls due next, and how it will be collected. */
public record BillingAccountResponse(
    String accountReference,
    Long customerId,
    String customerName,
    BigDecimal totalBalance,
    BigDecimal unappliedAmount,
    LocalDate nextPaymentDate,
    BigDecimal nextPaymentAmount,
    int nsfCount,
    int returnedPaymentCount,
    PaymentInformationResponse paymentInformation,
    List<PolicyTermResponse> terms) {

  public static BillingAccountResponse from(BillingAccount account) {
    return new BillingAccountResponse(
        account.getAccountReference(),
        account.getCustomer().getId(),
        account.getCustomer().getFullName(),
        account.getTotalBalance(),
        account.getUnappliedAmount(),
        account.nextPaymentDate().orElse(null),
        account.nextPaymentAmount().orElse(null),
        account.getNsfCount(),
        account.getReturnedPaymentCount(),
        PaymentInformationResponse.from(account),
        account.getTerms().stream().map(PolicyTermResponse::from).toList());
  }
}
