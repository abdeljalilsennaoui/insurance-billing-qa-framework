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
import java.time.LocalDate;

/**
 * One line of a policy term's billing ledger.
 *
 * <p>Every entry splits its amount across the same four columns: premium, tax, fee and suspense. The
 * total is <em>derived</em> from those four by {@link #getAmount()} rather than stored beside them, so
 * a line whose columns do not add up to its total is not something this class can represent. The
 * ledger's arithmetic therefore reconciles column by column and in total at the same time, which is
 * what makes a running balance worth trusting.
 *
 * <p>Suspense is money received that no installment claimed — an overpayment, or an amount that fell
 * short of the next installment due. It sits on the account rather than against a charge until
 * something applies it.
 *
 * <p>Entries are append-only. Nothing corrects a ledger line in place; a correction is another line.
 * That is why {@link #reversalOf} exists rather than a mutable {@code reversed} flag.
 */
@Entity
@Table(name = "billing_transactions")
public class BillingTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String reference;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_term_id", nullable = false)
  private PolicyTerm policyTerm;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionType type;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private LocalDate effectiveDate;

  @Column(nullable = false)
  private Instant processedAt;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal premiumAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal taxAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal feeAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal suspenseAmount;

  /** The payment entry this one reverses, when it is a return. Null on every other type. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "reversal_of_id")
  private BillingTransaction reversalOf;

  protected BillingTransaction() {
    // required by JPA
  }

  BillingTransaction(
      String reference,
      TransactionType type,
      String description,
      LocalDate effectiveDate,
      Instant processedAt,
      BigDecimal premiumAmount,
      BigDecimal taxAmount,
      BigDecimal feeAmount,
      BigDecimal suspenseAmount) {
    this.reference = reference;
    this.type = type;
    this.description = description;
    this.effectiveDate = effectiveDate;
    this.processedAt = processedAt;
    this.premiumAmount = Money.normalise(premiumAmount);
    this.taxAmount = Money.normalise(taxAmount);
    this.feeAmount = Money.normalise(feeAmount);
    this.suspenseAmount = Money.normalise(suspenseAmount);
  }

  /**
   * What this line does to the balance: the sum of its four columns.
   *
   * <p>Positive increases what is owed, negative reduces it. See {@link TransactionType} for the sign
   * each type carries.
   */
  public BigDecimal getAmount() {
    return premiumAmount.add(taxAmount).add(feeAmount).add(suspenseAmount);
  }

  public Long getId() {
    return id;
  }

  public String getReference() {
    return reference;
  }

  public PolicyTerm getPolicyTerm() {
    return policyTerm;
  }

  void setPolicyTerm(PolicyTerm policyTerm) {
    this.policyTerm = policyTerm;
  }

  public TransactionType getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public BigDecimal getPremiumAmount() {
    return premiumAmount;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getFeeAmount() {
    return feeAmount;
  }

  public BigDecimal getSuspenseAmount() {
    return suspenseAmount;
  }

  public BillingTransaction getReversalOf() {
    return reversalOf;
  }

  void setReversalOf(BillingTransaction reversalOf) {
    this.reversalOf = reversalOf;
  }
}
