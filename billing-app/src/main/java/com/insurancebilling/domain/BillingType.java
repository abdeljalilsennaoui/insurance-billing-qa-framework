package com.insurancebilling.domain;

/**
 * Who the insurer bills for a policy term.
 *
 * <p>{@code DIRECT_BILL} sends the schedule to the policyholder, which is the case every screen in
 * this application shows. {@code AGENCY_BILL} bills the broker, who collects from the policyholder
 * separately — the insurer's ledger still exists but the installments are not the policyholder's to
 * pay. The distinction is modelled because it changes who a refused payment should be reported to.
 */
public enum BillingType {
  DIRECT_BILL,
  AGENCY_BILL
}
