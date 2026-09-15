package com.insurancebilling.qa.api;

import com.insurancebilling.qa.api.client.BillingApiClient;
import com.insurancebilling.qa.api.client.CustomerApiClient;
import com.insurancebilling.qa.api.client.InvoiceApiClient;
import com.insurancebilling.qa.api.client.PolicyApiClient;
import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.config.TestEnvironment;
import com.insurancebilling.qa.api.data.BillingTestData;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.testng.annotations.BeforeSuite;

/**
 * Shared setup for the API suite.
 *
 * <p>The reachability check in {@link #verifyApplicationIsReachable()} exists so that pointing the
 * suite at a stopped application produces one clear message instead of dozens of connection-refused
 * stack traces that all look like test failures. Distinguishing "the environment is wrong" from "the
 * application is broken" in the first second of a run saves real debugging time.
 *
 * <p>Clients and the data builder are instance fields with no shared mutable state, so TestNG can run
 * methods in parallel without tests colliding.
 */
public abstract class BaseApiTest {

  protected final CustomerApiClient customers = new CustomerApiClient();
  protected final PolicyApiClient policies = new PolicyApiClient();
  protected final InvoiceApiClient invoices = new InvoiceApiClient();
  protected final BillingApiClient billing = new BillingApiClient();
  protected final BillingTestData testData = new BillingTestData();

  @BeforeSuite(alwaysRun = true)
  public void verifyApplicationIsReachable() {
    try {
      io.restassured.RestAssured.given()
          .spec(ApiSpecs.request())
          .accept(ContentType.ANY)
          .when()
          .get("/actuator/health")
          .then()
          .statusCode(200);
    } catch (RuntimeException unreachable) {
      throw new IllegalStateException(
          "The application under test is not reachable at "
              + TestEnvironment.baseUrl()
              + ". Start it first (see README) or point the suite elsewhere with -Dapp.base.url=...",
          unreachable);
    }
    RestAssured.reset();
  }
}
