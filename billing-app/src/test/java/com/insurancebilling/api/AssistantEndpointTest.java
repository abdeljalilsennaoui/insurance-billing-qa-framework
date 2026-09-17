package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The assistant endpoint, against the shipped {@code replay} provider.
 *
 * <p>Everything here runs with no API key and no network, which is the point of the provider port: the
 * endpoint a live configuration serves is the endpoint these assertions drive.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantEndpointTest {

  @Autowired private MockMvc mockMvc;

  private static String body(String question) {
    return "{\"question\":\"" + question + "\"}";
  }

  @Test
  @DisplayName("a recorded question is answered, with the calls behind the answer")
  void aRecordedQuestionIsAnswered() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts/ACCT-100001/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Why is my balance 1,328.00?")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.provider").value("replay"))
        .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("1328.00")))
        .andExpect(jsonPath("$.toolCalls[0].tool").value("find_account"))
        .andExpect(jsonPath("$.toolCalls[0].argument").value("ACCT-100001"));
  }

  @Test
  @DisplayName("a question nobody recorded is answered as unavailable, not as an error")
  void anUnrecordedQuestionIsUnavailableRatherThanAnError() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts/ACCT-100001/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("What is the airspeed of a swallow?")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.toolCalls").isEmpty());
  }

  @Test
  @DisplayName("an account that does not exist is a 404 from the platform, not an answer about it")
  void anAccountThatDoesNotExistIsA404() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts/ACCT-NOPE/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Why is my balance 1,328.00?")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("an empty question is refused before anything is asked of the assistant")
  void anEmptyQuestionIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts/ACCT-100001/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("   ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("a question longer than the cap is refused rather than billed for")
  void aQuestionLongerThanTheCapIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts/ACCT-100001/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("why ".repeat(200))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  @DisplayName("the usage figures are on the response, zero for a recorded answer")
  void theUsageFiguresAreOnTheResponse() throws Exception {
    mockMvc
        .perform(
            post("/api/accounts/ACCT-100001/assistant")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Why is my balance 1,328.00?")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usage.inputTokens").value(0))
        .andExpect(jsonPath("$.usage.outputTokens").value(0))
        .andExpect(jsonPath("$.usage.cacheReadTokens").value(0));
  }
}
