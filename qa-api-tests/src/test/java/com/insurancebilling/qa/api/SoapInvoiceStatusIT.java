package com.insurancebilling.qa.api;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.config.TestEnvironment;
import com.insurancebilling.qa.api.model.InvoiceDto;
import io.restassured.path.xml.XmlPath;
import io.restassured.response.Response;
import org.testng.annotations.Test;

/**
 * SOAP coverage for the invoice status service.
 *
 * <p>These tests post a real SOAP envelope as text and parse the XML that comes back, rather than using
 * a generated client. That is deliberate: a generated client would be built from the same XSD the server
 * uses, so a contract that had drifted would still appear to work on both sides. Sending the envelope a
 * partner would actually send is the only way to prove the published contract is the one being served.
 */
public class SoapInvoiceStatusIT extends BaseApiTest {

  private static final String NAMESPACE = "http://insurancebilling.com/billing/invoice-status";
  private static final String SOAP_PATH = "/ws";

  /**
   * GPath prefix for fields inside the response body.
   *
   * <p>Spelled out from the document root rather than using a wildcard. XmlPath is not namespace-aware
   * by default, so the {@code SOAP-ENV:} and {@code ns2:} prefixes on the wire are matched by local
   * name, and naming the full path makes it obvious which element each assertion reads.
   */
  private static final String RESPONSE = "Envelope.Body.GetInvoiceStatusResponse.";

  private static String getInvoiceStatusEnvelope(String invoiceNumber) {
    return """
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
          <soap:Body>
            <ins:GetInvoiceStatusRequest xmlns:ins="%s">
              <ins:invoiceNumber>%s</ins:invoiceNumber>
            </ins:GetInvoiceStatusRequest>
          </soap:Body>
        </soap:Envelope>
        """
        .formatted(NAMESPACE, invoiceNumber);
  }

  /**
   * Posts an envelope exactly as a SOAP 1.1 client would.
   *
   * <p>The content type is spelled out as {@code text/xml} rather than using REST Assured's
   * {@code ContentType.XML}, which sends {@code application/xml}. SOAP 1.1 specifies {@code text/xml},
   * and Spring WS refuses anything else — the first version of this test sent {@code application/xml}
   * and every call came back non-200 while the same envelope worked from curl. Sending what a real
   * client sends is also the more honest test.
   */
  private Response postEnvelope(String envelope) {
    return given()
        .baseUri(TestEnvironment.baseUrl())
        .contentType("text/xml; charset=utf-8")
        .accept("text/xml")
        .body(envelope)
        .when()
        .post(SOAP_PATH);
  }

  @Test(groups = {"soap", "regression"})
  public void theWsdlIsPublishedFromTheSchema() {
    Response response =
        given().baseUri(TestEnvironment.baseUrl()).when().get("/ws/invoiceStatus.wsdl");

    assertThat(response.statusCode()).isEqualTo(200);
    String wsdl = response.asString();
    assertThat(wsdl)
        .as("the WSDL should be generated from the XSD and describe the operation")
        .contains("GetInvoiceStatusRequest")
        .contains("GetInvoiceStatusResponse")
        .contains("InvoiceStatusPort")
        .contains(NAMESPACE);
  }

  @Test(groups = {"soap", "smoke", "regression"})
  public void anUnpaidInvoiceIsReportedOverTheSoapService() {
    InvoiceDto invoice = testData.unpaidInvoice("450.00");

    Response response = postEnvelope(getInvoiceStatusEnvelope(invoice.invoiceNumber()));

    assertThat(response.statusCode()).isEqualTo(200);
    XmlPath xml = response.xmlPath();
    assertThat(xml.getString(RESPONSE + "invoiceNumber")).isEqualTo(invoice.invoiceNumber());
    assertThat(xml.getString(RESPONSE + "status")).isEqualTo("UNPAID");
    assertThat(xml.getDouble(RESPONSE + "totalAmount")).isEqualTo(450.00);
    assertThat(xml.getDouble(RESPONSE + "amountPaid")).isEqualTo(0.00);
    assertThat(xml.getDouble(RESPONSE + "outstandingBalance")).isEqualTo(450.00);
    assertThat(xml.getBoolean(RESPONSE + "overdue")).isFalse();
  }

  @Test(groups = {"soap", "regression"})
  public void soapAndRestReportTheSameBalanceForTheSameInvoice() {
    InvoiceDto invoice = testData.partiallyPaidInvoice("500.00", "125.00");

    Response response = postEnvelope(getInvoiceStatusEnvelope(invoice.invoiceNumber()));
    XmlPath xml = response.xmlPath();

    // The point of this test is that two protocols over one domain cannot disagree. If the SOAP
    // endpoint ever computed its own totals, this is what would catch it.
    assertThat(xml.getString(RESPONSE + "status")).isEqualTo("PARTIALLY_PAID");
    assertThat(xml.getDouble(RESPONSE + "amountPaid"))
        .isEqualTo(invoice.amountPaid().doubleValue());
    assertThat(xml.getDouble(RESPONSE + "outstandingBalance"))
        .isEqualTo(invoice.outstandingBalance().doubleValue());
  }

  @Test(groups = {"soap", "regression"})
  public void aSettledInvoiceIsReportedAsPaidWithNothingOutstanding() {
    InvoiceDto invoice = testData.settledInvoice("200.00");

    XmlPath xml = postEnvelope(getInvoiceStatusEnvelope(invoice.invoiceNumber())).xmlPath();

    assertThat(xml.getString(RESPONSE + "status")).isEqualTo("PAID");
    assertThat(xml.getDouble(RESPONSE + "outstandingBalance")).isEqualTo(0.00);
    assertThat(xml.getBoolean(RESPONSE + "overdue")).isFalse();
  }

  @Test(groups = {"soap", "regression"})
  public void anOverdueInvoiceIsFlaggedOverTheSoapService() {
    InvoiceDto invoice = testData.overdueInvoice("200.00");

    XmlPath xml = postEnvelope(getInvoiceStatusEnvelope(invoice.invoiceNumber())).xmlPath();

    assertThat(xml.getString(RESPONSE + "status")).isEqualTo("OVERDUE");
    assertThat(xml.getBoolean(RESPONSE + "overdue")).isTrue();
  }

  @Test(groups = {"soap", "negative", "regression"})
  public void anUnknownInvoiceReturnsASoapFaultRatherThanAnEmptyResponse() {
    Response response = postEnvelope(getInvoiceStatusEnvelope("INV-DOES-NOT-EXIST"));

    // SOAP 1.1 reports a fault with HTTP 500; the meaningful assertion is the fault itself.
    assertThat(response.statusCode()).isEqualTo(500);

    String body = response.asString();
    assertThat(body).contains("Fault");
    assertThat(body)
        .as("the fault should name the invoice so a partner can log which query failed")
        .contains("INV-DOES-NOT-EXIST");
    assertThat(body)
        .as("an unknown invoice is the caller's mistake, not a server failure, so the code is Client")
        .containsIgnoringCase("Client");
  }

  @Test(groups = {"soap", "negative", "regression"})
  public void anEmptyInvoiceNumberIsRefusedByTheContract() {
    Response response = postEnvelope(getInvoiceStatusEnvelope(""));

    assertThat(response.statusCode())
        .as("the schema constrains invoiceNumber to at least one character")
        .isNotEqualTo(200);
  }
}
