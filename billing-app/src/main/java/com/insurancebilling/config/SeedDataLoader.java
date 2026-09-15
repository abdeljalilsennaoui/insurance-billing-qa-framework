package com.insurancebilling.config;

import com.insurancebilling.domain.BankAccountReference;
import com.insurancebilling.domain.BillingAccount;
import com.insurancebilling.domain.BillingTransaction;
import com.insurancebilling.domain.Customer;
import com.insurancebilling.domain.Installment;
import com.insurancebilling.domain.Invoice;
import com.insurancebilling.domain.PaymentMethod;
import com.insurancebilling.domain.PaymentPlan;
import com.insurancebilling.domain.Policy;
import com.insurancebilling.domain.PolicyStatus;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.domain.PolicyType;
import com.insurancebilling.domain.ReturnReason;
import com.insurancebilling.repository.BillingAccountRepository;
import com.insurancebilling.repository.CustomerRepository;
import com.insurancebilling.repository.InvoiceRepository;
import com.insurancebilling.service.InstallmentScheduleGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the deterministic baseline data set.
 *
 * <p>Every seeded record uses a fixed {@code SEED-} reference and a date expressed relative to the day
 * the application starts, so the data set is identical on every run without being pinned to calendar
 * dates that would silently turn an invoice overdue over time.
 *
 * <p>"The day the application starts" is the date in the configured business zone, read from the
 * injected {@link Clock}. It has to be the same zone the overdue rule is evaluated in: a baseline
 * built against one zone and judged against another would put the seeded invoices a day out of step
 * with their own documented states for part of every day. See DEF-012.
 *
 * <p>Seeded data exists for manual exploration, for read-only scenarios, and for the Cypress smoke
 * suite. Any scenario that <em>mutates</em> data creates its own records through the API instead: the
 * rule is that a test which changes data owns that data. Relying on a shared seeded invoice is what
 * makes suites order-dependent.
 */
@Configuration
public class SeedDataLoader {

  /**
   * Runs the seed load during bean initialisation, not from an {@code ApplicationRunner}.
   *
   * <p>This matters for test reliability. Spring Boot starts Tomcat as part of finishing the context
   * refresh, but {@code ApplicationRunner} beans execute only *after* the refresh completes. Seeding
   * from a runner therefore leaves a window in which the application already accepts requests — and
   * already reports healthy — while the database is still empty. A suite that waits on the health
   * endpoint and then immediately reads an invoice gets an intermittent 404.
   *
   * <p>A {@code @PostConstruct} on a singleton runs before the web server begins listening, which
   * closes that window: if the application can be reached at all, the baseline is loaded.
   *
   * <p>The call has to cross a bean boundary for the writer's {@code @Transactional} to apply: a
   * self-invocation would bypass the proxy and run without a transaction.
   */
  @Component
  public static class SeedDataInitializer {

    private final SeedDataWriter writer;

    public SeedDataInitializer(SeedDataWriter writer) {
      this.writer = writer;
    }

    @PostConstruct
    void seed() {
      writer.load();
    }
  }

  /**
   * Writes the baseline data set.
   *
   * <p>Separated from the runner bean so that the QA reset endpoint can call exactly the same loading
   * code, guaranteeing a reset restores the identical baseline rather than an approximation of it.
   */
  @Component
  public static class SeedDataWriter {

    private final CustomerRepository customers;
    private final BillingAccountRepository accounts;
    private final InvoiceRepository invoices;
    private final InstallmentScheduleGenerator scheduleGenerator;
    private final Clock clock;

    public SeedDataWriter(
        CustomerRepository customers,
        BillingAccountRepository accounts,
        InvoiceRepository invoices,
        InstallmentScheduleGenerator scheduleGenerator,
        Clock clock) {
      this.customers = customers;
      this.accounts = accounts;
      this.invoices = invoices;
      this.scheduleGenerator = scheduleGenerator;
      this.clock = clock;
    }

