package com.insurancebilling.repository;

import com.insurancebilling.domain.BillingTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Ledger line lookups, used to find the payment a return is reversing.
 *
 * <p>Fetches the term but not its collections, for the reason {@link PolicyTermRepository} gives; the
 * service hydrates them.
 */
public interface BillingTransactionRepository extends JpaRepository<BillingTransaction, Long> {

  @EntityGraph(attributePaths = {"policyTerm", "policyTerm.policy", "policyTerm.billingAccount"})
  Optional<BillingTransaction> findByReference(String reference);
}
