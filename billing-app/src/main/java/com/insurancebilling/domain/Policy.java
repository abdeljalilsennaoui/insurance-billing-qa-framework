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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * An insurance policy held by a customer.
 *
 * <p>The policy's {@link PolicyStatus} gates billing: invoices raised against a policy can only be
 * paid while the policy is {@code ACTIVE}. That rule is enforced in {@link Invoice#applyPayment} so it
 * cannot be bypassed by a caller that talks to the invoice directly.
 */
@Entity
@Table(name = "policies")
public class Policy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String policyNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PolicyType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PolicyStatus status = PolicyStatus.ACTIVE;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal annualPremium;

  @Column(nullable = false)
  private LocalDate startDate;

  @Column(nullable = false)
  private LocalDate endDate;

  @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<Invoice> invoices = new ArrayList<>();

  protected Policy() {
    // required by JPA
  }

  public Policy(
      String policyNumber,
      PolicyType type,
      BigDecimal annualPremium,
      LocalDate startDate,
      LocalDate endDate) {
    this.policyNumber = policyNumber;
    this.type = type;
    this.annualPremium = Money.normalise(annualPremium);
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public void addInvoice(Invoice invoice) {
    invoices.add(invoice);
    invoice.setPolicy(this);
  }

  /**
   * Attaches a billed term to this policy.
   *
   * <p>Unlike invoices, terms are not held in a collection here. A term is owned by the billing account
   * it is billed to, not by the policy it covers, and is persisted through that account. This method
   * only sets the back-reference so a term can name its policy.
   */
  public void attachTerm(PolicyTerm term) {
    term.setPolicy(this);
  }

  public boolean isActive() {
    return status == PolicyStatus.ACTIVE;
  }

  public Long getId() {
    return id;
  }

  public String getPolicyNumber() {
    return policyNumber;
  }

  public Customer getCustomer() {
    return customer;
  }

  void setCustomer(Customer customer) {
    this.customer = customer;
  }

  public PolicyType getType() {
    return type;
  }

  public PolicyStatus getStatus() {
    return status;
  }

  public void setStatus(PolicyStatus status) {
    this.status = status;
  }

  public BigDecimal getAnnualPremium() {
    return annualPremium;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

}
