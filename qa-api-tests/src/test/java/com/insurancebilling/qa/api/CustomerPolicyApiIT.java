package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.ApiError;
import com.insurancebilling.qa.api.model.CustomerDto;
import com.insurancebilling.qa.api.model.PolicyDto;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testng.annotations.Test;

/** Customer and policy coverage, including the validation and conflict paths. */
public class CustomerPolicyApiIT extends BaseApiTest {

  @Test(groups = {"smoke", "regression"})
  public void aCustomerCanBeCreatedAndRetrieved() {
    String email = testData.uniqueEmail();

    CustomerDto created = customers.create("Amira", "Haddad", email);

    assertThat(created.id()).isNotNull();
    assertThat(created.email()).isEqualTo(email);
    assertThat(customers.get(created.id())).isEqualTo(created);
  }

  @Test(groups = {"smoke", "regression"})
  public void aPolicyCanBeCreatedForACustomer() {
    CustomerDto customer = testData.customer();
    LocalDate start = LocalDate.now();

    PolicyDto policy =
        policies.create(customer.id(), "HOME", new BigDecimal("960.00"), start, start.plusYears(1));

    assertThat(policy.policyNumber()).startsWith("POL-");
    assertThat(policy.status()).isEqualTo("ACTIVE");
    assertThat(policy.customerId()).isEqualTo(customer.id());
    assertThat(policy.customerName()).isEqualTo("QA Tester");
  }

  @Test(groups = "regression")
  public void aCustomersPoliciesAreListed() {
    CustomerDto customer = testData.customer();
    LocalDate start = LocalDate.now();
    policies.create(customer.id(), "AUTO", new BigDecimal("1200.00"), start, start.plusYears(1));
    policies.create(customer.id(), "LIFE", new BigDecimal("2400.00"), start, start.plusYears(1));

    assertThat(customers.policiesOf(customer.id())).hasSize(2);
  }

  @Test(groups = {"smoke", "regression"})
  public void aPolicyCanBeRetrievedById() {
    PolicyDto created = testData.activePolicy();

    PolicyDto fetched = policies.get(created.id());

    assertThat(fetched.policyNumber()).isEqualTo(created.policyNumber());
    assertThat(fetched.status()).isEqualTo("ACTIVE");
    assertThat(fetched.annualPremium()).isEqualByComparingTo(created.annualPremium());
    assertThat(fetched.customerName()).isEqualTo(created.customerName());
  }

  @Test(groups = {"negative", "regression"})
  public void anUnknownPolicyIsNotFound() {
    Response response = policies.getRaw(999_999_999L);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.as(ApiError.class).code()).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void aMissingRequiredFieldNamesTheOffendingField() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("firstName", "NoLastName");
    body.put("email", testData.uniqueEmail());

    Response response = customers.createRaw(body);

    assertThat(response.statusCode()).isEqualTo(400);
    ApiError error = response.as(ApiError.class);
    assertThat(error.code()).isEqualTo("VALIDATION_FAILED");
    assertThat(error.fieldErrors()).containsEntry("lastName", "lastName is required");
  }

  @Test(groups = {"negative", "regression"})
  public void anInvalidEmailIsRejected() {
    Response response = customers.createRaw("Bad", "Email", "not-an-email");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.as(ApiError.class).fieldErrors())
        .containsEntry("email", "email must be a valid address");
  }

  @Test(groups = {"negative", "regression"})
  public void reusingAnEmailIsAConflictNotAValidationFailure() {
    String email = testData.uniqueEmail();
    customers.create("First", "Claimant", email);

    Response response = customers.createRaw("Second", "Claimant", email);

    assertThat(response.statusCode())
        .as("a well-formed request that clashes with existing state is 409")
        .isEqualTo(409);
    assertThat(response.as(ApiError.class).code()).isEqualTo("DUPLICATE_EMAIL");
  }

  @Test(groups = {"negative", "regression"})
  public void anUnknownCustomerIsNotFound() {
    Response response = customers.getRaw(999_999_999L);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.as(ApiError.class).code()).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void aNonNumericCustomerIdIsABadRequestNotANotFound() {
    Response response = customers.getRaw("not-a-number");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.as(ApiError.class).code()).isEqualTo("MALFORMED_REQUEST");
  }

  @Test(groups = {"negative", "regression"})
  public void listingPoliciesForAnUnknownCustomerIsNotFoundRatherThanAnEmptyList() {
    Response response = customers.policiesOfRaw(999_999_999L);

    assertThat(response.statusCode())
        .as("an empty list would imply the customer exists with no policies")
        .isEqualTo(404);
  }

  @Test(groups = {"negative", "regression"})
  public void aPolicyForAnUnknownCustomerIsNotFound() {
    LocalDate start = LocalDate.now();

    Response response =
        policies.createRaw(999_999_999L, "AUTO", new BigDecimal("100.00"), start, start.plusYears(1));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.as(ApiError.class).code()).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void aZeroPremiumIsRejected() {
    CustomerDto customer = testData.customer();
    LocalDate start = LocalDate.now();

    Response response =
        policies.createRaw(customer.id(), "AUTO", new BigDecimal("0.00"), start, start.plusYears(1));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.as(ApiError.class).fieldErrors()).containsKey("annualPremium");
  }
}
