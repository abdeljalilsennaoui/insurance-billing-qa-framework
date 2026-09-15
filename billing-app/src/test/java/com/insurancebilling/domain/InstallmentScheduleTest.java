package com.insurancebilling.domain;

import static com.insurancebilling.domain.BillingFixtures.TERM_START;
import static com.insurancebilling.domain.BillingFixtures.evenTerm;
import static com.insurancebilling.domain.BillingFixtures.money;
import static com.insurancebilling.domain.BillingFixtures.payNextInstallment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How a single installment reports itself as time passes.
 *
 * <p>The aging rule takes the reference date as an argument rather than reading a clock, for the same
 * reason {@link Invoice#isOverdue} does — the entity states the rule and the caller states the time. It
 * is why these cases can be written as plain assertions about named dates instead of by freezing a
 * clock.
 */
class InstallmentScheduleTest {

  private static final LocalDate SCHEDULED = LocalDate.of(2026, 3, 12);
  private static final LocalDate DUE = LocalDate.of(2026, 3, 27);

  private static Installment installment() {
    return new Installment("INS-TEST-01", 1, SCHEDULED, DUE, money("120.00"), money("10.80"), money("2.00"));
  }

  @Test
  @DisplayName("an installment is due for its premium, its tax and its fee together")
  void anInstallmentIsDueForAllThreeColumns() {
    assertThat(installment().getAmountDue()).isEqualByComparingTo("132.80");
  }

  @Test
  @DisplayName("before it is drawn, an installment is merely scheduled")
  void beforeItIsDrawnAnInstallmentIsScheduled() {
    Installment installment = installment();

    installment.ageAsOf(SCHEDULED.minusDays(1));

    assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.SCHEDULED);
  }

  @Test
  @DisplayName("on the day it is drawn, an installment is billed")
  void onTheDayItIsDrawnAnInstallmentIsBilled() {
    Installment installment = installment();

    installment.ageAsOf(SCHEDULED);

    assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.BILLED);
  }

  @Test
  @DisplayName("on its due date an installment is still only billed, not late")
  void onItsDueDateAnInstallmentIsNotYetLate() {
    Installment installment = installment();

    installment.ageAsOf(DUE);

    assertThat(installment.getStatus())
        .as("the grace period runs to the end of the due date, not to the start of it")
        .isEqualTo(InstallmentStatus.BILLED);
  }

  @Test
  @DisplayName("the day after its due date an installment is overdue")
  void theDayAfterItsDueDateAnInstallmentIsOverdue() {
    Installment installment = installment();

    installment.ageAsOf(DUE.plusDays(1));

    assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.OVERDUE);
  }

  @Test
  @DisplayName("a settled installment is never aged back into being owed")
  void aSettledInstallmentIsNeverAgedBack() {
    PolicyTerm term = evenTerm();
    payNextInstallment(term, "TXN-PAY-1");

    term.ageScheduleAsOf(TERM_START.plusYears(2));

    assertThat(term.getInstallments().get(0).getStatus()).isEqualTo(InstallmentStatus.PAID);
    assertThat(term.getInstallments().subList(1, 12))
        .allSatisfy(installment -> assertThat(installment.getStatus()).isEqualTo(InstallmentStatus.OVERDUE));
  }

  @Test
  @DisplayName("aging a whole schedule sorts it into what is past, due and still to come")
  void agingAWholeScheduleSortsIt() {
    PolicyTerm term = evenTerm();

    term.ageScheduleAsOf(TERM_START.plusMonths(3));

    assertThat(term.getInstallments())
        .filteredOn(installment -> installment.getStatus() == InstallmentStatus.OVERDUE)
        .as("the first three draws have passed their grace period by month three")
        .hasSize(3);
    assertThat(term.getInstallments())
        .filteredOn(installment -> installment.getStatus() == InstallmentStatus.BILLED)
        .hasSize(1);
    assertThat(term.getInstallments())
        .filteredOn(installment -> installment.getStatus() == InstallmentStatus.SCHEDULED)
        .hasSize(8);
  }

  @Test
  @DisplayName("an installment cannot fall due before it is drawn")
  void anInstallmentCannotFallDueBeforeItIsDrawn() {
    assertThatThrownBy(() ->
            new Installment("INS-TEST-01", 1, DUE, SCHEDULED, money("120.00"), money("10.80"), money("2.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot be due");
  }

  @Test
  @DisplayName("installments are numbered from one")
  void installmentsAreNumberedFromOne() {
    assertThatThrownBy(() ->
            new Installment("INS-TEST-00", 0, SCHEDULED, DUE, money("120.00"), money("10.80"), money("2.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("numbered from one");
  }

  @Test
  @DisplayName("an installment that has been settled is no longer outstanding")
  void aSettledInstallmentIsNoLongerOutstanding() {
    PolicyTerm term = evenTerm();
    assertThat(term.getInstallments().get(0).isOutstanding()).isTrue();

    payNextInstallment(term, "TXN-PAY-1");

    assertThat(term.getInstallments().get(0).isOutstanding()).isFalse();
    assertThat(term.getInstallments().get(0).getSettledBy()).isNotNull();
  }
}
