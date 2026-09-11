package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Integration coverage for the customer and policy endpoints, including their failure paths. */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerPolicyApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private ApiTestClient api;

  @BeforeEach
  void setUp() {
    api = new ApiTestClient(mockMvc, objectMapper);
  }

  @Test
  @DisplayName("creating a customer returns 201 with the persisted representation")
  void createCustomerReturns201() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"firstName":"Marie","lastName":"Dubois","email":"marie.dubois.api@example.com"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.firstName").value("Marie"))
        .andExpect(jsonPath("$.email").value("marie.dubois.api@example.com"));
  }

  @Test
  @DisplayName("a missing required field returns 400 naming the offending field")
  void missingFieldReturns400WithFieldError() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Marie","email":"no.last.name@example.com"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.lastName").value("lastName is required"));
  }

  @Test
  @DisplayName("an invalid email shape returns 400")
  void invalidEmailReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName":"Marie","lastName":"Dubois","email":"not-an-email"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.email").value("email must be a valid address"));
  }

  @Test
  @DisplayName("reusing an email returns 409 rather than 400")
  void duplicateEmailReturns409() throws Exception {
    String body =
        """
        {"firstName":"Duplicate","lastName":"Tester","email":"duplicate.tester@example.com"}
        """;

    mockMvc
        .perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mockMvc
        .perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }

  @Test
  @DisplayName("an unknown customer id returns 404")
  void unknownCustomerReturns404() throws Exception {
    mockMvc
        .perform(get("/api/customers/999999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("a non-numeric customer id returns 400, not 404")
  void nonNumericCustomerIdReturns400() throws Exception {
    mockMvc
        .perform(get("/api/customers/not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  @DisplayName("creating a policy returns 201 with a generated policy number")
  void createPolicyReturns201() throws Exception {
    long customerId = api.createCustomer();
    LocalDate start = LocalDate.now();

    mockMvc
        .perform(
            post("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":%d,"type":"HOME","annualPremium":960.00,
                     "startDate":"%s","endDate":"%s"}
                    """
                        .formatted(customerId, start, start.plusYears(1))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.policyNumber").value(org.hamcrest.Matchers.startsWith("POL-")))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.customerId").value((int) customerId));
  }

  @Test
  @DisplayName("creating a policy for an unknown customer returns 404")
  void createPolicyForUnknownCustomerReturns404() throws Exception {
    LocalDate start = LocalDate.now();

    mockMvc
        .perform(
            post("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":999999,"type":"AUTO","annualPremium":100.00,
                     "startDate":"%s","endDate":"%s"}
                    """
                        .formatted(start, start.plusYears(1))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("a zero premium is rejected with 400")
  void zeroPremiumReturns400() throws Exception {
    long customerId = api.createCustomer();
    LocalDate start = LocalDate.now();

    mockMvc
        .perform(
            post("/api/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"customerId":%d,"type":"AUTO","annualPremium":0.00,
                     "startDate":"%s","endDate":"%s"}
                    """
                        .formatted(customerId, start, start.plusYears(1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.annualPremium").exists());
  }

  @Test
  @DisplayName("a customer's policies are listed")
  void customerPoliciesAreListed() throws Exception {
    long customerId = api.createCustomer();
    api.createPolicy(customerId);
    api.createPolicy(customerId);

    mockMvc
        .perform(get("/api/customers/{id}/policies", customerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("listing policies for an unknown customer returns 404 rather than an empty list")
  void policiesForUnknownCustomerReturns404() throws Exception {
    mockMvc
        .perform(get("/api/customers/999999/policies"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("the seeded baseline is available for read-only scenarios")
  void seededCustomersAreAvailable() throws Exception {
    mockMvc
        .perform(get("/api/customers"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.email == 'alice.tremblay@example.com')]")
                .exists());
  }
}
