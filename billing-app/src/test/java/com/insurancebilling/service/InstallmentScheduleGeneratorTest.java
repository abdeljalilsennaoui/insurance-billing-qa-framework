package com.insurancebilling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insurancebilling.domain.Installment;
import com.insurancebilling.domain.InstallmentStatus;
import com.insurancebilling.domain.Money;
import com.insurancebilling.domain.PaymentPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Building a payment schedule from a term's figures.
 *
 * <p>No Spring context: the generator takes every input as an argument and reads no clock, so these are
 * plain unit tests over pure arithmetic and date shifting.
 */
class InstallmentScheduleGeneratorTest {

  private static final LocalDate FIRST_DRAW = LocalDate.of(2026, 1, 12);

  private final InstallmentScheduleGenerator generator = new InstallmentScheduleGenerator();

  private static BigDecimal money(String amount) {
    return new BigDecimal(amount);
  }

  private List<Installment> monthlySchedule(String premium, String tax, String fee) {
    return generator.generate("INS-TEST", PaymentPlan.MONTHLY, money(premium), money(tax), money(fee), FIRST_DRAW);
  }

  @Test
  @DisplayName("a monthly plan produces twelve installments numbered from one")
  void monthlyPlanProducesTwelveInstallmentsInOrder() {
    List<Installment> schedule = monthlySchedule("1440.00", "129.60", "2.00");

    assertThat(schedule).hasSize(12);
    assertThat(schedule).extracting(Installment::getSequenceNumber).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
  }

  @Test
  @DisplayName("a schedule that divides evenly bills the same amount after the down payment")
  void evenScheduleBillsIdenticalInstallmentsAfterTheDownPayment() {
    List<Installment> schedule = monthlySchedule("1440.00", "129.60", "2.00");

    assertThat(schedule.get(0).getAmountDue())
        .as("the down payment carries no installment fee")
        .isEqualByComparingTo("130.80");
    assertThat(schedule.subList(1, 12))
        .allSatisfy(installment -> assertThat(installment.getAmountDue()).isEqualByComparingTo("132.80"));
  }

  @Test
  @DisplayName("an uneven premium puts the odd cents on the down payment, not the last installment")
  void unevenPremiumPutsTheOddCentsOnTheDownPayment() {
    List<Installment> schedule = monthlySchedule("1000.00", "90.00", "2.00");

    assertThat(schedule.get(0).getPremiumAmount()).isEqualByComparingTo("83.37");
    assertThat(schedule.get(0).getAmountDue()).isEqualByComparingTo("90.87");
    assertThat(schedule.subList(1, 12))
        .as("a policyholder quoted eleven payments of 92.83 gets eleven payments of 92.83")
        .allSatisfy(installment -> assertThat(installment.getAmountDue()).isEqualByComparingTo("92.83"));
    assertThat(schedule.get(11).getAmountDue())
        .as("nothing is stranded on the final installment")
        .isEqualByComparingTo("92.83");
  }

  @ParameterizedTest(name = "{0} premium and {1} tax on a {2} plan reconcile exactly")
  @CsvSource({
    "1440.00, 129.60, MONTHLY",
    "1000.00, 90.00, MONTHLY",
    "2400.00, 216.00, MONTHLY",
    "999.99, 89.99, MONTHLY",
    "1234.56, 111.11, QUARTERLY",
    "960.00, 86.40, SEMI_ANNUAL",
    "1080.00, 97.20, ANNUAL",
    "0.07, 0.01, MONTHLY"
  })
  @DisplayName("premium, tax and fee each reconcile against the term on their own")
  void everyColumnReconcilesOnItsOwn(String premium, String tax, PaymentPlan plan) {
    List<Installment> schedule =
        generator.generate("INS-TEST", plan, money(premium), money(tax), money("2.00"), FIRST_DRAW);

    assertThat(Money.sum(schedule.stream().map(Installment::getPremiumAmount).toList()))
        .as("the premium column must add back to the term premium")
        .isEqualByComparingTo(money(premium));
    assertThat(Money.sum(schedule.stream().map(Installment::getTaxAmount).toList()))
        .as("the tax column must add back to the term tax")
        .isEqualByComparingTo(money(tax));
    assertThat(Money.sum(schedule.stream().map(Installment::getAmountDue).toList()))
        .as("the schedule must collect exactly what the term is worth")
        .isEqualByComparingTo(generator.termTotal(plan, money(premium), money(tax), money("2.00")));
  }

