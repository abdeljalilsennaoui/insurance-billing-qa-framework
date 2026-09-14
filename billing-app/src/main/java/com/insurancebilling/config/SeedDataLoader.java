package com.insurancebilling.config;

import com.insurancebilling.domain.Customer;
import com.insurancebilling.domain.Invoice;
import com.insurancebilling.domain.PaymentMethod;
import com.insurancebilling.domain.Policy;
import com.insurancebilling.domain.PolicyStatus;
import com.insurancebilling.domain.PolicyType;
import com.insurancebilling.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
    private final Clock clock;

    public SeedDataWriter(CustomerRepository customers, Clock clock) {
      this.customers = customers;
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
