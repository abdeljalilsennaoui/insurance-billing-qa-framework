package com.insurancebilling.api.dto;

import com.insurancebilling.domain.Invoice;
import com.insurancebilling.domain.InvoiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outbound representation of an invoice.
 *
 * <p>Includes the derived amount paid and outstanding balance so a client never has to sum the
 * payment list itself, and an explicit {@code overdue} flag so the overdue rule is asserted from one
 * place rather than recomputed by every consumer.
 */
public record InvoiceResponse(
    Long id,
    String invoiceNumber,
    Long policyId,
    String policyNumber,
    String customerName,
    BigDecimal totalAmount,
    BigDecimal amountPaid,
    BigDecimal outstandingBalance,
    InvoiceStatus status,
    boolean overdue,
    LocalDate issueDate,
    LocalDate dueDate,
    List<PaymentResponse> payments) {

  public static InvoiceResponse from(Invoice invoice, LocalDate asOf) {
    return new InvoiceResponse(
        invoice.getId(),
        invoice.getInvoiceNumber(),
        invoice.getPolicy().getId(),
        invoice.getPolicy().getPolicyNumber(),
        invoice.getPolicy().getCustomer().getFullName(),
        invoice.getTotalAmount(),
        invoice.getAmountPaid(),
        invoice.getOutstandingBalance(),
        invoice.getStatus(),
        invoice.isOverdue(asOf),
        invoice.getIssueDate(),
        invoice.getDueDate(),
        invoice.getPayments().stream().map(PaymentResponse::from).toList());
  }
}
