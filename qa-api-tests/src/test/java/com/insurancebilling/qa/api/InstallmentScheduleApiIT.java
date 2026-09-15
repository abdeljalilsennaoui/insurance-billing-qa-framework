package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.InstallmentDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.List;
import org.testng.annotations.Test;

/**
 * The payment schedule a bound term produces, over HTTP.
 *
 * <p>Every scenario builds its own account and term. The API suite runs four threads against one
 * application, so a test reading a shared seeded schedule while another paid it would be a race, not a
 * test.
 */
public class InstallmentScheduleApiIT extends BaseApiTest {

  @Test(groups = {"smoke", "regression"})
  public void bindingATermProducesASchedulePostedToTheLedger() {
    BillingAccountDto account = testData.account();

    PolicyTermDto term = testData.evenTerm(account.accountReference());

    assertThat(term.status()).isEqualTo("IN_FORCE");
    assertThat(term.scheduledTotal()).isEqualByComparingTo("1591.60");
    assertThat(term.balance())
        .as("a term must be posted for exactly what its schedule will collect, or it can never settle")
        .isEqualByComparingTo(term.scheduledTotal());
    assertThat(term.installmentsRemaining()).isEqualTo(12);
  }

  @Test(groups = {"smoke", "regression"})
  public void aScheduleIsTwelveInstallmentsNumberedFromOne() {
    PolicyTermDto term = testData.boundTerm();

    List<InstallmentDto> schedule = billing.schedule(term.termReference());

    assertThat(schedule).hasSize(12);
    assertThat(schedule).extracting(InstallmentDto::sequenceNumber).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    assertThat(schedule).extracting(InstallmentDto::reference).doesNotHaveDuplicates();
  }

  @Test(groups = "regression")
  public void anEvenTermBillsTheSameAmountAfterItsDownPayment() {
    PolicyTermDto term = testData.boundTerm();

    List<InstallmentDto> schedule = billing.schedule(term.termReference());

    assertThat(schedule.get(0).amountDue()).isEqualByComparingTo("130.80");
    assertThat(schedule.subList(1, 12))
        .allSatisfy(installment -> assertThat(installment.amountDue()).isEqualByComparingTo("132.80"));
  }

  @Test(groups = "regression")
  public void anUnevenTermPutsTheOddCentsOnTheDownPayment() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.unevenTerm(account.accountReference());

    List<InstallmentDto> schedule = billing.schedule(term.termReference());

    assertThat(schedule.get(0).premiumAmount())
        .as("83.33 twelve times collects four cents less than 1000.00, and the down payment absorbs them")
        .isEqualByComparingTo("83.37");
    assertThat(schedule.get(0).amountDue()).isEqualByComparingTo("90.87");
    assertThat(schedule.subList(1, 12))
        .allSatisfy(installment -> assertThat(installment.amountDue()).isEqualByComparingTo("92.83"));
    assertThat(schedule.get(11).amountDue())
        .as("no cent may be stranded on the final installment")
        .isEqualByComparingTo("92.83");
  }

  @Test(groups = "regression")
  public void everyScheduleColumnReconcilesAgainstItsTerm() {
    BillingAccountDto account = testData.account();
    PolicyTermDto term = testData.unevenTerm(account.accountReference());

    List<InstallmentDto> schedule = billing.schedule(term.termReference());

    assertThat(sum(schedule, InstallmentDto::premiumAmount))
        .as("the premium column must add back to the term premium")
        .isEqualByComparingTo(term.termPremium());
    assertThat(sum(schedule, InstallmentDto::taxAmount))
        .as("the tax column must add back to the term tax")
        .isEqualByComparingTo(term.termTax());
    assertThat(sum(schedule, InstallmentDto::amountDue))
        .as("the schedule must collect exactly what the term was posted for")
        .isEqualByComparingTo(term.scheduledTotal());
  }

  @Test(groups = "regression")
  public void onlyTheDownPaymentEscapesTheInstallmentFee() {
    PolicyTermDto term = testData.boundTerm();

    List<InstallmentDto> schedule = billing.schedule(term.termReference());

    assertThat(schedule.get(0).feeAmount())
        .as("nothing is being deferred at the moment the first installment is collected")
        .isEqualByComparingTo("0.00");
    assertThat(schedule.subList(1, 12))
        .allSatisfy(installment -> assertThat(installment.feeAmount()).isEqualByComparingTo("2.00"));
  }

  @Test(groups = "regression")
  public void anInstallmentFallsDueAfterTheDateItIsDrawn() {
    PolicyTermDto term = testData.boundTerm();

    assertThat(billing.schedule(term.termReference()))
        .allSatisfy(
            installment ->
                assertThat(installment.dueDate())
                    .as("the grace period is what separates being drawn from being late")
                    .isAfter(installment.scheduledDate()));
  }

  @Test(groups = "regression")
  public void aScheduleAgesItselfIntoWhatIsPastDueAndStillToCome() {
    PolicyTermDto term = testData.boundTerm();

    List<InstallmentDto> schedule = billing.schedule(term.termReference());

    assertThat(schedule)
        .as("a term that started two months ago cannot have a fully unbilled schedule")
        .anySatisfy(installment -> assertThat(installment.status()).isNotEqualTo("SCHEDULED"));
    assertThat(schedule).anySatisfy(installment -> assertThat(installment.status()).isEqualTo("SCHEDULED"));
  }

  @Test(groups = {"negative", "regression"})
  public void anUnknownTermIsNotFoundRatherThanAnEmptySchedule() {
    Response response = billing.scheduleRaw("TERM-DOES-NOT-EXIST");

    assertThat(response.statusCode())
        .as("an empty list would tell a caller the term exists and has no schedule")
        .isEqualTo(404);
    assertThat(response.jsonPath().getString("code")).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void bindingATermToAnUnknownAccountIsNotFound() {
    Response response =
        billing.bindTermRaw(
            "ACCT-DOES-NOT-EXIST",
            java.util.Map.of(
                "policyId", testData.activePolicy().id(),
                "effectiveDate", "2026-01-12",
                "paymentPlan", "MONTHLY",
                "termPremium", "1200.00",
                "termTax", "108.00",
                "installmentFee", "2.00"));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.jsonPath().getString("code")).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void bindingATermWithoutAPremiumIsAMalformedRequest() {
    BillingAccountDto account = testData.account();

    Response response =
        billing.bindTermRaw(
            account.accountReference(),
            java.util.Map.of(
                "policyId", testData.activePolicy().id(),
                "effectiveDate", "2026-01-12",
                "paymentPlan", "MONTHLY",
                "termTax", "108.00",
                "installmentFee", "2.00"));

    assertThat(response.statusCode())
        .as("a missing required field is the caller's shape being wrong, not a billing rule refusing")
        .isEqualTo(400);
    assertThat(response.jsonPath().getString("code")).isEqualTo("VALIDATION_FAILED");
    assertThat(response.jsonPath().getString("fieldErrors.termPremium")).isNotBlank();
  }

  private static BigDecimal sum(
      List<InstallmentDto> schedule, java.util.function.Function<InstallmentDto, BigDecimal> column) {
    return schedule.stream().map(column).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
