package com.insurancebilling.service;

import com.insurancebilling.api.dto.InvoiceRequest;
import com.insurancebilling.api.dto.PaymentRequest;
import com.insurancebilling.domain.Invoice;
import com.insurancebilling.domain.InvoiceStatus;
import com.insurancebilling.domain.Payment;
import com.insurancebilling.domain.Policy;
import com.insurancebilling.repository.InvoiceRepository;
import com.insurancebilling.repository.PolicyRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for invoices and payments.
 *
 * <p>This class handles lookup, transaction boundaries and persistence. It deliberately contains no
 * payment rules: those live on {@link Invoice#applyPayment} so they cannot be bypassed and can be unit
 * tested without Spring. The service's job is to find the invoice, hand the request to the domain, and
 * let a rejection propagate to the exception handler.
 *
 * <p>It is also where "now" enters the billing rules. The domain takes the reference date and the
 * receipt instant as arguments; this class supplies them from the injected {@link Clock}, which is
 * built from the configured business zone rather than the host's default. See
 * {@link com.insurancebilling.config.BillingTimeConfiguration} and DEF-012.
 */
@Service
@Transactional
public class InvoiceService {

  private final InvoiceRepository invoices;
  private final PolicyRepository policies;
  private final ReferenceGenerator references;
  private final Clock clock;

  public InvoiceService(
      InvoiceRepository invoices,
      PolicyRepository policies,
      ReferenceGenerator references,
      Clock clock) {
    this.invoices = invoices;
    this.policies = policies;
    this.references = references;
    this.clock = clock;
  }

  public Invoice create(InvoiceRequest request) {
    Policy policy =
        policies
            .findById(request.policyId())
            .orElseThrow(() -> new ResourceNotFoundException("Policy", request.policyId()));

    Invoice invoice =
        new Invoice(
            references.invoiceNumber(),
            request.totalAmount(),
            request.issueDate(),
            request.dueDate());
    policy.addInvoice(invoice);
    Invoice saved = invoices.save(invoice);
    saved.markOverdueIfDue(LocalDate.now(clock));
    return saved;
  }

  /**
   * Applies a payment to an invoice.
   *
   * @throws ResourceNotFoundException if the invoice does not exist
   * @throws com.insurancebilling.domain.PaymentRejectedException if a billing rule refuses the payment
   */
  public Payment pay(Long invoiceId, PaymentRequest request) {
    Invoice invoice = findById(invoiceId);
    return invoice.applyPayment(
        request.amount(), request.method(), request.reference(), clock.instant());
  }

  @Transactional(readOnly = true)
  public Invoice findById(Long id) {
    return invoices.findById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
  }

  /**
   * Lists invoices, optionally filtered by status.
   *
   * <p>Not read-only: listing promotes any untouched past-due invoice to {@code OVERDUE}, and that
   * promotion has to be flushed. A read-only transaction would compute the new status, return it, and
   * silently discard it, so the next read would report the old value.
   */
  @Transactional
  public List<Invoice> findAll(InvoiceStatus status) {
    List<Invoice> result = status == null ? invoices.findAll() : invoices.findByStatus(status);
    result.forEach(invoice -> invoice.markOverdueIfDue(LocalDate.now(clock)));
    return result;
  }

  @Transactional(readOnly = true)
  public List<Payment> findPayments(Long invoiceId) {
    return findById(invoiceId).getPayments();
  }

  /** Cancels an invoice. Used by suites that need a cancelled invoice to pay against. */
  public Invoice cancel(Long invoiceId) {
    Invoice invoice = findById(invoiceId);
    invoice.cancel();
    return invoice;
  }
}
