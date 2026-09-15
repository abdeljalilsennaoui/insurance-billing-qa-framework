package com.insurancebilling.qa.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.model.InvoiceDto;
import io.restassured.response.Response;
import java.util.List;
import org.testng.annotations.Test;

/**
 * Coverage for the QA reset endpoint.
 *
 * <p><b>Named ResetEndpointIT rather than TestSupportResetIT for a reason.</b> Surefire's default include
 * patterns are {@code Test*.java}, {@code *Test.java}, {@code *Tests.java} and {@code *TestCase.java}. A
 * class called {@code TestSupportResetIT} matches the first of those despite ending in {@code IT}, so
 * Surefire claimed it and ran it during {@code mvn install} — before any application was started — and the
 * build failed on connection refused. Ending in {@code IT} is not enough; the name must also avoid
 * starting with {@code Test}.
 *
 * <p><b>This class is in the {@code test-support} group on purpose, and that group is deliberately not
 * part of {@code smoke}, {@code negative} or {@code regression}.</b>
 *
 * <p>The reason is that {@code /api/test-support/reset} deletes every row in the database. The regression
 * suite runs with {@code parallel=methods} and four threads, and every other test builds its own customer,
 * policy and invoice. If this test ran alongside them, it would delete the fixtures those tests were in
 * the middle of using, and they would fail with 404s that had nothing to do with what they were checking
 * — the worst kind of flakiness, because the failure appears in innocent tests.
 *
 * <p>It therefore runs alone:
 *
 * <pre>
 *   mvn -B verify -pl qa-api-tests -Dapi.groups=test-support
 * </pre>
 *
 * <p>This endpoint was untested until a coverage report showed
 * {@code TestSupportController.reset()} at zero lines covered. That is coverage doing its job: the gap was
 * invisible from the test list, because nothing looked missing.
 */
/*
 * singleThreaded because every method here wipes the database. The suite runs four threads, and a reset
 * landing between another method's setup and its assertion looks exactly like a broken reset - the term
 * it just created is gone, and the failure reads 404 rather than "these two tests raced".
 */
@Test(singleThreaded = true)
public class ResetEndpointIT extends BaseApiTest {

  /**
   * Invoices in the baseline: six raised directly against a policy, and three raised by the installments
   * of the seeded schedule that have already been drawn.
   */
  private static final int SEEDED_INVOICE_COUNT = 9;

  private static final int SEEDED_ACCOUNT_COUNT = 2;
  private static final int SEEDED_TERM_COUNT = 2;

  @Test(groups = "test-support")
  public void resetRestoresTheSeededBaseline() {
    // Diverge from the baseline first: add an invoice and pay part of a new one, so a reset that did
    // nothing at all could not be mistaken for a reset that worked.
    InvoiceDto extra = testData.unpaidInvoice("777.00");
    invoices.pay(extra.id(), "100.00");

    List<InvoiceDto> before = invoices.list();
    assertThat(before.size())
        .as("the extra fixtures should have pushed the count above the seeded baseline")
        .isGreaterThan(SEEDED_INVOICE_COUNT);

    Response response =
        given().spec(ApiSpecs.request()).when().post("/api/test-support/reset");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getString("status")).isEqualTo("reset");
    assertThat(response.jsonPath().getInt("invoices")).isEqualTo(SEEDED_INVOICE_COUNT);
    assertThat(response.jsonPath().getInt("accounts")).isEqualTo(SEEDED_ACCOUNT_COUNT);
    assertThat(response.jsonPath().getInt("terms")).isEqualTo(SEEDED_TERM_COUNT);

    List<InvoiceDto> after = invoices.list();
    assertThat(after).hasSize(SEEDED_INVOICE_COUNT);
    assertThat(after)
        .extracting(InvoiceDto::invoiceNumber)
        .as("every invoice should be a seeded one again")
        .allSatisfy(number -> assertThat(number).startsWith("SEED-"));
  }

  @Test(groups = "test-support")
  public void resetRestoresTheSeededSchedulesAndLedgersToo() {
    // Move money on both seeded terms, so a reset that restored only the invoices would be caught.
    billing.pay("SEED-TERM-001", "132.80");
    billing.pay("SEED-TERM-002", "92.83");

    given().spec(ApiSpecs.request()).when().post("/api/test-support/reset").then().statusCode(200);

    assertThat(billing.account("ACCT-100001").totalBalance())
        .as("the evenly divisible term owes its whole schedule less the two payments in the baseline")
        .isEqualByComparingTo("1328.00");
    assertThat(billing.account("ACCT-100002").totalBalance())
        .as("the uneven term owes its schedule, restored after a returned payment, plus the fee")
        .isEqualByComparingTo("1137.00");
    assertThat(billing.account("ACCT-100002").nsfCount())
        .as("the returned payment is part of the baseline, not something a test left behind")
        .isEqualTo(1);
  }

  @Test(groups = "test-support")
  public void theSeededBaselineCoversEveryInstallmentState() {
    given().spec(ApiSpecs.request()).when().post("/api/test-support/reset").then().statusCode(200);

    assertThat(billing.schedule("SEED-TERM-001"))
        .extracting(com.insurancebilling.qa.api.model.InstallmentDto::status)
        .as("the baseline exists to give every schedule state a worked example")
        .contains("PAID", "SCHEDULED");
    assertThat(billing.schedule("SEED-TERM-002"))
        .extracting(com.insurancebilling.qa.api.model.InstallmentDto::status)
        .as("only a returned payment produces a reversed installment")
        .contains("REVERSED");
  }

  @Test(groups = "test-support")
  public void theSeededBaselineCoversEveryInvoiceState() {
    given().spec(ApiSpecs.request()).when().post("/api/test-support/reset").then().statusCode(200);

    assertThat(invoices.list())
        .extracting(InvoiceDto::status)
        .as("the baseline exists to give every status a worked example")
        .contains("UNPAID", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED");
  }
}
