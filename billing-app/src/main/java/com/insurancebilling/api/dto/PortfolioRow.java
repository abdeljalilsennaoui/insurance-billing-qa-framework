package com.insurancebilling.api.dto;

import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.domain.PolicyType;
import com.insurancebilling.domain.TermStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of the agent console's portfolio grid.
 *
 * <p>A row is a <em>term</em>, not a policy: effective date, expiry date and balance all belong to a
 * term, and a policy renewed three times has three of each. The policy number is shown because that is
 * what a caller quotes down the phone, but it is the term that the figures are about.
 *
 * <p>The status column carries the term's billing status rather than the policy's. An agent opening
 * this grid is asking which terms still owe money, and a policy can be perfectly active while the term
 * in front of them has expired.
 *
 * <p>Separate from {@link PolicyTermResponse} on purpose. That record is the REST API's payload and
 * adding grid-only columns to it would widen a published contract to suit one screen.
 */
public record PortfolioRow(
    String termReference,
    String accountReference,
    String policyNumber,
    String insuredName,
    PolicyType product,
    TermStatus status,
    LocalDate effectiveDate,
    LocalDate expiryDate,
    BigDecimal balance) {

  public static PortfolioRow from(PolicyTerm term) {
    return new PortfolioRow(
        term.getTermReference(),
        term.getBillingAccount().getAccountReference(),
        term.getPolicy().getPolicyNumber(),
        term.getPolicy().getCustomer().getFullName(),
        term.getPolicy().getType(),
        term.getStatus(),
        term.getEffectiveDate(),
        term.getExpiryDate(),
        term.getBalance());
  }
}
