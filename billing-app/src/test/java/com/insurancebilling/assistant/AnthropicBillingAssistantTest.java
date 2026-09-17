package com.insurancebilling.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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

/**
 * The model-backed assistant, exercised end to end against a stubbed Messages API.
 *
 * <p>No API key, no network, no cost, and the same code that runs in production: the real SDK client
 * is pointed at a {@link MockWebServer} on localhost with its own {@code baseUrl}, so the tool loop,
 * the usage accounting, the stop-reason handling and the error mapping are all the real ones. The
 * alternative was mocking the SDK's own types, which would have proved that a mock behaves the way
 * the mock was told to.
 *
 * <p>This is also the answer to a coverage problem worth naming. The shipped configuration is
 * {@code replay}, so nothing in CI would otherwise call this class at all and it would read as dead
 * code in the report. Excluding it from JaCoCo would have made the number look right while leaving
 * the class untested, which is the opposite of what the number is for.
 *
 * <p>The tool results are real: {@link BillingReadTools} runs against the seeded database, so what
 * the stub gets asked for and what the adapter feeds back are the figures the application actually
 * holds.
 */
@SpringBootTest
class AnthropicBillingAssistantTest {

  @Autowired private BillingReadTools tools;

  private MockWebServer server;
  private AnthropicBillingAssistant assistant;

  @BeforeEach
  void startStub() throws Exception {
    server = new MockWebServer();
    server.start();
    AnthropicClient client =
        AnthropicOkHttpClient.builder()
            .apiKey("test-key-not-a-real-credential")
            .baseUrl(server.url("/").toString())
            .maxRetries(0)
            .build();
    assistant = new AnthropicBillingAssistant(client, tools, "claude-opus-5", 1024L);
  }

  @AfterEach
  void stopStub() throws Exception {
    server.shutdown();
  }

  private AssistantQuestion question() {
    return new AssistantQuestion(
        "Why is my balance what it is?", "ACCT-100001", Locale.forLanguageTag("en-CA"));
  }

  private void enqueue(String body) {
    server.enqueue(
        new MockResponse().setResponseCode(200).setHeader("content-type", "application/json").setBody(body));
  }

  private static String answer(String text, int input, int output, int cacheRead) {
    return """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
         "content":[{"type":"text","text":"%s"}],
         "stop_reason":"end_turn","stop_sequence":null,
         "usage":{"input_tokens":%d,"output_tokens":%d,"cache_read_input_tokens":%d,
                  "cache_creation_input_tokens":0}}
        """
        .formatted(text, input, output, cacheRead);
  }

  private static String toolCall(String tool, String argumentName, String argument) {
    return """
        {"id":"msg_0","type":"message","role":"assistant","model":"claude-opus-5",
         "content":[{"type":"tool_use","id":"toolu_1","name":"%s","input":{"%s":"%s"}}],
         "stop_reason":"tool_use","stop_sequence":null,
         "usage":{"input_tokens":100,"output_tokens":20,"cache_read_input_tokens":0,
                  "cache_creation_input_tokens":0}}
        """
        .formatted(tool, argumentName, argument);
  }

  // ---------------------------------------------------------------------------------------------
  // The tool loop
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("a tool call is executed and its result fed back, and the final answer is returned")
  void aToolCallIsExecutedAndItsResultFedBack() throws Exception {
    enqueue(toolCall("find_account", "accountReference", "ACCT-100001"));
    enqueue(answer("Your balance is 1328.00.", 300, 40, 0));

    AssistantAnswer result = assistant.ask(question());

    assertThat(result.available()).isTrue();
    assertThat(result.provider()).isEqualTo("anthropic");
    assertThat(result.text()).isEqualTo("Your balance is 1328.00.");
    assertThat(result.toolCalls())
        .containsExactly(new AssistantToolCall("find_account", "ACCT-100001"));

    server.takeRequest();
    RecordedRequest second = server.takeRequest();
    assertThat(second.getBody().readUtf8())
        .as("the tool result has to carry the real figures back to the model")
        .contains("1328.00")
        .contains("tool_result");
  }

  @Test
  @DisplayName("usage is accumulated across every call the answer took, not just the last one")
  void usageIsAccumulatedAcrossEveryCall() {
    enqueue(toolCall("find_account", "accountReference", "ACCT-100001"));
    enqueue(answer("Done.", 300, 40, 250));

    AssistantUsage usage = assistant.ask(question()).usage();

    assertThat(usage.inputTokens()).isEqualTo(400);
    assertThat(usage.outputTokens()).isEqualTo(60);
    assertThat(usage.cacheReadTokens()).isEqualTo(250);
    assertThat(usage.latencyMillis()).isNotNegative();
  }

  @Test
  @DisplayName("a tool that throws comes back as an error result rather than being dropped")
  void aToolThatThrowsComesBackAsAnErrorResult() throws Exception {
    enqueue(toolCall("find_account", "accountReference", "ACCT-DOES-NOT-EXIST"));
    enqueue(answer("I could not find that account.", 200, 20, 0));

    AssistantAnswer result = assistant.ask(question());

    assertThat(result.available()).isTrue();
    server.takeRequest();
    String fedBack = server.takeRequest().getBody().readUtf8();
    assertThat(fedBack).contains("is_error");
  }

