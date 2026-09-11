package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Creates test data through the public API for the integration tests.
 *
 * <p>Each helper generates a unique email and returns the created id, so a test never depends on a
 * seeded row and two tests running in the same context cannot collide on the email unique constraint.
 */
final class ApiTestClient {

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;

  ApiTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
  }

  long createCustomer() throws Exception {
    String email = "qa-" + UUID.randomUUID() + "@example.com";
    String body =
        """
        {"firstName":"Integration","lastName":"Tester","email":"%s"}
        """
            .formatted(email);
    return idOf(performJson("/api/customers", body));
  }

  long createPolicy(long customerId) throws Exception {
    LocalDate start = LocalDate.now().minusMonths(1);
    String body =
        """
        {"customerId":%d,"type":"AUTO","annualPremium":1200.00,
         "startDate":"%s","endDate":"%s"}
        """
            .formatted(customerId, start, start.plusYears(1));
    return idOf(performJson("/api/policies", body));
  }

  /** An invoice for the given total on a fresh customer and active policy. */
  long createInvoice(String totalAmount) throws Exception {
    return createInvoice(createPolicy(createCustomer()), totalAmount, LocalDate.now().plusDays(30));
  }

  long createInvoice(long policyId, String totalAmount, LocalDate dueDate) throws Exception {
    String body =
        """
        {"policyId":%d,"totalAmount":%s,"issueDate":"%s","dueDate":"%s"}
        """
            .formatted(policyId, totalAmount, LocalDate.now(), dueDate);
    return idOf(performJson("/api/invoices", body));
  }

  private String performJson(String path, String body) throws Exception {
    return mockMvc
        .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private long idOf(String responseBody) throws Exception {
    JsonNode node = objectMapper.readTree(responseBody);
    if (!node.has("id")) {
      throw new AssertionError("Expected a created resource with an id but got: " + responseBody);
    }
    return node.get("id").asLong();
  }
}
