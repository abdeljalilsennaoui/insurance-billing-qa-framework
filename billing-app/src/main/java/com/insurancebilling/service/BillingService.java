package com.insurancebilling.service;

import com.insurancebilling.api.dto.BillingAccountRequest;
import com.insurancebilling.api.dto.PolicyTermRequest;
import com.insurancebilling.domain.BankAccountReference;
import com.insurancebilling.domain.BillingAccount;
import com.insurancebilling.domain.BillingTransaction;
import com.insurancebilling.domain.BillingType;
import com.insurancebilling.domain.Customer;
import com.insurancebilling.domain.Installment;
import com.insurancebilling.domain.Money;
import com.insurancebilling.domain.Policy;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.domain.ReturnReason;
import com.insurancebilling.repository.BillingAccountRepository;
import com.insurancebilling.repository.BillingTransactionRepository;
import com.insurancebilling.repository.CustomerRepository;
import com.insurancebilling.repository.PolicyRepository;
import com.insurancebilling.repository.PolicyTermRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes against billing accounts, policy terms and their ledgers.
 *
 * <p>The service holds no billing rules. Every decision about whether money may move, what a payment
 * settles and what a return undoes lives on {@link PolicyTerm}, for the reasons its class javadoc
 * gives. What is here is the part a domain object cannot do for itself: finding the right aggregate,
 * generating references, reading the business clock, and turning a missing row into a 404.
 *
 * <p>Reads are deliberately <em>not</em> {@code readOnly}. Looking at a schedule ages it: an
 * installment whose due date has passed becomes overdue the moment somebody asks, exactly as
 * {@link InvoiceService#findAll} promotes a past-due invoice. The alternative is a scheduled job, which
 * would make the state of the data depend on when that job last ran.
 */
@Service
@Transactional
public class BillingService {

  private final BillingAccountRepository accounts;
  private final PolicyTermRepository terms;
  private final BillingTransactionRepository transactions;
  private final PolicyRepository policies;
  private final CustomerRepository customers;
  private final ReferenceGenerator references;
  private final InstallmentScheduleGenerator scheduleGenerator;
  private final Clock clock;
  private final BigDecimal nsfFee;

  public BillingService(
      BillingAccountRepository accounts,
      PolicyTermRepository terms,
      BillingTransactionRepository transactions,
      PolicyRepository policies,
      CustomerRepository customers,
      ReferenceGenerator references,
      InstallmentScheduleGenerator scheduleGenerator,
      Clock clock,
      @Value("${billing.nsf-fee:25.00}") BigDecimal nsfFee) {
    this.accounts = accounts;
    this.terms = terms;
    this.transactions = transactions;
    this.policies = policies;
    this.customers = customers;
    this.references = references;
    this.scheduleGenerator = scheduleGenerator;
    this.clock = clock;
    this.nsfFee = Money.normalise(nsfFee);
  }

  /**
   * Opens a billing account for a customer.
   *
   * <p>Exists so that an automated suite can create the data it is about to change. The API suite runs
   * four threads in parallel against one application; a test that moved money on a shared seeded
   * account would be racing every other test that read it.
   */
  public BillingAccount openAccount(BillingAccountRequest request) {
    Customer customer =
        customers
            .findById(request.customerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", request.customerId()));

    BillingAccount account =
        new BillingAccount(
            references.accountReference(), request.paymentPlan(), request.paymentMethod());
    account.setCustomer(customer);
    if (request.accountHolder() != null && request.accountLastDigits() != null) {
      account.setBankAccount(
          BankAccountReference.of(request.accountHolder(), request.accountLastDigits()));
    }
    return accounts.save(account);
  }

  /**
   * Binds a term to an account: generates the schedule and posts the term to the ledger.
   *
   * <p>One call, because a term that exists without a schedule, or with a schedule but nothing posted,
   * is not a state the business has a name for.
   */
  public PolicyTerm bindTerm(String accountReference, PolicyTermRequest request) {
    BillingAccount account = findAccount(accountReference);
    Policy policy =
        policies
            .findById(request.policyId())
            .orElseThrow(() -> new ResourceNotFoundException("Policy", request.policyId()));

    PolicyTerm term =
        new PolicyTerm(
            references.termReference(),
            account.getTerms().size() + 1,
            request.effectiveDate(),
            request.effectiveDate().plusYears(1),
            request.paymentPlan(),
            request.termPremium(),
            request.termTax(),
            request.installmentFee());
    term.setBillingType(
        request.billingType() == null ? BillingType.DIRECT_BILL : request.billingType());
    policy.attachTerm(term);
    account.addTerm(term);

    List<Installment> schedule =
        scheduleGenerator.generate(
            references.installmentPrefix(),
            request.paymentPlan(),
            request.termPremium(),
            request.termTax(),
            request.installmentFee(),
            request.effectiveDate());
    schedule.forEach(term::addInstallment);
    term.postNewBusiness(
        references.transactionReference(), "New business", request.effectiveDate(), clock.instant());

    accounts.save(account);
    return term;
  }

  /** Every account, aged, for the agent console's portfolio grid. */
  public List<BillingAccount> findAllAccounts() {
    List<BillingAccount> all = accounts.findAll();
    hydrate(all.stream().flatMap(account -> account.getTerms().stream()).toList());
    all.forEach(this::ageAccount);
    return all;
  }

  /** One account by its business reference. */
  public BillingAccount findAccount(String accountReference) {
    BillingAccount account =
        accounts
            .findByAccountReference(accountReference)
            .orElseThrow(() -> new ResourceNotFoundException("Billing account", accountReference));
    hydrate(account.getTerms());
    ageAccount(account);
    return account;
  }

  /** The terms billed to an account, oldest first. */
  public List<PolicyTerm> findTermsForAccount(String accountReference) {
    return findAccount(accountReference).getTerms();
  }

  /** One term by its business reference. */
  public PolicyTerm findTerm(String termReference) {
    PolicyTerm term =
        terms
            .findByTermReference(termReference)
            .orElseThrow(() -> new ResourceNotFoundException("Policy term", termReference));
    hydrate(List.of(term));
    term.ageScheduleAsOf(today());
    return term;
  }

  /**
   * Records money received against a term.
   *
   * <p>The amount is applied to the oldest unsettled installment first, then the next, for as far as it
   * reaches — the domain's rule, not this method's. A payment that names no amount it can fully settle
   * still reduces the balance and is held in suspense.
   */
  public BillingTransaction payTerm(String termReference, BigDecimal amount, String description) {
    PolicyTerm term = findTerm(termReference);
    return term.recordPayment(
        references.transactionReference(),
        amount,
        description == null || description.isBlank() ? "Payment" : description,
        today(),
        clock.instant());
  }

  /**
   * Reverses a payment the bank refused, and charges the configured fee.
   *
   * <p>The fee is waived for a return that is not the policyholder's doing — an account the bank closed
   * is not an account the policyholder failed to fund — which is a commercial policy decision and is
   * why it lives here rather than on the entity.
   */
  public BillingTransaction returnPayment(String transactionReference, ReturnReason reason) {
    BillingTransaction payment =
        transactions
            .findByReference(transactionReference)
            .orElseThrow(
                () -> new ResourceNotFoundException("Billing transaction", transactionReference));
    PolicyTerm term = payment.getPolicyTerm();
    hydrate(List.of(term));
    BigDecimal fee = reason.countsAsNsf() ? nsfFee : Money.ZERO;
    return term.returnPayment(
        references.transactionReference(),
        references.transactionReference(),
        payment,
        reason,
        fee,
        today(),
        clock.instant());
  }

  /**
   * Loads the schedules and ledgers of the given terms.
   *
   * <p>Two queries, whatever the number of terms. See {@link PolicyTermRepository} for why they cannot
   * come back with the terms themselves. The returned instances are discarded because they are the same
   * managed instances that were passed in — the point of the call is the side effect on the persistence
   * context, which is worth saying out loud because it looks like a dead call otherwise.
   */
  private void hydrate(List<PolicyTerm> toHydrate) {
    if (toHydrate.isEmpty()) {
      return;
    }
    List<Long> ids = toHydrate.stream().map(PolicyTerm::getId).toList();
    terms.findWithInstallmentsByIdIn(ids);
    terms.findWithTransactionsByIdIn(ids);
  }

  private void ageAccount(BillingAccount account) {
    LocalDate today = today();
    account.getTerms().forEach(term -> term.ageScheduleAsOf(today));
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
