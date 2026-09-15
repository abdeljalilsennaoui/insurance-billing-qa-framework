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
import java.time.LocalDate;

/**
 * One scheduled collection on a policy term's payment schedule.
 *
 * <p>An installment is a <em>plan</em>, not a charge. The term's whole premium and tax are posted to
 * the ledger when the policy is bound; the schedule only says when the insurer intends to collect it.
 * That is why settling an installment does not reduce the balance on its own — the payment that
 * settles it does.
 *
 * <p>Two dates, and they are not the same date. {@link #scheduledDate} is when the insurer draws the
 * money or issues the notice; {@link #dueDate} is the date by which it must have arrived. The gap is
 * the policyholder's grace period, and an installment is only late once the due date has passed.
 */
@Entity
@Table(name = "installments")
public class Installment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String reference;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_term_id", nullable = false)
  private PolicyTerm policyTerm;

  @Column(nullable = false)
  private int sequenceNumber;

  @Column(nullable = false)
  private LocalDate scheduledDate;

  @Column(nullable = false)
  private LocalDate dueDate;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal premiumAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal taxAmount;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal feeAmount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InstallmentStatus status = InstallmentStatus.SCHEDULED;

  /** The ledger entry that settled this installment, if one has. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "settled_by_id")
  private BillingTransaction settledBy;

  protected Installment() {
    // required by JPA
  }

  public Installment(
      String reference,
      int sequenceNumber,
      LocalDate scheduledDate,
      LocalDate dueDate,
      BigDecimal premiumAmount,
      BigDecimal taxAmount,
      BigDecimal feeAmount) {
    if (sequenceNumber < 1) {
      throw new IllegalArgumentException("Installments are numbered from one, not " + sequenceNumber);
    }
    if (dueDate.isBefore(scheduledDate)) {
      throw new IllegalArgumentException(
          "An installment cannot be due (" + dueDate + ") before it is scheduled (" + scheduledDate + ")");
    }
    this.reference = reference;
    this.sequenceNumber = sequenceNumber;
    this.scheduledDate = scheduledDate;
    this.dueDate = dueDate;
    this.premiumAmount = Money.normalise(premiumAmount);
    this.taxAmount = Money.normalise(taxAmount);
    this.feeAmount = Money.normalise(feeAmount);
  }

  /** What the insurer intends to collect on this installment: premium plus tax plus fee. */
  public BigDecimal getAmountDue() {
    return premiumAmount.add(taxAmount).add(feeAmount);
  }

  /** True when this installment still needs settling. */
  public boolean isOutstanding() {
    return status != InstallmentStatus.PAID;
  }

  /**
   * Marks this installment settled by the given ledger entry.
   *
   * @throws PaymentRejectedException if it is already paid
   */
  void settleWith(BillingTransaction payment) {
    if (status == InstallmentStatus.PAID) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.INSTALLMENT_ALREADY_PAID,
          "Installment " + sequenceNumber + " of " + reference + " is already paid");
    }
    this.status = InstallmentStatus.PAID;
    this.settledBy = payment;
  }

  /** Undoes {@link #settleWith} after the settling payment was returned unpaid. */
  void reverseSettlement() {
    this.status = InstallmentStatus.REVERSED;
    this.settledBy = null;
  }

  /**
   * Advances the status for the passage of time.
   *
   * <p>Takes the reference date as an argument rather than reading a clock, for the same reason
   * {@link Invoice#isOverdue} does: the entity states the rule and the caller states the time.
   *
   * <p>A settled installment is never re-aged, and a reversed one stays reversed — its history is the
   * point of it.
   */
  public void ageAsOf(LocalDate asOf) {
    if (status == InstallmentStatus.PAID || status == InstallmentStatus.REVERSED) {
      return;
    }
    if (dueDate.isBefore(asOf)) {
      status = InstallmentStatus.OVERDUE;
    } else if (!scheduledDate.isAfter(asOf)) {
      status = InstallmentStatus.BILLED;
    } else {
      status = InstallmentStatus.SCHEDULED;
    }
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

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public LocalDate getScheduledDate() {
    return scheduledDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
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

  public InstallmentStatus getStatus() {
    return status;
  }

  public BillingTransaction getSettledBy() {
    return settledBy;
  }
}
