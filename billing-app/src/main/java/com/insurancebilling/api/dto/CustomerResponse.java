package com.insurancebilling.api.dto;

import com.insurancebilling.domain.Customer;

/** Outbound representation of a customer. */
public record CustomerResponse(Long id, String firstName, String lastName, String email) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(
        customer.getId(), customer.getFirstName(), customer.getLastName(), customer.getEmail());
  }
}
