package com.insurancebilling.service;

import com.insurancebilling.api.dto.PolicyRequest;
import com.insurancebilling.domain.Customer;
import com.insurancebilling.domain.Policy;
import com.insurancebilling.domain.PolicyStatus;
import com.insurancebilling.repository.CustomerRepository;
import com.insurancebilling.repository.PolicyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PolicyService {

  private final PolicyRepository policies;
  private final CustomerRepository customers;
  private final ReferenceGenerator references;

  public PolicyService(
      PolicyRepository policies, CustomerRepository customers, ReferenceGenerator references) {
    this.policies = policies;
    this.customers = customers;
    this.references = references;
  }

  public Policy create(PolicyRequest request) {
    Customer customer =
        customers
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    Policy policy =
        new Policy(
            references.policyNumber(),
            request.type(),
            request.annualPremium(),
            request.startDate(),
            request.endDate());
    customer.addPolicy(policy);
    return policies.save(policy);
  }

  /** Changes a policy's lifecycle state. Used by suites that need a lapsed or cancelled policy. */
  public Policy updateStatus(Long policyId, PolicyStatus status) {
    Policy policy = findById(policyId);
    policy.setStatus(status);
    return policy;
  }

  @Transactional(readOnly = true)
  public Policy findById(Long id) {
    return policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Policy", id));
  }

  @Transactional(readOnly = true)
  public List<Policy> findByCustomer(Long customerId) {
    if (!customers.existsById(customerId)) {
      throw new ResourceNotFoundException("Customer", customerId);
    }
    return policies.findByCustomerId(customerId);
  }
}
