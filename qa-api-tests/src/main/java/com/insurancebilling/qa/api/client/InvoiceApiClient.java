package com.insurancebilling.qa.api.client;

import static io.restassured.RestAssured.given;

import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.api.model.PaymentDto;
import com.insurancebilling.qa.api.model.PaymentRequestBody;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Invoice and payment endpoints. */
public class InvoiceApiClient {

  private static final String INVOICES = "/api/invoices";

  public InvoiceDto create(long policyId, BigDecimal total, LocalDate issueDate, LocalDate dueDate) {
    return createRaw(policyId, total, issueDate, dueDate)
        .then()
        .statusCode(201)
        .extract()
        .as(InvoiceDto.class);
  }

  public Response createRaw(long policyId, BigDecimal total, LocalDate issueDate, LocalDate dueDate) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("policyId", policyId);
    body.put("totalAmount", total);
    body.put("issueDate", issueDate.toString());
    body.put("dueDate", dueDate.toString());
    return createRaw(body);
  }

  public Response createRaw(Map<String, Object> body) {
    return given().spec(ApiSpecs.request()).body(body).when().post(INVOICES);
  }

  public InvoiceDto get(long id) {
    return getRaw(id).then().statusCode(200).extract().as(InvoiceDto.class);
  }

  public Response getRaw(long id) {
    return given().spec(ApiSpecs.request()).when().get(INVOICES + "/{id}", id);
  }

  public List<InvoiceDto> list() {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .get(INVOICES)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", InvoiceDto.class);
  }

  public List<InvoiceDto> listByStatus(String status) {
    return listByStatusRaw(status)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", InvoiceDto.class);
  }

  public Response listByStatusRaw(String status) {
    return given().spec(ApiSpecs.request()).queryParam("status", status).when().get(INVOICES);
  }

  /** Pays an invoice, asserting the payment was accepted. */
  public PaymentDto pay(long invoiceId, String amount) {
    return payRaw(invoiceId, PaymentRequestBody.of(amount))
        .then()
        .statusCode(201)
        .extract()
        .as(PaymentDto.class);
  }

  public Response payRaw(long invoiceId, PaymentRequestBody body) {
    return given()
        .spec(ApiSpecs.request())
        .body(body)
        .when()
        .post(INVOICES + "/{id}/payments", invoiceId);
  }

  /** Sends an arbitrary payment body, for tests that need to send something malformed on purpose. */
  public Response payRaw(long invoiceId, Map<String, Object> body) {
    return given()
        .spec(ApiSpecs.request())
        .body(body)
        .when()
        .post(INVOICES + "/{id}/payments", invoiceId);
  }

  /** Sends a raw, possibly unparseable, request body. */
  public Response payWithRawBody(long invoiceId, String rawJson) {
    return given()
        .spec(ApiSpecs.request())
        .body(rawJson)
        .when()
        .post(INVOICES + "/{id}/payments", invoiceId);
  }

  public List<PaymentDto> payments(long invoiceId) {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .get(INVOICES + "/{id}/payments", invoiceId)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", PaymentDto.class);
  }

  public InvoiceDto cancel(long invoiceId) {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .post(INVOICES + "/{id}/cancellation", invoiceId)
        .then()
        .statusCode(200)
        .extract()
        .as(InvoiceDto.class);
  }
}
