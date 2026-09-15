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
import java.util.Optional;

/**
 * One billed period of a policy — its schedule, its ledger, and the rules that move money on it.
 *
 * <p>This is where the billing arithmetic lives, for the same two reasons {@link Invoice} owns the
 * payment rules: it can be unit tested as plain Java with no Spring context and no database, and there
 * is no path to the ledger that bypasses it.
 *
 * <p><strong>The balance is derived, never stored.</strong> {@link #getBalance()} is the ordered sum of
 * every ledger line, and {@link #balanceAfter} is the same sum truncated at a given line. Nothing
 * caches it. A stored balance is a second source of truth that drifts from the first the moment any
 * code path forgets to update it, and a billing platform whose header figure disagrees with its own
 * transaction history is worse than one with no header figure at all.
 *
 * <p>The whole term's premium and tax are posted once, at new business. The installment schedule is a
 * plan for collecting that balance, not a series of further charges — which is why twelve scheduled
 * installments do not appear on the ledger until they are paid.
 */
@Entity
@Table(name = "policy_terms")
public class PolicyTerm {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String termReference;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "policy_id", nullable = false)
  private Policy policy;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "billing_account_id", nullable = false)
  private BillingAccount billingAccount;

  @Column(nullable = false)
  private int termNumber;

  @Column(nullable = false)
  private LocalDate effectiveDate;

  @Column(nullable = false)
  private LocalDate expiryDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BillingType billingType = BillingType.DIRECT_BILL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TermStatus status = TermStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentPlan paymentPlan;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal termPremium;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal termTax;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal installmentFee;

  @OneToMany(mappedBy = "policyTerm", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sequenceNumber ASC")
  private List<Installment> installments = new ArrayList<>();

  @OneToMany(mappedBy = "policyTerm", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("processedAt ASC, id ASC")
  private List<BillingTransaction> transactions = new ArrayList<>();

  protected PolicyTerm() {
    // required by JPA
  }

  public PolicyTerm(
      String termReference,
      int termNumber,
      LocalDate effectiveDate,
      LocalDate expiryDate,
      PaymentPlan paymentPlan,
      BigDecimal termPremium,
      BigDecimal termTax,
      BigDecimal installmentFee) {
    if (expiryDate.isBefore(effectiveDate)) {
      throw new IllegalArgumentException(
          "A term cannot expire (" + expiryDate + ") before it takes effect (" + effectiveDate + ")");
    }
    this.termReference = termReference;
    this.termNumber = termNumber;
    this.effectiveDate = effectiveDate;
    this.expiryDate = expiryDate;
    this.paymentPlan = paymentPlan;
    this.termPremium = Money.normalise(termPremium);
    this.termTax = Money.normalise(termTax);
    this.installmentFee = Money.normalise(installmentFee);
  }

  // ---------------------------------------------------------------- schedule

  /** Attaches a scheduled installment, wiring both sides of the relationship. */
  public void addInstallment(Installment installment) {
    installments.add(installment);
    installment.setPolicyTerm(this);
  }

  public List<Installment> getInstallments() {
    return Collections.unmodifiableList(installments);
  }

  /** The next installment still to be settled, in schedule order. */
  public Optional<Installment> nextUnpaidInstallment() {
    return installments.stream().filter(Installment::isOutstanding).findFirst();
  }

  /** How many installments on this schedule are still to be settled. */
  public int getInstallmentsRemaining() {
    return (int) installments.stream().filter(Installment::isOutstanding).count();
  }

  /** Re-derives every installment's status for the given date. */
  public void ageScheduleAsOf(LocalDate asOf) {
    installments.forEach(installment -> installment.ageAsOf(asOf));
  }

  /**
   * What the schedule intends to collect in total.
   *
   * <p>Equal to premium plus tax plus every installment fee. An installment schedule that does not sum
   * to this is a defect, and is asserted as such rather than assumed.
   */
  public BigDecimal getScheduledTotal() {
    return Money.sum(installments.stream().map(Installment::getAmountDue).toList());
  }

  // ------------------------------------------------------------------ ledger

  public List<BillingTransaction> getTransactions() {
    return Collections.unmodifiableList(transactions);
  }

  /** What is owed on this term: the ordered sum of every ledger line. Never stored. */
  public BigDecimal getBalance() {
    return Money.sum(transactions.stream().map(BillingTransaction::getAmount).toList());
  }

  /**
   * The balance as it stood immediately after the given line was posted.
   *
   * <p>Derived by summing the ledger up to and including that line rather than read from a column on
   * it, so a displayed running balance cannot disagree with the lines above it.
   */
  public BigDecimal balanceAfter(BillingTransaction transaction) {
    List<BigDecimal> upToAndIncluding = new ArrayList<>();
    for (BillingTransaction entry : transactions) {
      upToAndIncluding.add(entry.getAmount());
      if (entry == transaction) {
        return Money.sum(upToAndIncluding);
      }
    }
    throw new IllegalArgumentException(
        "Transaction " + transaction.getReference() + " is not on term " + termReference);
  }

  /** Appends a ledger line. The only way one is created. */
  private BillingTransaction post(
      String reference,
      TransactionType type,
      String description,
      LocalDate effectiveDate,
      Instant processedAt,
      BigDecimal premium,
      BigDecimal tax,
      BigDecimal fee,
      BigDecimal suspense) {
    BillingTransaction transaction =
        new BillingTransaction(
            reference, type, description, effectiveDate, processedAt, premium, tax, fee, suspense);
    transaction.setPolicyTerm(this);
    transactions.add(transaction);
    return transaction;
  }

  /**
   * Posts the term's whole premium and tax, and puts the term in force.
   *
   * @throws IllegalStateException if the term has already been bound
   */
  public BillingTransaction postNewBusiness(
      String reference, String description, LocalDate effectiveDate, Instant processedAt) {
    if (!transactions.isEmpty()) {
      throw new IllegalStateException(
          "Term " + termReference + " has already been bound and cannot post new business twice");
    }
    BigDecimal fees = Money.sum(installments.stream().map(Installment::getFeeAmount).toList());
    BillingTransaction posted =
        post(
            reference,
            TransactionType.NEW_BUSINESS,
            description,
            effectiveDate,
            processedAt,
            termPremium,
            termTax,
            fees,
            Money.ZERO);
    this.status = TermStatus.IN_FORCE;
    return posted;
  }

  /**
   * Records money received against this term.
   *
   * <p>The payment settles outstanding installments in schedule order — oldest first — while enough of
   * it remains to cover one whole. Whatever is left over is suspense: money on the account that no
   * charge has claimed. Splitting it that way is what lets the ledger's premium and tax columns each
   * reconcile on their own, because a settled installment contributes exactly its own split.
   *
   * <p>Guards run cheapest-first so the reported reason is the most specific one available, matching
   * {@link Invoice#applyPayment}.
   *
   * @return the ledger line that was posted
   * @throws PaymentRejectedException if any billing rule refuses the payment
   */
  public BillingTransaction recordPayment(
      String reference,
      BigDecimal amount,
      String description,
      LocalDate effectiveDate,
      Instant processedAt) {
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
    if (status != TermStatus.IN_FORCE) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.TERM_NOT_IN_FORCE,
          "Term " + termReference + " is " + status + " and cannot accept payments");
    }
    BigDecimal normalised = Money.normalise(amount);
    if (normalised.compareTo(getBalance()) > 0) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.EXCEEDS_OUTSTANDING_BALANCE,
          "Payment of "
              + normalised.toPlainString()
              + " exceeds the outstanding balance of "
              + getBalance().toPlainString());
    }

    List<Installment> settled = new ArrayList<>();
    BigDecimal premium = Money.ZERO;
    BigDecimal tax = Money.ZERO;
    BigDecimal fee = Money.ZERO;
    BigDecimal remaining = normalised;

    for (Installment installment : installments) {
      if (!installment.isOutstanding()) {
        continue;
      }
      if (remaining.compareTo(installment.getAmountDue()) < 0) {
        break;
      }
      settled.add(installment);
      premium = premium.add(installment.getPremiumAmount());
      tax = tax.add(installment.getTaxAmount());
      fee = fee.add(installment.getFeeAmount());
      remaining = remaining.subtract(installment.getAmountDue());
    }

    BillingTransaction payment =
        post(
            reference,
            TransactionType.PAYMENT,
            description,
            effectiveDate,
            processedAt,
            premium.negate(),
            tax.negate(),
            fee.negate(),
            remaining.negate());
    settled.forEach(installment -> installment.settleWith(payment));
    return payment;
  }

  /**
   * Reverses a payment the bank did not honour, and charges for the trouble.
   *
   * <p>Three things happen together, which is exactly why they belong in one method: the ledger gets a
   * reversing line that restores the balance, every installment that payment settled goes back to
   * unsettled, and the account's counters move. A caller that could do one without the others would
   * eventually do one without the others.
   *
   * <p>The reversal negates the original line column for column rather than recomputing it, so a
   * returned payment restores the balance to the cent no matter what it had settled.
   *
   * @param nsfFee the charge to raise, or {@link Money#ZERO} to waive it
   * @return the reversing ledger line
   * @throws PaymentRejectedException if the line is not a payment on this term, or was already returned
   */
  public BillingTransaction returnPayment(
      String reversalReference,
      String feeReference,
      BillingTransaction payment,
      ReturnReason reason,
      BigDecimal nsfFee,
      LocalDate effectiveDate,
      Instant processedAt) {
    if (payment.getType() != TransactionType.PAYMENT || !transactions.contains(payment)) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.PAYMENT_ALREADY_RETURNED,
          "Transaction " + payment.getReference() + " is not a payment on term " + termReference);
    }
    boolean alreadyReturned =
        transactions.stream().anyMatch(entry -> entry.getReversalOf() == payment);
    if (alreadyReturned) {
      throw new PaymentRejectedException(
          PaymentRejectionReason.PAYMENT_ALREADY_RETURNED,
          "Payment " + payment.getReference() + " has already been returned");
    }

    BillingTransaction reversal =
        post(
            reversalReference,
            TransactionType.PAYMENT_RETURNED,
            "Returned payment - " + reason,
            effectiveDate,
            processedAt,
            payment.getPremiumAmount().negate(),
            payment.getTaxAmount().negate(),
            payment.getFeeAmount().negate(),
            payment.getSuspenseAmount().negate());
    reversal.setReversalOf(payment);

    installments.stream()
        .filter(installment -> installment.getSettledBy() == payment)
        .forEach(Installment::reverseSettlement);

    if (Money.isPositive(nsfFee)) {
      post(
          feeReference,
          TransactionType.NSF_FEE,
          "Returned payment fee",
          effectiveDate,
          processedAt,
          Money.ZERO,
          Money.ZERO,
          Money.normalise(nsfFee),
          Money.ZERO);
    }

    billingAccount.recordReturnedPayment(reason);
    return reversal;
  }

  // ----------------------------------------------------------------- getters

  public Long getId() {
    return id;
  }

  public String getTermReference() {
    return termReference;
  }

  public Policy getPolicy() {
    return policy;
  }

  void setPolicy(Policy policy) {
    this.policy = policy;
  }

  public BillingAccount getBillingAccount() {
    return billingAccount;
  }

  void setBillingAccount(BillingAccount billingAccount) {
    this.billingAccount = billingAccount;
  }

  public int getTermNumber() {
    return termNumber;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }

  public LocalDate getExpiryDate() {
    return expiryDate;
  }

  public BillingType getBillingType() {
    return billingType;
  }

  public void setBillingType(BillingType billingType) {
    this.billingType = billingType;
  }

  public TermStatus getStatus() {
    return status;
  }

  public void setStatus(TermStatus status) {
    this.status = status;
  }

  public PaymentPlan getPaymentPlan() {
    return paymentPlan;
  }

  public BigDecimal getTermPremium() {
    return termPremium;
  }

  public BigDecimal getTermTax() {
    return termTax;
  }

  public BigDecimal getInstallmentFee() {
    return installmentFee;
  }
}
