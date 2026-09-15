package com.insurancebilling.repository;

import com.insurancebilling.domain.PolicyTerm;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Policy term lookups.
 *
 * <p>A term owns two list-valued collections — its schedule and its ledger — and Hibernate refuses to
 * fetch two of those in a single query. The two {@code findWith...ByIdIn} methods exist to load them in
 * one further query each, against every term at once. Called inside the same transaction, they populate
 * the very instances already loaded, so a caller reads a fully hydrated object graph after three queries
 * regardless of whether it asked for one term or fifty.
 *
 * <p>The alternative — touching each collection as it is needed — would work and is shorter, but makes
 * the query count grow with the data, which is exactly the surprise explicit fetching exists to avoid.
 */
public interface PolicyTermRepository extends JpaRepository<PolicyTerm, Long> {

  @Override
  @EntityGraph(
      attributePaths = {"policy", "policy.customer", "billingAccount", "billingAccount.customer"})
  Optional<PolicyTerm> findById(Long id);

  @Override
  @EntityGraph(
      attributePaths = {"policy", "policy.customer", "billingAccount", "billingAccount.customer"})
  List<PolicyTerm> findAll();

  @EntityGraph(
      attributePaths = {"policy", "policy.customer", "billingAccount", "billingAccount.customer"})
  Optional<PolicyTerm> findByTermReference(String termReference);

  @EntityGraph(
      attributePaths = {"policy", "policy.customer", "billingAccount", "billingAccount.customer"})
  List<PolicyTerm> findByPolicyId(Long policyId);

  /** Loads the payment schedules of the given terms. */
  @EntityGraph(attributePaths = {"installments"})
  List<PolicyTerm> findWithInstallmentsByIdIn(Collection<Long> ids);

  /** Loads the ledgers of the given terms. */
  @EntityGraph(attributePaths = {"transactions"})
  List<PolicyTerm> findWithTransactionsByIdIn(Collection<Long> ids);
}
