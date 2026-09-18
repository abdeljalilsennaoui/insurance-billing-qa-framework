package com.insurancebilling.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

/**
 * The Gemini provider, exercised end to end against a stubbed API.
 *
 * <p>Same bargain as {@code AnthropicBillingAssistantTest}: no key, no network, no cost, and the real
 * adapter rather than a mock of it. The stub is a {@link MockWebServer} on localhost and the client is
 * the one the application builds, so the tool loop, the usage accounting, the finish-reason handling
 * and the error mapping under test are the shipped ones.
 *
 * <p>Two assertions here are about the request rather than the answer, and they are the ones that
 * matter most: the tools sent are the registry's, and a tool result is returned with the {@code id} of
 * the call it answers. A model that is told about a tool nobody registered, or handed a result it
 * cannot pair with its question, fails in ways that look like the model being unreliable.
 *
 * <p>The tool results are real - {@link BillingReadTools} runs against the seeded database - so the
 * figures fed back to the stub are the ones the application actually holds.
 */
@SpringBootTest
class GeminiBillingAssistantTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private BillingReadTools tools;

  private MockWebServer server;
  private GeminiBillingAssistant assistant;

  @BeforeEach
  void startStub() throws Exception {
    server = new MockWebServer();
    server.start();
    RestClient client =
        RestClient.builder()
            .baseUrl(server.url("/").toString())
            .defaultHeader("x-goog-api-key", "test-key-not-a-real-credential")
            .build();
    assistant = new GeminiBillingAssistant(client, tools, "gemini-3.8-flash", 1024L);
  }

  @AfterEach
  void stopStub() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("an answer with no tool call comes back as the model wrote it")
  void aPlainAnswerIsReturned() {
    enqueue(answer("Your balance is 1328.00.", 120, 40, 0));

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.available()).isTrue();
    assertThat(answer.text()).isEqualTo("Your balance is 1328.00.");
    assertThat(answer.provider()).isEqualTo("gemini");
    assertThat(answer.usage().inputTokens()).isEqualTo(120);
    assertThat(answer.usage().outputTokens()).isEqualTo(40);
  }

  @Test
  @DisplayName("the tools offered to the model are the registry's, with its own schemas")
  void theToolsOfferedAreTheRegistrys() throws Exception {
    enqueue(answer("No tools needed.", 10, 5, 0));

    assistant.ask(question());

    JsonNode declarations = bodyOf(server.takeRequest()).path("tools").path(0).path("functionDeclarations");
    assertThat(declarations).hasSize(tools.definitions().size());
    for (int index = 0; index < tools.definitions().size(); index++) {
      BillingToolDefinition definition = tools.definitions().get(index);
      assertThat(declarations.path(index).path("name").asText()).isEqualTo(definition.name());
      assertThat(declarations.path(index).path("parameters").path("required").path(0).asText())
          .isEqualTo(definition.argument());
    }
  }

  @Test
  @DisplayName("a tool call is run and its result returned against the id of the call")
  void aToolCallIsRunAndAnswered() throws Exception {
    enqueue(toolCall("find_account", "accountReference", "ACCT-100001", "call_1"));
    enqueue(answer("Your balance is 1328.00.", 200, 30, 0));

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.available()).isTrue();
    assertThat(answer.toolCalls())
        .extracting(AssistantToolCall::tool, AssistantToolCall::argument)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("find_account", "ACCT-100001"));

    server.takeRequest();
    JsonNode second = bodyOf(server.takeRequest());
    JsonNode functionResponse =
        second.path("contents").path(2).path("parts").path(0).path("functionResponse");
    assertThat(functionResponse.path("name").asText()).isEqualTo("find_account");
    assertThat(functionResponse.path("id").asText()).isEqualTo("call_1");
    assertThat(functionResponse.path("response").path("result").path("accountReference").asText())
        .isEqualTo("ACCT-100001");
  }

  @Test
  @DisplayName("the usage of every round is added up, not just the last one")
  void usageIsAccumulatedAcrossRounds() {
    enqueue(toolCall("find_account", "accountReference", "ACCT-100001", "call_1"));
    enqueue(answer("Your balance is 1328.00.", 200, 30, 12));

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.usage().inputTokens()).isEqualTo(300);
    assertThat(answer.usage().outputTokens()).isEqualTo(55);
    assertThat(answer.usage().cacheReadTokens()).isEqualTo(12);
  }

  /**
   * A tool that throws is reported to the model rather than dropped.
   *
   * <p>Dropping it would leave the model waiting for a result that never arrives, and it would spend
   * the rest of the loop asking again. The error is sent back as the result of that call.
   */
  @Test
  void aToolThatFailsIsReportedBackToTheModel() throws Exception {
    enqueue(toolCall("find_account", "accountReference", "ACCT-NOT-A-REAL-ONE", "call_1"));
    enqueue(answer("I could not find that account.", 50, 10, 0));

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.available()).isTrue();
    server.takeRequest();
    JsonNode functionResponse =
        bodyOf(server.takeRequest()).path("contents").path(2).path("parts").path(0).path("functionResponse");
    assertThat(functionResponse.path("response").path("error").asText()).contains("was not found");
  }

  @Test
  @DisplayName("a response cut off at the token ceiling is unavailable, not a half answer")
  void aTruncatedAnswerIsUnavailable() {
    enqueue(
        """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"Your balance is"}]},
          "finishReason":"MAX_TOKENS"}],
         "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":1024}}
        """);

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.available()).isFalse();
    assertThat(answer.text()).contains("too long");
  }

  @Test
  @DisplayName("a safety refusal is unavailable rather than an empty answer")
  void aRefusalIsUnavailable() {
    enqueue(
        """
        {"candidates":[{"content":{"role":"model","parts":[]},"finishReason":"SAFETY"}],
         "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":0}}
        """);

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.available()).isFalse();
    assertThat(answer.text()).contains("declined");
  }

  @Test
  @DisplayName("a rate limit is an unavailable answer, never an exception out of the console")
  void aRateLimitIsUnavailable() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":429}}"));

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.available()).isFalse();
    assertThat(answer.text()).contains("unavailable");
  }

  /**
   * The loop has a ceiling, and this proves it stops rather than that it is believed to.
   *
   * <p>A model that keeps asking for data forever costs money for as long as it does. The failure mode
   * of a runaway agent is a bill, not a crash, so nothing would alert on it.
   */
  @Test
  void aModelThatNeverStopsCallingToolsIsGivenUpOn() {
    for (int round = 0; round < 12; round++) {
      enqueue(toolCall("find_account", "accountReference", "ACCT-100001", "call_" + round));
    }

    AssistantAnswer answer = assistant.ask(question());

    assertThat(answer.available()).isFalse();
    assertThat(answer.text()).contains("did not reach an answer");
    assertThat(server.getRequestCount()).isLessThanOrEqualTo(8);
  }

  @Test
  @DisplayName("the shared system prompt is what the model is instructed with")
  void theSharedSystemPromptIsSent() throws Exception {
    enqueue(answer("Fine.", 10, 5, 0));

    assistant.ask(question());

    JsonNode instruction = bodyOf(server.takeRequest()).path("systemInstruction").path("parts").path(0);
    assertThat(instruction.path("text").asText())
        .isEqualTo(AssistantSystemPrompt.TEXT)
        .contains("must come from a tool result");
  }

  private AssistantQuestion question() {
    return new AssistantQuestion(
        "Why is my balance what it is?", "ACCT-100001", Locale.forLanguageTag("en-CA"));
  }

  private void enqueue(String body) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("content-type", "application/json")
            .setBody(body));
  }

  private JsonNode bodyOf(RecordedRequest request) throws Exception {
    return JSON.readTree(request.getBody().readUtf8());
  }

  private static String answer(String text, int input, int output, int cached) {
    return """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"%s"}]},"finishReason":"STOP"}],
         "usageMetadata":{"promptTokenCount":%d,"candidatesTokenCount":%d,"cachedContentTokenCount":%d}}
        """
        .formatted(text, input, output, cached);
  }

  private static String toolCall(String tool, String argumentName, String argument, String id) {
    return """
        {"candidates":[{"content":{"role":"model","parts":[
           {"functionCall":{"id":"%s","name":"%s","args":{"%s":"%s"}}}]},"finishReason":"STOP"}],
         "usageMetadata":{"promptTokenCount":100,"candidatesTokenCount":25}}
        """
        .formatted(id, tool, argumentName, argument);
  }
}
