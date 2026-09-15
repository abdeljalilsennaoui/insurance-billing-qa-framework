package com.insurancebilling.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The billing relationship between the insurer and one policyholder.
 *
 * <p>An account holds what is true of the customer rather than of any one policy: how they have chosen
 * to pay, which bank account the money comes from, and how often their payments have failed. A customer
 * with three policies has one account, one payment plan and one set of counters.
 *
 * <p>The two failure counters are deliberately separate and are not the same number. Every payment a
 * bank refuses increments {@link #returnedPaymentCount}; only one refused for want of money increments
 * {@link #nsfCount}. Collapsing them would make a closed account look like a funding problem, and those
 * are handled differently by everyone downstream.
 *
 * <p>The total balance is derived from the terms billed to the account, never stored — same reasoning
 * as {@link PolicyTerm#getBalance()}.
 */
@Entity
@Table(name = "billing_accounts")
public class BillingAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String accountReference;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false, unique = true)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentPlan paymentPlan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentMethod paymentMethod;

  /** Present only for methods drawn from a bank account. See {@link BankAccountReference}. */
  @Embedded private BankAccountReference bankAccount;

  @Column(nullable = false)
  private int nsfCount;

  @Column(nullable = false)
  private int returnedPaymentCount;

  @OneToMany(mappedBy = "billingAccount", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("effectiveDate ASC, id ASC")
  private List<PolicyTerm> terms = new ArrayList<>();

  protected BillingAccount() {
    // required by JPA
  }

  public BillingAccount(
      String accountReference, PaymentPlan paymentPlan, PaymentMethod paymentMethod) {
    this.accountReference = accountReference;
    this.paymentPlan = paymentPlan;
    this.paymentMethod = paymentMethod;
  }

  /** Attaches a term to this account, wiring both sides of the relationship. */
  public void addTerm(PolicyTerm term) {
    terms.add(term);
    term.setBillingAccount(this);
  }

  public List<PolicyTerm> getTerms() {
    return Collections.unmodifiableList(terms);
  }

  /** What the policyholder owes across every term billed to this account. */
  public BigDecimal getTotalBalance() {
    return Money.sum(terms.stream().map(PolicyTerm::getBalance).toList());
  }

  /**
   * Money received that no installment has claimed, across every term.
   *
   * <p>Suspense is held as negative amounts on the ledger, because it reduces what is owed. It is
   * reported to the policyholder as a positive credit, which is why this negates it.
   */
  public BigDecimal getUnappliedAmount() {
    return Money.sum(
            terms.stream()
                .flatMap(term -> term.getTransactions().stream())
                .map(BillingTransaction::getSuspenseAmount)
                .toList())
        .negate();
  }

  /** The earliest unsettled installment across every term, which is what falls due next. */
  public Optional<Installment> nextInstallmentDue() {
    return terms.stream()
        .map(PolicyTerm::nextUnpaidInstallment)
        .flatMap(Optional::stream)
        .min((left, right) -> left.getDueDate().compareTo(right.getDueDate()));
  }

  /** The date the next payment is expected, if any term still has one scheduled. */
  public Optional<LocalDate> nextPaymentDate() {
    return nextInstallmentDue().map(Installment::getDueDate);
  }

  /** The amount expected on the next payment, if any term still has one scheduled. */
  public Optional<BigDecimal> nextPaymentAmount() {
    return nextInstallmentDue().map(Installment::getAmountDue);
  }

  /**
   * Counts a payment the bank refused.
   *
   * <p>Package-private: the counters move as part of {@link PolicyTerm#returnPayment}, which also posts
   * the reversing ledger line. Letting a caller bump a counter on its own would allow an account whose
   * NSF tally disagrees with its own ledger.
   */
  void recordReturnedPayment(ReturnReason reason) {
    returnedPaymentCount++;
    if (reason.countsAsNsf()) {
      nsfCount++;
    }
  }

  public Long getId() {
    return id;
  }

  public String getAccountReference() {
    return accountReference;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }

  public PaymentPlan getPaymentPlan() {
    return paymentPlan;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public BankAccountReference getBankAccount() {
    return bankAccount;
  }

  public void setBankAccount(BankAccountReference bankAccount) {
    this.bankAccount = bankAccount;
  }

  public int getNsfCount() {
    return nsfCount;
  }

  public int getReturnedPaymentCount() {
    return returnedPaymentCount;
  }
}