  @ParameterizedTest
  @EnumSource(PaymentPlan.class)
  @DisplayName("every payment plan produces its own installment count and spacing")
  void everyPlanProducesItsOwnCountAndSpacing(PaymentPlan plan) {
    List<Installment> schedule =
        generator.generate("INS-TEST", plan, money("1200.00"), money("108.00"), money("2.00"), FIRST_DRAW);

    assertThat(schedule).hasSize(plan.installmentCount());
    if (plan.installmentCount() > 1) {
      assertThat(schedule.get(1).getScheduledDate())
          .isEqualTo(FIRST_DRAW.plusMonths(plan.monthsBetweenInstallments()));
    }
    assertThat(schedule.get(schedule.size() - 1).getScheduledDate())
        .as("the last installment is drawn inside the term, not after it")
        .isBefore(FIRST_DRAW.plusYears(1));
  }

  @Test
  @DisplayName("an annual plan is a single installment with no fee to spread")
  void annualPlanCarriesNoInstallmentFee() {
    List<Installment> schedule =
        generator.generate("INS-TEST", PaymentPlan.ANNUAL, money("1080.00"), money("97.20"), money("2.00"), FIRST_DRAW);

    assertThat(schedule).singleElement().satisfies(installment -> {
      assertThat(installment.getFeeAmount()).isEqualByComparingTo("0.00");
      assertThat(installment.getAmountDue()).isEqualByComparingTo("1177.20");
    });
  }

  @Test
  @DisplayName("only the first installment escapes the installment fee")
  void onlyTheDownPaymentEscapesTheFee() {
    List<Installment> schedule = monthlySchedule("1440.00", "129.60", "2.00");

    assertThat(schedule.get(0).getFeeAmount()).isEqualByComparingTo("0.00");
    assertThat(schedule.subList(1, 12))
        .allSatisfy(installment -> assertThat(installment.getFeeAmount()).isEqualByComparingTo("2.00"));
  }

  @Test
  @DisplayName("each installment falls due a grace period after it is drawn")
  void dueDateFollowsTheScheduledDateByTheGracePeriod() {
    List<Installment> schedule = monthlySchedule("1440.00", "129.60", "2.00");

    assertThat(schedule.get(0).getScheduledDate()).isEqualTo(FIRST_DRAW);
    assertThat(schedule.get(0).getDueDate())
        .isEqualTo(FIRST_DRAW.plusDays(InstallmentScheduleGenerator.DEFAULT_GRACE_DAYS));
    assertThat(schedule)
        .allSatisfy(installment ->
            assertThat(installment.getDueDate())
                .isEqualTo(installment.getScheduledDate().plusDays(InstallmentScheduleGenerator.DEFAULT_GRACE_DAYS)));
  }

  @Test
  @DisplayName("a schedule drawn on the 31st does not skip a month")
  void scheduleDrawnOnTheThirtyFirstDoesNotSkipAMonth() {
    List<Installment> schedule =
        generator.generate(
            "INS-TEST", PaymentPlan.MONTHLY, money("1200.00"), money("108.00"), money("2.00"), LocalDate.of(2026, 1, 31));

    assertThat(schedule.get(1).getScheduledDate())
        .as("February has no 31st, so the draw moves to the last day it has")
        .isEqualTo(LocalDate.of(2026, 2, 28));
    assertThat(schedule).extracting(Installment::getScheduledDate).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("every installment starts scheduled and references its sequence")
  void everyInstallmentStartsScheduledWithItsOwnReference() {
    List<Installment> schedule = monthlySchedule("1440.00", "129.60", "2.00");

    assertThat(schedule).allSatisfy(installment ->
        assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.SCHEDULED));
    assertThat(schedule).extracting(Installment::getReference).doesNotHaveDuplicates();
    assertThat(schedule.get(0).getReference()).isEqualTo("INS-TEST-01");
    assertThat(schedule.get(11).getReference()).isEqualTo("INS-TEST-12");
  }

  @Test
  @DisplayName("a negative grace period is refused")
  void negativeGracePeriodIsRefused() {
    assertThatThrownBy(() ->
            generator.generate(
                "INS-TEST", PaymentPlan.MONTHLY, money("1200.00"), money("108.00"), money("2.00"), FIRST_DRAW, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Grace days cannot be negative");
  }
}
