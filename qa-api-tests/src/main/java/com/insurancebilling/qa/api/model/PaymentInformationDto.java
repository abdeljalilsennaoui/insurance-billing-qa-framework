package com.insurancebilling.qa.api.model;

/**
 * How an account is collected, as the API returns it.
 *
 * <p>Every bank field is a String because every one of them is masked. There is no unmasked variant to
 * deserialize, which is the property {@code AccountMaskingApiIT} exists to hold the platform to.
 */
public record PaymentInformationDto(
    String paymentPlan,
    String paymentMethod,
    String accountHolder,
    String institutionNumber,
    String branchNumber,
    String accountNumber) {}
