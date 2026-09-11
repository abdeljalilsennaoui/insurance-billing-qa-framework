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
public class ResetEndpointIT extends BaseApiTest {

  private static final int SEEDED_INVOICE_COUNT = 6;

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

    List<InvoiceDto> after = invoices.list();
    assertThat(after).hasSize(SEEDED_INVOICE_COUNT);
    assertThat(after)
        .extracting(InvoiceDto::invoiceNumber)
        .as("every invoice should be a seeded one again")
        .allSatisfy(number -> assertThat(number).startsWith("SEED-INV-"));
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
