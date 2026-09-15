package com.insurancebilling.qa.api.client;

import static io.restassured.RestAssured.given;

import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.BillingTransactionDto;
import com.insurancebilling.qa.api.model.InstallmentDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import com.insurancebilling.qa.api.model.TermPaymentBody;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;

/**
 * Billing accounts, terms, schedules and ledgers over HTTP.
 *
 * <p>Follows the same idiom as every other client here: a typed method that asserts the expected status
 * and deserializes, beside a raw {@code Response} twin for the tests that are about the refusal rather
 * than the result.
 */
public class BillingApiClient {

  private static final String ACCOUNTS = "/api/accounts";
  private static final String TERMS = "/api/terms";

  public BillingAccountDto openAccount(long customerId, String accountHolder, String lastDigits) {
    return openAccountRaw(
            Map.of(
                "customerId", customerId,
                "paymentPlan", "MONTHLY",
                "paymentMethod", "PRE_AUTHORIZED_DEBIT",
                "accountHolder", accountHolder,
                "accountLastDigits", lastDigits))
        .then()
        .statusCode(201)
        .extract()
        .as(BillingAccountDto.class);
  }

  public Response openAccountRaw(Map<String, Object> body) {
    return given().spec(ApiSpecs.request()).body(body).when().post(ACCOUNTS);
  }

  public PolicyTermDto bindTerm(
      String accountReference,
      long policyId,
      String effectiveDate,
      String premium,
      String tax,
      String fee) {
    return bindTermRaw(
            accountReference,
            Map.of(
                "policyId", policyId,
                "effectiveDate", effectiveDate,
                "paymentPlan", "MONTHLY",
                "termPremium", premium,
                "termTax", tax,
                "installmentFee", fee))
        .then()
        .statusCode(201)
        .extract()
        .as(PolicyTermDto.class);
  }

  public Response bindTermRaw(String accountReference, Map<String, Object> body) {
    return given()
        .spec(ApiSpecs.request())
        .body(body)
        .when()
        .post(ACCOUNTS + "/{reference}/terms", accountReference);
  }

  public BillingAccountDto account(String accountReference) {
    return accountRaw(accountReference)
        .then()
        .statusCode(200)
        .extract()
        .as(BillingAccountDto.class);
  }

  public Response accountRaw(String accountReference) {
    return given().spec(ApiSpecs.request()).when().get(ACCOUNTS + "/{reference}", accountReference);
  }

  public List<BillingAccountDto> accounts() {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .get(ACCOUNTS)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", BillingAccountDto.class);
  }

  public List<PolicyTermDto> termsOf(String accountReference) {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .get(ACCOUNTS + "/{reference}/terms", accountReference)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", PolicyTermDto.class);
  }

  public PolicyTermDto term(String termReference) {
    return termRaw(termReference).then().statusCode(200).extract().as(PolicyTermDto.class);
  }

  public Response termRaw(String termReference) {
    return given().spec(ApiSpecs.request()).when().get(TERMS + "/{reference}", termReference);
  }

  public List<InstallmentDto> schedule(String termReference) {
    return scheduleRaw(termReference)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", InstallmentDto.class);
  }

  public Response scheduleRaw(String termReference) {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .get(TERMS + "/{reference}/schedule", termReference);
  }

  public List<BillingTransactionDto> ledger(String termReference) {
    return ledgerRaw(termReference)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", BillingTransactionDto.class);
  }

  public Response ledgerRaw(String termReference) {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .get(TERMS + "/{reference}/transactions", termReference);
  }

  public BillingTransactionDto pay(String termReference, String amount) {
    return payRaw(termReference, TermPaymentBody.of(amount))
        .then()
        .statusCode(201)
        .extract()
        .as(BillingTransactionDto.class);
  }

  public Response payRaw(String termReference, TermPaymentBody body) {
    return given()
        .spec(ApiSpecs.request())
        .body(body)
        .when()
        .post(TERMS + "/{reference}/payments", termReference);
  }

  public Response payRaw(String termReference, Map<String, Object> body) {
    return given()
        .spec(ApiSpecs.request())
        .body(body)
        .when()
        .post(TERMS + "/{reference}/payments", termReference);
  }

  /** Sends a raw, possibly unparseable, request body. */
  public Response payWithRawBody(String termReference, String rawJson) {
    return given()
        .spec(ApiSpecs.request())
        .body(rawJson)
        .when()
        .post(TERMS + "/{reference}/payments", termReference);
  }

  public BillingTransactionDto returnPayment(String transactionReference, String reason) {
    return returnPaymentRaw(transactionReference, reason)
        .then()
        .statusCode(201)
        .extract()
        .as(BillingTransactionDto.class);
  }

  public Response returnPaymentRaw(String transactionReference, String reason) {
    return given()
        .spec(ApiSpecs.request())
        .body(Map.of("reason", reason))
        .when()
        .post(TERMS + "/transactions/{reference}/return", transactionReference);
  }
}
