package com.insurancebilling.service;

import com.insurancebilling.api.dto.CustomerRequest;
import com.insurancebilling.domain.Customer;
import com.insurancebilling.repository.CustomerRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerService {

  private final CustomerRepository customers;

  public CustomerService(CustomerRepository customers) {
    this.customers = customers;
  }

  public Customer create(CustomerRequest request) {
    if (customers.existsByEmail(request.email())) {
      throw new DuplicateEmailException(request.email());
    }
    return customers.save(
        new Customer(request.firstName(), request.lastName(), request.email()));
  }

  @Transactional(readOnly = true)
  public List<Customer> findAll() {
    return customers.findAll();
  }

  @Transactional(readOnly = true)
  public Customer findById(Long id) {
    return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
  }
}