    @Transactional
    public void load() {
      if (customers.count() > 0) {
        return;
      }
      LocalDate today = LocalDate.now(clock);

      Customer alice = new Customer("Alice", "Tremblay", "alice.tremblay@example.com");
      Customer bruno = new Customer("Bruno", "Lavoie", "bruno.lavoie@example.com");
      Customer chantal = new Customer("Chantal", "Gagnon", "chantal.gagnon@example.com");

      Policy autoPolicy =
          seedPolicy("SEED-POL-001", PolicyType.AUTO, "1440.00", today.minusMonths(3));
      Policy homePolicy =
          seedPolicy("SEED-POL-002", PolicyType.HOME, "960.00", today.minusMonths(6));
      Policy lifePolicy =
          seedPolicy("SEED-POL-003", PolicyType.LIFE, "2400.00", today.minusMonths(1));
      Policy lapsedPolicy =
          seedPolicy("SEED-POL-004", PolicyType.AUTO, "1080.00", today.minusMonths(12));
      lapsedPolicy.setStatus(PolicyStatus.LAPSED);

      alice.addPolicy(autoPolicy);
      alice.addPolicy(lifePolicy);
      bruno.addPolicy(homePolicy);
      chantal.addPolicy(lapsedPolicy);

      // An untouched invoice, well within its due date.
      Invoice unpaid = seedInvoice("SEED-INV-001", "360.00", today.minusDays(5), today.plusDays(25));
      autoPolicy.addInvoice(unpaid);

      // Part-paid: two of four instalments received.
      Invoice partiallyPaid =
          seedInvoice("SEED-INV-002", "360.00", today.minusDays(40), today.plusDays(10));
      autoPolicy.addInvoice(partiallyPaid);
      partiallyPaid.applyPayment(
          new BigDecimal("90.00"), PaymentMethod.DIRECT_DEBIT, "SEED-PAY-001", clock.instant());
      partiallyPaid.applyPayment(
          new BigDecimal("90.00"), PaymentMethod.DIRECT_DEBIT, "SEED-PAY-002", clock.instant());

      // Settled in full.
      Invoice paid = seedInvoice("SEED-INV-003", "480.00", today.minusDays(50), today.minusDays(20));
      homePolicy.addInvoice(paid);
      paid.applyPayment(
          new BigDecimal("480.00"), PaymentMethod.BANK_TRANSFER, "SEED-PAY-003", clock.instant());

      // Past its due date with nothing paid: becomes OVERDUE on the first listing.
      Invoice overdue =
          seedInvoice("SEED-INV-004", "200.00", today.minusDays(60), today.minusDays(30));
      homePolicy.addInvoice(overdue);
      overdue.markOverdueIfDue(today);

      // Cancelled, so payments against it are refused.
      Invoice cancelled =
          seedInvoice("SEED-INV-005", "600.00", today.minusDays(15), today.plusDays(15));
      lifePolicy.addInvoice(cancelled);
      cancelled.cancel();

      // On a lapsed policy, so payments against it are refused for a different reason.
      Invoice onLapsedPolicy =
          seedInvoice("SEED-INV-006", "270.00", today.minusDays(20), today.plusDays(10));
      lapsedPolicy.addInvoice(onLapsedPolicy);

      // Policies and invoices are reachable from the customer and cascade on save.
      customers.saveAll(java.util.List.of(alice, bruno, chantal));

      seedBilledAccounts(today);
    }

    /**
     * Seeds the two accounts billed on a payment schedule.
     *
     * <p>Two, not one, and the figures are chosen rather than arbitrary. The first term's premium and
     * tax divide evenly across twelve; the second's do not, so its schedule carries the rounding
     * remainder on its down payment and is the fixture that would notice if that ever changed. The
     * second account also carries a returned payment, which is the only way the NSF counters, the
     * reversing ledger line and a reversed installment are visible without a test creating them.
     *
     * <p>Saved after the customers rather than with them: a term's installments have to exist before an
     * invoice can reference one.
     */
    private void seedBilledAccounts(LocalDate today) {
      Customer dominique = new Customer("Dominique", "Fortin", "dominique.fortin@example.com");
      Policy evenPolicy =
          seedPolicy("SEED-POL-005", PolicyType.AUTO, "1440.00", today.minusMonths(2));
      dominique.addPolicy(evenPolicy);

      // An accented name on purpose: it makes every hop - JSON, Thymeleaf, JPA and SOAP - prove it
      // round-trips UTF-8 rather than merely claiming to.
      Customer elise = new Customer("Élise", "Marchand", "elise.marchand@example.com");
      Policy unevenPolicy =
          seedPolicy("SEED-POL-006", PolicyType.HOME, "1000.00", today.minusMonths(1));
      elise.addPolicy(unevenPolicy);

      customers.saveAll(List.of(dominique, elise));

      // 1440.00 over twelve is 120.00 exactly, and 129.60 of tax is 10.80 exactly: a down payment of
      // 130.80 then eleven of 132.80, totalling 1591.60.
      BillingAccount evenAccount =
          seedAccount("ACCT-100001", "Dominique Fortin", "204", PaymentPlan.MONTHLY);
      PolicyTerm evenTerm =
          seedTerm(
              evenAccount,
              evenPolicy,
              "SEED-TERM-001",
              "SEED-INS-001",
              "1440.00",
              "129.60",
              "2.00",
              today.minusMonths(2));
      evenTerm.postNewBusiness(
          "SEED-TXN-001", "New business", today.minusMonths(2), clock.instant());
      payInstallment(evenTerm, "SEED-TXN-002", today.minusMonths(2));
      payInstallment(evenTerm, "SEED-TXN-003", today.minusMonths(1));

      // 1000.00 over twelve is 83.3333: the down payment absorbs the four cents and pays 83.37, so the
      // schedule is 90.87 then eleven of 92.83, totalling 1112.00.
      BillingAccount unevenAccount =
          seedAccount("ACCT-100002", "Élise Marchand", "871", PaymentPlan.MONTHLY);
      PolicyTerm unevenTerm =
          seedTerm(
              unevenAccount,
              unevenPolicy,
              "SEED-TERM-002",
              "SEED-INS-002",
              "1000.00",
              "90.00",
              "2.00",
              today.minusMonths(1));
      unevenTerm.postNewBusiness(
          "SEED-TXN-101", "New business", today.minusMonths(1), clock.instant());
      BillingTransaction returnedPayment =
          payInstallment(unevenTerm, "SEED-TXN-102", today.minusMonths(1));
      unevenTerm.returnPayment(
          "SEED-TXN-103",
          "SEED-TXN-104",
          returnedPayment,
          ReturnReason.INSUFFICIENT_FUNDS,
          new BigDecimal("25.00"),
          today.minusDays(20),
          clock.instant());

      accounts.saveAll(List.of(evenAccount, unevenAccount));

      seedInstallmentInvoices(evenTerm, today);
    }

