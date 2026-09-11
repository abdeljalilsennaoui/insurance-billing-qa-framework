package com.insurancebilling.qa.api.client;

import static io.restassured.RestAssured.given;

import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.model.CustomerDto;
import com.insurancebilling.qa.api.model.PolicyDto;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;

/**
 * Customer endpoints.
 *
 * <p>Each operation comes in two forms: a typed method that asserts the expected status and returns a
 * deserialised object, for tests whose subject is something else, and a raw {@code Response} method for
 * tests whose subject *is* the response. Without the raw form, a negative test could not inspect an
 * error body; without the typed form, every happy-path test would repeat the same extraction.
 */
public class CustomerApiClient {

  private static final String CUSTOMERS = "/api/customers";

  public CustomerDto create(String firstName, String lastName, String email) {
    return createRaw(firstName, lastName, email).then().statusCode(201).extract().as(CustomerDto.class);
  }

  public Response createRaw(String firstName, String lastName, String email) {
    return given()
        .spec(ApiSpecs.request())
        .body(Map.of("firstName", firstName, "lastName", lastName, "email", email))
        .when()
        .post(CUSTOMERS);
  }

  public Response createRaw(Map<String, Object> body) {
    return given().spec(ApiSpecs.request()).body(body).when().post(CUSTOMERS);
  }

  public CustomerDto get(long id) {
    return getRaw(id).then().statusCode(200).extract().as(CustomerDto.class);
  }

  public Response getRaw(long id) {
    return given().spec(ApiSpecs.request()).when().get(CUSTOMERS + "/{id}", id);
  }

  public Response getRaw(String rawId) {
    return given().spec(ApiSpecs.request()).when().get(CUSTOMERS + "/{id}", rawId);
  }

  public List<CustomerDto> list() {
    return given()
        .spec(ApiSpecs.request())
        .when()
        .get(CUSTOMERS)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", CustomerDto.class);
  }

  public List<PolicyDto> policiesOf(long customerId) {
    return policiesOfRaw(customerId)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getList(".", PolicyDto.class);
  }

  public Response policiesOfRaw(long customerId) {
    return given().spec(ApiSpecs.request()).when().get(CUSTOMERS + "/{id}/policies", customerId);
  }
}
