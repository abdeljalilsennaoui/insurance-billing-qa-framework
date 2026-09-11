package com.insurancebilling.api.dto;

import com.insurancebilling.domain.PaymentMethod;

/**
 * Backing object for the web payment form.
 *
 * <p>The amount is carried as a {@code String} rather than a {@code BigDecimal} on purpose. If the
 * field were typed, Spring's own binder would reject "abc" before the application saw it, producing a
 * generic binding failure and a page with no specific message for the user. Parsing it here lets the
 * console show one deliberate message per problem, which is also what makes the UI error assertions
 * stable.
 */
public class PaymentForm {

  private String amount;
  private PaymentMethod method = PaymentMethod.CARD;
  private String reference;

  public String getAmount() {
    return amount;
  }

  public void setAmount(String amount) {
    this.amount = amount;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public void setMethod(PaymentMethod method) {
    this.method = method;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }
}
