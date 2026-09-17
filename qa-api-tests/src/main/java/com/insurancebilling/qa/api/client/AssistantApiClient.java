package com.insurancebilling.qa.api.client;

import static io.restassured.RestAssured.given;

import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.model.AssistantAnswerDto;
import io.restassured.response.Response;
import java.util.Map;

/**
 * The billing assistant, over HTTP.
 *
 * <p>Every method here drives the same endpoint the console drives. Nothing in this suite knows which
 * provider is answering: that is configuration on the application, and a test that had to know would
 * be a test of the configuration rather than of the endpoint.
 */
public class AssistantApiClient {

  private static final String PATH = "/api/accounts/{reference}/assistant";

  /** Asks a question, asserting only that the platform answered at all. */
  public AssistantAnswerDto ask(String accountReference, String question) {
    return given()
        .spec(ApiSpecs.request())
        .pathParam("reference", accountReference)
        .body(Map.of("question", question))
        .when()
        .post(PATH)
        .then()
        .statusCode(200)
        .extract()
        .as(AssistantAnswerDto.class);
  }

  /** The raw response, for the negative paths where the status is the thing under test. */
  public Response askRaw(String accountReference, String question) {
    return given()
        .spec(ApiSpecs.request())
        .pathParam("reference", accountReference)
        .body(Map.of("question", question))
        .when()
        .post(PATH);
  }
}
