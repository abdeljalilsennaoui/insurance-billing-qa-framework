package com.insurancebilling.repository;

import com.insurancebilling.domain.Policy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

  Optional<Policy> findByPolicyNumber(String policyNumber);

  List<Policy> findByCustomerId(Long customerId);
}
