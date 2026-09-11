package com.insurancebilling.repository;

import com.insurancebilling.domain.Invoice;
import com.insurancebilling.domain.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Invoice lookups.
 *
 * <p>Every read that feeds a response is annotated with an entity graph covering the policy, its
 * customer and the payment list. The application runs with {@code spring.jpa.open-in-view=false}, so
 * the Hibernate session is closed by the time a controller maps an entity to a DTO; without these
 * graphs that mapping fails with a {@code LazyInitializationException} on the customer proxy.
 *
 * <p>Fetching explicitly here, rather than keeping the session open for the whole request, keeps the
 * query count predictable and keeps the fetching decision visible in code instead of depending on
 * request scope.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

  @Override
  @EntityGraph(attributePaths = {"policy", "policy.customer", "payments"})
  Optional<Invoice> findById(Long id);

  @Override
  @EntityGraph(attributePaths = {"policy", "policy.customer", "payments"})
  List<Invoice> findAll();

  @EntityGraph(attributePaths = {"policy", "policy.customer", "payments"})
  Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

  @EntityGraph(attributePaths = {"policy", "policy.customer", "payments"})
  List<Invoice> findByStatus(InvoiceStatus status);

  @EntityGraph(attributePaths = {"policy", "policy.customer", "payments"})
  List<Invoice> findByPolicyId(Long policyId);
}
