package com.insurancebilling.service;

import com.insurancebilling.domain.Installment;
import com.insurancebilling.domain.Money;
import com.insurancebilling.domain.PaymentPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the payment schedule for a policy term.
 *
 * <p>A {@code @Component} so it can be injected, but it holds no state, reads no clock and touches no
 * database — every input arrives as an argument. That means the whole of this class can be exercised by
 * plain unit tests with no Spring context, which is where the interesting cases are: the ones about
 * money that does not divide evenly.
 *
 * <p>Premium, tax and fee are allocated <em>separately</em> rather than being summed and split once.
 * The ledger shows those three as their own columns, and a policyholder comparing the tax column
 * against the premium column expects each to reconcile on its own. Splitting the total and
 * back-deriving the parts would put the rounding remainder in whichever column the arithmetic happened
 * to land it in.
 *
 * <p>The first installment carries no installment fee. It is the down payment, taken when the policy is
 * bound, and the fee is a charge for spreading the <em>rest</em> of the term over time — there is
 * nothing being deferred at the moment the first one is collected. This is also why an
 * {@link PaymentPlan#ANNUAL} term carries no fee at all.
 */
@Component
public class InstallmentScheduleGenerator {

  /** Days between an installment being drawn and the date it must have arrived by. */
  public static final int DEFAULT_GRACE_DAYS = 15;

  /**
   * Builds a full schedule.
   *
   * <p>The returned installments sum, to the cent, to the term premium plus the term tax plus one fee
   * for every installment after the first. That is an invariant of this method rather than a hope: it
   * follows from {@link Money#allocate} distributing a remainder rather than rounding one away.
   *
   * @param referencePrefix prefix for each installment's reference, suffixed with its sequence number
   * @param plan the payment plan, which decides both how many installments and how far apart
   * @param termPremium the term's premium, split across the schedule
   * @param termTax the tax on that premium, split across the schedule
   * @param installmentFee the fee charged on every installment after the first
   * @param firstScheduledDate when the first installment is drawn
   * @param graceDays days between an installment being drawn and falling due
   * @return the schedule, ordered from sequence one
   */
  public List<Installment> generate(
      String referencePrefix,
      PaymentPlan plan,
      BigDecimal termPremium,
      BigDecimal termTax,
      BigDecimal installmentFee,
      LocalDate firstScheduledDate,
      int graceDays) {
    if (graceDays < 0) {
      throw new IllegalArgumentException("Grace days cannot be negative but was " + graceDays);
    }

    int count = plan.installmentCount();
    List<BigDecimal> premiums = Money.allocate(termPremium, count);
    List<BigDecimal> taxes = Money.allocate(termTax, count);
    BigDecimal fee = Money.normalise(installmentFee);

    List<Installment> schedule = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      int sequence = index + 1;
      LocalDate scheduledDate =
          firstScheduledDate.plusMonths((long) index * plan.monthsBetweenInstallments());
      schedule.add(
          new Installment(
              "%s-%02d".formatted(referencePrefix, sequence),
              sequence,
              scheduledDate,
              scheduledDate.plusDays(graceDays),
              premiums.get(index),
              taxes.get(index),
              index == 0 ? Money.ZERO : fee));
    }
    return schedule;
  }

  /** Builds a schedule using the platform's default grace period. */
  public List<Installment> generate(
      String referencePrefix,
      PaymentPlan plan,
      BigDecimal termPremium,
      BigDecimal termTax,
      BigDecimal installmentFee,
      LocalDate firstScheduledDate) {
    return generate(
        referencePrefix,
        plan,
        termPremium,
        termTax,
        installmentFee,
        firstScheduledDate,
        DEFAULT_GRACE_DAYS);
  }

  /**
   * What a schedule built from these inputs will collect in total.
   *
   * <p>Premium, plus tax, plus one fee for every installment after the first. Exposed so that a term
   * can be posted to the ledger without building the schedule first, and so that a test can assert the
   * two agree.
   */
  public BigDecimal termTotal(PaymentPlan plan, BigDecimal termPremium, BigDecimal termTax, BigDecimal installmentFee) {
    BigDecimal fees = Money.normalise(installmentFee).multiply(BigDecimal.valueOf(plan.installmentCount() - 1L));
    return Money.normalise(termPremium).add(Money.normalise(termTax)).add(fees);
  }
}