  @Test
  @DisplayName("a model that only ever asks for more data is stopped rather than billed forever")
  void aModelThatOnlyAsksForMoreDataIsStopped() {
    for (int i = 0; i < 12; i++) {
      enqueue(toolCall("find_account", "accountReference", "ACCT-100001"));
    }

    AssistantAnswer result = assistant.ask(question());

    assertThat(result.available()).isFalse();
    assertThat(result.text()).contains("did not reach an answer");
    assertThat(server.getRequestCount()).isLessThanOrEqualTo(8);
  }

  // ---------------------------------------------------------------------------------------------
  // The shape of the request
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("every tool is offered, strictly, with the question after the cached prefix")
  void everyToolIsOfferedStrictlyAfterTheCachedPrefix() throws Exception {
    enqueue(answer("No tools needed.", 100, 10, 0));

    assistant.ask(question());

    String body = server.takeRequest().getBody().readUtf8();
    assertThat(body).contains("claude-opus-5");
    for (String tool : tools.toolNames()) {
      assertThat(body).as("tool missing from the request: %s", tool).contains(tool);
    }
    assertThat(body).contains("\"strict\":true");
    assertThat(body).contains("\"additionalProperties\":false");
  }

  @Test
  @DisplayName("the cached prefix is the system prompt, and nothing per-question is inside it")
  void theCachedPrefixHoldsNothingPerQuestion() throws Exception {
    enqueue(answer("No tools needed.", 100, 10, 0));

    assistant.ask(question());

    JsonNode request = new ObjectMapper().readTree(server.takeRequest().getBody().readUtf8());
    JsonNode system = request.get("system");

    assertThat(system.isArray())
        .as("the system prompt has to be blocks, not a bare string, to carry a breakpoint")
        .isTrue();
    assertThat(system.get(0).get("cache_control").get("type").asText()).isEqualTo("ephemeral");

    // Caching is a prefix match and the API renders tools, then system, then messages - the order of
    // keys in this JSON body has nothing to do with it, which is what a first version of this test got
    // wrong. What matters is that nothing varying per question is inside the cached part: an account
    // reference or a question in the system prompt would mean a prefix that never repeats, a cache
    // that never hits, and a cost that drifts with no code change to blame for it.
    String cached = system.get(0).get("text").asText();
    assertThat(cached)
        .doesNotContain("ACCT-100001")
        .doesNotContain("Why is my balance what it is?")
        .doesNotContain("en-CA");
  }

  @Test
  @DisplayName("no billing figure is put in the prompt; the model has to ask for them")
  void noBillingFigureIsPutInThePrompt() throws Exception {
    enqueue(answer("No tools needed.", 100, 10, 0));

    assistant.ask(question());

    String body = server.takeRequest().getBody().readUtf8();
    assertThat(body).doesNotContain("1328.00").doesNotContain("1591.60");
  }

  // ---------------------------------------------------------------------------------------------
  // Everything that can go wrong
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("a rate limit degrades to an unavailable answer, never an exception")
  void aRateLimitDegradesRatherThanThrowing() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}"));

    AssistantAnswer result = assistant.ask(question());

    assertThat(result.available()).isFalse();
    assertThat(result.provider()).isEqualTo("anthropic");
    assertThat(result.text()).contains("unavailable");
  }

  @Test
  @DisplayName("a server error degrades to an unavailable answer")
  void aServerErrorDegrades() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}"));

    assertThat(assistant.ask(question()).available()).isFalse();
  }

  @Test
  @DisplayName("a refusal is reported as a refusal, not as an empty answer")
  void aRefusalIsReportedAsARefusal() {
    enqueue(
        """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
         "content":[],"stop_reason":"refusal","stop_sequence":null,
         "usage":{"input_tokens":10,"output_tokens":0,"cache_read_input_tokens":0,
                  "cache_creation_input_tokens":0}}
        """);

    AssistantAnswer result = assistant.ask(question());

    assertThat(result.available()).isFalse();
    assertThat(result.text()).contains("declined");
  }

  @Test
  @DisplayName("an answer cut off at the token ceiling is not passed off as a complete one")
  void aTruncatedAnswerIsNotPassedOffAsComplete() {
    enqueue(
        """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
         "content":[{"type":"text","text":"Your balance is 13"}],
         "stop_reason":"max_tokens","stop_sequence":null,
         "usage":{"input_tokens":10,"output_tokens":1024,"cache_read_input_tokens":0,
                  "cache_creation_input_tokens":0}}
        """);

    AssistantAnswer result = assistant.ask(question());

    assertThat(result.available()).isFalse();
    assertThat(result.text()).doesNotContain("Your balance is 13");
  }

  @Test
  @DisplayName("the provider names itself")
  void theProviderNamesItself() {
    assertThat(assistant.providerName()).isEqualTo("anthropic");
  }

  @Test
  @DisplayName("hostile text in billing data is fed back as data and moves no money")
  void hostileTextInBillingDataMovesNoMoney() throws Exception {
    enqueue(toolCall("get_ledger", "termReference", "SEED-TERM-001"));
    enqueue(answer("The ledger shows three transactions.", 200, 20, 0));

    List<AssistantToolCall> made = assistant.ask(question()).toolCalls();

    assertThat(made).extracting(AssistantToolCall::tool).containsExactly("get_ledger");
    server.takeRequest();
    String fedBack = server.takeRequest().getBody().readUtf8();
    assertThat(fedBack).contains("tool_result");
    // Whatever a description says, the only tools that exist are read-only ones.
    assertThat(tools.toolNames()).doesNotContain("pay_term", "return_payment");
  }
}
