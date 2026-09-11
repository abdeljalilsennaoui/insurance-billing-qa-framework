package com.insurancebilling.repository;

import com.insurancebilling.domain.Invoice;
import com.insurancebilling.domain.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

  Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

  List<Invoice> findByStatus(InvoiceStatus status);

  List<Invoice> findByPolicyId(Long policyId);
}
