package com.insurancebilling.repository;

import com.insurancebilling.domain.BillingAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Billing account lookups.
 *
 * <p>The graph reaches the customer and the terms, and each term's policy and insured — but stops
 * short of the schedules and ledgers. Hibernate cannot fetch two list-valued collections in one query,
 * and a term has two of them; asking for both raises {@code MultipleBagFetchException} at runtime.
 * {@link PolicyTermRepository} loads them in a second and third query instead, which keeps the total
 * query count flat rather than growing with the number of terms.
 *
 * <p>The application runs with {@code spring.jpa.open-in-view=false}, so anything not fetched by the
 * time the transaction ends is unavailable when a controller maps to a DTO.
 */
public interface BillingAccountRepository extends JpaRepository<BillingAccount, Long> {

  @Override
  @EntityGraph(attributePaths = {"customer", "terms", "terms.policy", "terms.policy.customer"})
  Optional<BillingAccount> findById(Long id);

  @Override
  @EntityGraph(attributePaths = {"customer", "terms", "terms.policy", "terms.policy.customer"})
  List<BillingAccount> findAll();

  @EntityGraph(attributePaths = {"customer", "terms", "terms.policy", "terms.policy.customer"})
  Optional<BillingAccount> findByAccountReference(String accountReference);

  @EntityGraph(attributePaths = {"customer", "terms", "terms.policy", "terms.policy.customer"})
  Optional<BillingAccount> findByCustomerId(Long customerId);
}
