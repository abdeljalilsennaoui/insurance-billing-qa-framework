package com.insurancebilling.api.dto;

import com.insurancebilling.domain.BankAccountReference;
import com.insurancebilling.domain.BillingAccount;
import com.insurancebilling.domain.PaymentMethod;
import com.insurancebilling.domain.PaymentPlan;

/**
 * How an account is collected, as it may be shown to a caller.
 *
 * <p>The bank fields are the masked forms and there is no unmasked variant, because
 * {@link BankAccountReference} holds nothing to unmask. That is a property of the domain rather than of
 * this record: a future response carrying bank details has nowhere to get an unmasked number from
 * either.
 */
public record PaymentInformationResponse(
    PaymentPlan paymentPlan,
    PaymentMethod paymentMethod,
    String accountHolder,
    String institutionNumber,
    String branchNumber,
    String accountNumber) {

  public static PaymentInformationResponse from(BillingAccount account) {
    BankAccountReference bank = account.getBankAccount();
    if (bank == null) {
      return new PaymentInformationResponse(
          account.getPaymentPlan(), account.getPaymentMethod(), null, null, null, null);
    }
    return new PaymentInformationResponse(
        account.getPaymentPlan(),
        account.getPaymentMethod(),
        bank.getAccountHolder(),
        bank.getMaskedInstitutionNumber(),
        bank.getMaskedBranchNumber(),
        bank.getMaskedAccountNumber());
  }
}
