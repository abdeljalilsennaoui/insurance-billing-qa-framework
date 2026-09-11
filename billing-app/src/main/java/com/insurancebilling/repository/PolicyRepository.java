package com.insurancebilling.repository;

import com.insurancebilling.domain.Policy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Policy lookups.
 *
 * <p>The customer is fetched alongside the policy because every policy response carries the customer
 * name, and the Hibernate session is closed before that mapping runs. See {@link InvoiceRepository} for
 * the reasoning behind fetching explicitly.
 */
public interface PolicyRepository extends JpaRepository<Policy, Long> {

  @Override
  @EntityGraph(attributePaths = {"customer"})
  Optional<Policy> findById(Long id);

  @EntityGraph(attributePaths = {"customer"})
  Optional<Policy> findByPolicyNumber(String policyNumber);

  @EntityGraph(attributePaths = {"customer"})
  List<Policy> findByCustomerId(Long customerId);
}
