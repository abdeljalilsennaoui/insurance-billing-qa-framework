package com.insurancebilling.repository;

import com.insurancebilling.domain.Payment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  List<Payment> findByInvoiceIdOrderByReceivedAtAsc(Long invoiceId);
}
