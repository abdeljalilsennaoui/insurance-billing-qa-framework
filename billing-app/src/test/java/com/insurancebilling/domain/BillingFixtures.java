package com.insurancebilling.domain;

import com.insurancebilling.service.InstallmentScheduleGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Builders for the account, term and ledger objects used by the billing unit tests.
 *
 * <p>Separate from {@link DomainFixtures} because the two model different things: that one builds
 * invoices raised directly against a policy, this one builds terms billed on a schedule. A test reading
 * either should not have to work out which half of a combined fixture class it is using.
 *
 * <p>Every builder returns a fresh graph. Nothing is shared between tests, so a test that pays an
 * installment cannot change what the next test sees.
 */
final class BillingFixtures {

  private BillingFixtures() {}

  /** The instant every fixture ledger line is processed at. Fixed, for the reasons DomainFixtures gives. */
  static final Instant PROCESSED_AT = Instant.parse("2026-01-14T15:00:00Z");

  /** The date every fixture term takes effect. Fixed so schedule dates are nameable in assertions. */
  static final LocalDate TERM_START = LocalDate.of(2026, 1, 12);

  static final InstallmentScheduleGenerator GENERATOR = new InstallmentScheduleGenerator();

  static BigDecimal money(String amount) {
    return new BigDecimal(amount);
  }

  /**
   * A monthly term of 1440.00 premium and 129.60 tax, bound and in force, with nothing paid.
   *
   * <p>These figures divide evenly across twelve: twelve premiums of 120.00 and twelve taxes of 10.80.
   * Use {@link #unevenTerm()} for the case where they do not.
   */
  static PolicyTerm evenTerm() {
    return boundTerm("1440.00", "129.60", "2.00", PaymentPlan.MONTHLY);
  }

  /**
   * A monthly term of 1000.00 premium and 90.00 tax, bound and in force.
   *
   * <p>1000.00 over twelve is 83.3333…, so this term is the one that exercises the rounding remainder
   * all the way through the ledger.
   */
  static PolicyTerm unevenTerm() {
    return boundTerm("1000.00", "90.00", "2.00", PaymentPlan.MONTHLY);
  }

  /** A term built from the given figures, bound to a fresh account, customer and policy. */
  static PolicyTerm boundTerm(String premium, String tax, String fee, PaymentPlan plan) {
    PolicyTerm term = unboundTerm(premium, tax, fee, plan);
    term.postNewBusiness("TXN-TEST-NB", "New business", TERM_START, PROCESSED_AT);
    return term;
  }

  /** A term with its schedule attached but no ledger yet, for tests about binding itself. */
  static PolicyTerm unboundTerm(String premium, String tax, String fee, PaymentPlan plan) {
    Customer customer = new Customer("Test", "Policyholder", "test.policyholder@example.com");
    Policy policy =
        new Policy(
            "POL-TEST-TERM",
            PolicyType.AUTO,
            money(premium),
            TERM_START,
            TERM_START.plusYears(1));
    customer.addPolicy(policy);

    BillingAccount account =
        new BillingAccount("ACCT-TEST-001", plan, PaymentMethod.PRE_AUTHORIZED_DEBIT);
    account.setCustomer(customer);
    account.setBankAccount(BankAccountReference.of("Test Policyholder", "204"));

    PolicyTerm term =
        new PolicyTerm(
            "TERM-TEST-001",
            1,
            TERM_START,
            TERM_START.plusYears(1),
            plan,
            money(premium),
            money(tax),
            money(fee));
    policy.attachTerm(term);
    account.addTerm(term);

    List<Installment> schedule =
        GENERATOR.generate("INS-TEST", plan, money(premium), money(tax), money(fee), TERM_START);
    schedule.forEach(term::addInstallment);
    return term;
  }

  /** Records a payment of the exact amount the next unsettled installment asks for. */
  static BillingTransaction payNextInstallment(PolicyTerm term, String reference) {
    BigDecimal due =
        term.nextUnpaidInstallment()
            .orElseThrow(() -> new IllegalStateException("The schedule has nothing left to pay"))
            .getAmountDue();
    return term.recordPayment(reference, due, "Payment - pre-authorised debit", TERM_START, PROCESSED_AT);
  }
}
