package com.insurancebilling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single payment recorded against an invoice.
 *
 * <p>Payments are append-only: an invoice's paid amount is the sum of its payments, never a stored
 * running total that could drift out of step with the payment history.
 */
@Entity
@Table(name = "payments")
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentMethod method;

  @Column(nullable = false)
  private Instant receivedAt;

  @Column
  private String reference;

  protected Payment() {
    // required by JPA
  }

  Payment(Invoice invoice, BigDecimal amount, PaymentMethod method, String reference) {
    this.invoice = invoice;
    this.amount = Money.normalise(amount);
    this.method = method;
    this.reference = reference;
    this.receivedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Invoice getInvoice() {
    return invoice;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getReference() {
    return reference;
  }
}
