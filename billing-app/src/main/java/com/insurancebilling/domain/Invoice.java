package com.insurancebilling.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A billing document raised against a policy and settled by one or more payments.
 *
 * <p>This class owns the payment rules rather than delegating them to a service. Two reasons:
 *
 * <ul>
 *   <li>The rules can be unit tested as plain Java, with no Spring context and no database, which is
 *       what keeps the domain suite fast.
 *   <li>There is no way to record a payment that skips validation. A service-layer guard can be
 *       bypassed by any other caller that reaches the entity; a guard inside {@link #applyPayment}
 *       cannot.
 * </ul>
 *
 * <p>The paid amount is always derived by summing {@link #getPayments()}, never stored, so the header
 * figure cannot drift away from the payment history.
 */
@Entity
@Table(name = "invoices")
public class Invoice {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String invoiceNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_id", nullable = false)
  private Policy policy;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(nullable = false)
  private LocalDate issueDate;

  @Column(nullable = false)
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private InvoiceStatus status = InvoiceStatus.UNPAID;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("receivedAt ASC")
  private List<Payment> payments = new ArrayList<>();

  /**
   * The scheduled installment this invoice bills, when it has one.
   *
   * <p>Null for an invoice raised directly against a policy rather than generated from a payment
   * schedule. Both kinds coexist deliberately: an insurer raises one-off documents as well as
   * scheduled ones, and every invoice that predates the schedule model is one of the former.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "installment_id")
  private Installment installment;

  protected Invoice() {
    // required by JPA
  }

  public Invoice(
      String invoiceNumber, BigDecimal totalAmount, LocalDate issueDate, LocalDate dueDate) {
    this.invoiceNumber = invoiceNumber;
    this.totalAmount = Money.normalise(totalAmount);
    this.issueDate = issueDate;
    this.dueDate = dueDate;
  }

  /**
   * Validates and records a payment against this invoice.
   *
   * <p>Rules are checked cheapest-first so that the reported reason is the most specific one
   * available: the amount is inspected before invoice state, and invoice state before the
   * overpayment comparison. A caller sending {@code -50.00} against a cancelled invoice is told the
   * amount is invalid, because that is the problem it can fix without further information.
   *
   * <p>{@code receivedAt} is a parameter for the same reason {@link #isOverdue} takes {@code asOf}:
   * the entity states the rules, the caller states the time. The application supplies it from the
   * configured business clock.
   *
   * @return the recorded payment
   * @throws PaymentRejectedException if any billing rule refuses the payment
   */
  public Payment applyPayment(
      BigDecimal amount, PaymentMethod method, String reference, Instant receivedAt) {
    if (!Money.isPositive(amount)) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.AMOUNT_NOT_POSITIVE,
          "Payment amount must be greater than zero but was " + amount.toPlainString());
    }
    if (!Money.hasValidScale(amount)) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.AMOUNT_SCALE_INVALID,
          "Payment amount must have at most "
              + Money.SCALE
              + " decimal places but was "
              + amount.toPlainString());
    }
    if (status == InvoiceStatus.CANCELLED) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.INVOICE_CANCELLED,
          "Invoice " + invoiceNumber + " is cancelled and cannot accept payments");
    }
    if (isSettled()) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.INVOICE_ALREADY_PAID,
          "Invoice " + invoiceNumber + " is already paid in full");
    }
    if (policy != null && !policy.isActive()) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.POLICY_NOT_ACTIVE,
          "Policy "
              + policy.getPolicyNumber()
              + " is "
              + policy.getStatus()
              + " and cannot be paid against");
    }
    BigDecimal normalised = Money.normalise(amount);
    if (normalised.compareTo(getOutstandingBalance()) > 0) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.EXCEEDS_OUTSTANDING_BALANCE,
          "Payment of "
              + normalised.toPlainString()
              + " exceeds the outstanding balance of "
              + getOutstandingBalance().toPlainString());
    }

    Payment payment = new Payment(this, normalised, method, reference, receivedAt);
    payments.add(payment);
    refreshStatus();
    return payment;
  }

  /** Cancels the invoice. A cancelled invoice can no longer be paid. */
  public void cancel() {
    this.status = InvoiceStatus.CANCELLED;
  }

  /** Sum of every payment recorded against this invoice. */
  public BigDecimal getAmountPaid() {
    return payments.stream()
        .map(Payment::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(Money.SCALE, java.math.RoundingMode.UNNECESSARY);
  }

  /** Amount still owed on this invoice. Never negative: overpayment is refused up front. */
  public BigDecimal getOutstandingBalance() {
    return totalAmount.subtract(getAmountPaid());
  }

  /** True when nothing is left to pay. */
  public boolean isSettled() {
    return getOutstandingBalance().compareTo(BigDecimal.ZERO) == 0;
  }

  /**
   * True when the due date has passed and the invoice is neither settled nor cancelled.
   *
   * <p>Takes the reference date as an argument rather than calling {@code LocalDate.now()} so the
   * behaviour is testable without freezing the system clock. Callers read that date from the
   * application's business clock, which is built from a configured zone rather than the host's — the
   * entity was always clean here, and DEF-012 was entirely about who supplied this argument.
   */
  public boolean isOverdue(LocalDate asOf) {
    return status != InvoiceStatus.CANCELLED && !isSettled() && dueDate.isBefore(asOf);
  }

  /**
   * Recomputes the status from the recorded payments.
   *
   * <p>{@code CANCELLED} is operator-set and is never overwritten here.
   */
  private void refreshStatus() {
    if (status == InvoiceStatus.CANCELLED) {
      return;
    }
    if (isSettled()) {
      status = InvoiceStatus.PAID;
    } else if (Money.isPositive(getAmountPaid())) {
      status = InvoiceStatus.PARTIALLY_PAID;
    } else {
      status = InvoiceStatus.UNPAID;
    }
  }

  /**
   * Promotes an untouched, past-due invoice to {@code OVERDUE}.
   *
   * <p>Only an invoice with no payments becomes {@code OVERDUE}; one that is part-paid keeps
   * {@code PARTIALLY_PAID} so that the payment progress is not hidden, and {@link #isOverdue} still
   * reports the lateness. This asymmetry is deliberate and is covered by unit tests.
   */
  public void markOverdueIfDue(LocalDate asOf) {
    if (status == InvoiceStatus.UNPAID && isOverdue(asOf)) {
      status = InvoiceStatus.OVERDUE;
    }
  }

  public Long getId() {
    return id;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public Policy getPolicy() {
    return policy;
  }

  void setPolicy(Policy policy) {
    this.policy = policy;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public LocalDate getIssueDate() {
    return issueDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public List<Payment> getPayments() {
    return Collections.unmodifiableList(payments);
  }

  /** The installment this invoice bills, or empty when it was raised directly against the policy. */
  public java.util.Optional<Installment> getInstallment() {
    return java.util.Optional.ofNullable(installment);
  }

  /** Records that this invoice bills the given scheduled installment. */
  public void billsInstallment(Installment installment) {
    this.installment = installment;
  }
}
