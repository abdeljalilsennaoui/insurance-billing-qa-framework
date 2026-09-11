package com.insurancebilling.qa.api.client;

import static io.restassured.RestAssured.given;

import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.model.PolicyDto;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Policy endpoints, including the status change the refusal scenarios rely on. */
public class PolicyApiClient {

  private static final String POLICIES = "/api/policies";

  public PolicyDto create(
      long customerId, String type, BigDecimal premium, LocalDate startDate, LocalDate endDate) {
    return createRaw(customerId, type, premium, startDate, endDate)
        .then()
        .statusCode(201)
        .extract()
        .as(PolicyDto.class);
  }

  public Response createRaw(
      long customerId, String type, BigDecimal premium, LocalDate startDate, LocalDate endDate) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("customerId", customerId);
    body.put("type", type);
    body.put("annualPremium", premium);
    body.put("startDate", startDate.toString());
    body.put("endDate", endDate.toString());
    return createRaw(body);
  }

  public Response createRaw(Map<String, Object> body) {
    return given().spec(ApiSpecs.request()).body(body).when().post(POLICIES);
  }

  public PolicyDto get(long id) {
    return getRaw(id).then().statusCode(200).extract().as(PolicyDto.class);
  }

  public Response getRaw(long id) {
    return given().spec(ApiSpecs.request()).when().get(POLICIES + "/{id}", id);
  }

  /** Moves a policy to a new lifecycle state, so a test can set up a refusal scenario. */
  public PolicyDto changeStatus(long policyId, String status) {
    return changeStatusRaw(policyId, status).then().statusCode(200).extract().as(PolicyDto.class);
  }

  public Response changeStatusRaw(long policyId, String status) {
    return given()
        .spec(ApiSpecs.request())
        .body(Map.of("status", status))
        .when()
        .patch(POLICIES + "/{id}/status", policyId);
  }
}
