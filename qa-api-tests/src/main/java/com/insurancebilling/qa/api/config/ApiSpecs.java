package com.insurancebilling.qa.api.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.LogConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.specification.RequestSpecification;

/**
 * The single place that knows how to talk to the billing API.
 *
 * <p>Every request in the suite is built from {@link #request()}. Base URI, content type, JSON mapping
 * and logging policy are configured once here, so a test body contains only the call it is making and
 * the assertion it is checking. Changing the target or the logging policy is a one-line edit rather
 * than a sweep through every test.
 *
 * <p>Logging is deliberately conditional: request and response are dumped only when an assertion
 * fails. Always-on logging makes a green run unreadable, and a failure that prints nothing is
 * undiagnosable from a CI log. This gives the diagnostic output exactly when it is needed.
 */
public final class ApiSpecs {

  private static final RestAssuredConfig CONFIG =
      RestAssuredConfig.config()
          .logConfig(
              LogConfig.logConfig().enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL))
          .objectMapperConfig(
              new ObjectMapperConfig(ObjectMapperType.JACKSON_2)
                  .jackson2ObjectMapperFactory((type, charset) -> objectMapper()));

  private ApiSpecs() {}

  /** A request pre-configured for the application under test. */
  public static RequestSpecification request() {
    return new RequestSpecBuilder()
        .setBaseUri(TestEnvironment.baseUrl())
        .setContentType(ContentType.JSON)
        .setAccept(ContentType.JSON)
        .setConfig(CONFIG)
        .build();
  }

  /**
   * Jackson configured for the API's payloads.
   *
   * <p>Unknown properties are ignored on purpose: a field added to a response should not break an
   * unrelated test. Asserting that a field is absent is a test's job, not the mapper's.
   */
  private static ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }
}
