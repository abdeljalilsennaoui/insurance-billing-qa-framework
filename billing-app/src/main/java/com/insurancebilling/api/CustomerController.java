package com.insurancebilling.api;

import com.insurancebilling.api.dto.CustomerRequest;
import com.insurancebilling.api.dto.CustomerResponse;
import com.insurancebilling.api.dto.PolicyResponse;
import com.insurancebilling.service.CustomerService;
import com.insurancebilling.service.PolicyService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customers;
  private final PolicyService policies;

  public CustomerController(CustomerService customers, PolicyService policies) {
    this.customers = customers;
    this.policies = policies;
  }

  @PostMapping
  public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
    CustomerResponse created = CustomerResponse.from(customers.create(request));
    return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
  }

  @GetMapping
  public List<CustomerResponse> list() {
    return customers.findAll().stream().map(CustomerResponse::from).toList();
  }

  @GetMapping("/{id}")
  public CustomerResponse get(@PathVariable Long id) {
    return CustomerResponse.from(customers.findById(id));
  }

  @GetMapping("/{id}/policies")
  public List<PolicyResponse> policies(@PathVariable Long id) {
    return policies.findByCustomer(id).stream().map(PolicyResponse::from).toList();
  }
}