    /** An account collected by pre-authorised debit, holding only the last three account digits. */
    private BillingAccount seedAccount(
        String reference, String accountHolder, String lastDigits, PaymentPlan plan) {
      BillingAccount account =
          new BillingAccount(reference, plan, PaymentMethod.PRE_AUTHORIZED_DEBIT);
      account.setBankAccount(BankAccountReference.of(accountHolder, lastDigits));
      return account;
    }

    /** A term with its schedule generated but not yet bound. */
    private PolicyTerm seedTerm(
        BillingAccount account,
        Policy policy,
        String termReference,
        String installmentPrefix,
        String premium,
        String tax,
        String fee,
        LocalDate effective) {
      PolicyTerm term =
          new PolicyTerm(
              termReference,
              1,
              effective,
              effective.plusYears(1),
              PaymentPlan.MONTHLY,
              new BigDecimal(premium),
              new BigDecimal(tax),
              new BigDecimal(fee));
      policy.attachTerm(term);
      account.addTerm(term);
      account.setCustomer(policy.getCustomer());

      List<Installment> schedule =
          scheduleGenerator.generate(
              installmentPrefix,
              PaymentPlan.MONTHLY,
              new BigDecimal(premium),
              new BigDecimal(tax),
              new BigDecimal(fee),
              effective);
      schedule.forEach(term::addInstallment);
      return term;
    }

    /** Pays exactly what the next unsettled installment asks for. */
    private BillingTransaction payInstallment(
        PolicyTerm term, String reference, LocalDate effective) {
      BigDecimal due =
          term.nextUnpaidInstallment()
              .orElseThrow(() -> new IllegalStateException("Seeded schedule has nothing left to pay"))
              .getAmountDue();
      return term.recordPayment(
          reference, due, "Payment - pre-authorised debit", effective, clock.instant());
    }

    /**
     * Raises the invoice each already-billed installment produced.
     *
     * <p>This is what joins the two halves of the domain: a scheduled installment is what raises the
     * billing document, and the document is what the invoice console has always listed. Only
     * installments that have actually been drawn get one — a schedule twelve months long does not put
     * twelve invoices in front of a policyholder on day one.
     */
    private void seedInstallmentInvoices(PolicyTerm term, LocalDate today) {
      term.ageScheduleAsOf(today);
      int sequence = 1;
      for (Installment installment : term.getInstallments()) {
        if (installment.getScheduledDate().isAfter(today)) {
          break;
        }
        Invoice invoice =
            new Invoice(
                "SEED-INS-INV-%03d".formatted(sequence++),
                installment.getAmountDue(),
                installment.getScheduledDate(),
                installment.getDueDate());
        term.getPolicy().addInvoice(invoice);
        invoice.billsInstallment(installment);
        if (installment.getStatus() == com.insurancebilling.domain.InstallmentStatus.PAID) {
          invoice.applyPayment(
              installment.getAmountDue(),
              PaymentMethod.PRE_AUTHORIZED_DEBIT,
              "SEED-PAD-%03d".formatted(sequence - 1),
              clock.instant());
        }
        invoice.markOverdueIfDue(today);
        invoices.save(invoice);
      }
    }

    /** A one-year policy term starting on the given date. */
    private Policy seedPolicy(String number, PolicyType type, String premium, LocalDate start) {
      return new Policy(number, type, new BigDecimal(premium), start, start.plusYears(1));
    }

    private Invoice seedInvoice(String number, String total, LocalDate issued, LocalDate due) {
      return new Invoice(number, new BigDecimal(total), issued, due);
    }
  }
}
