package com.insurancebilling.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The second live provider: Gemini, over its REST API.
 *
 * <p>It exists to prove the port was worth building. The console, the REST endpoint, the tool registry
 * and every suite above them are untouched by it — configuration chooses which model answers, and the
 * grounding, injection and PII assertions run against whichever one does. It is also free to run,
 * which is what lets a live model be exercised on a schedule rather than only in recordings.
 *
 * <p><b>The instructions are shared, not copied.</b> {@link AssistantSystemPrompt} is given to both
 * providers verbatim, so when the evaluation suite compares their answers the difference is the model
 * rather than the prompt.
 *
 * <p><b>Written against the REST shapes rather than an SDK.</b> Google's Java client is a large
 * dependency for four fields, and the request here is a nested map: {@code contents} carrying the
 * turns, {@code tools[].functionDeclarations} carrying the same schemas
 * {@link BillingReadTools} already publishes, and {@code systemInstruction} carrying the prompt.
 * {@code RestClient} comes with the web starter this application already has.
 *
 * <p><b>Failure is an answer, not an exception</b>, exactly as in the Anthropic provider: a rate limit,
 * a refusal, a response cut off at the token ceiling and a runaway tool loop all come back as an
 * unavailable answer, and every one of those paths is exercised against a stubbed API in
 * {@code GeminiBillingAssistantTest}.
 */
public class GeminiBillingAssistant implements BillingAssistant {

  static final String PROVIDER = "gemini";

  /** A Flash model, because those are the ones the free tier serves. */
  static final String DEFAULT_MODEL = "gemini-3.8-flash";

  /** The same ceiling the Anthropic provider uses, for the same reason: a loop with none is a bill. */
  private static final int MAX_TOOL_ROUNDS = 6;

  private final RestClient http;
  private final BillingReadTools tools;
  private final String model;
  private final long maxTokens;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  public GeminiBillingAssistant(
      RestClient http, BillingReadTools tools, String model, long maxTokens) {
    this.http = http;
    this.tools = tools;
    this.model = model;
    this.maxTokens = maxTokens;
  }

  @Override
  public String providerName() {
    return PROVIDER;
  }

  @Override
  public AssistantAnswer ask(AssistantQuestion question) {
    long startedAt = System.currentTimeMillis();
    List<AssistantToolCall> made = new ArrayList<>();
    List<Map<String, Object>> turns = new ArrayList<>();
    turns.add(turn("user", List.of(Map.of("text", opening(question)))));

    int inputTokens = 0;
    int outputTokens = 0;
    int cachedTokens = 0;

    try {
      for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
        JsonNode response = send(turns);

        JsonNode usage = response.path("usageMetadata");
        inputTokens += usage.path("promptTokenCount").asInt();
        outputTokens += usage.path("candidatesTokenCount").asInt();
        cachedTokens += usage.path("cachedContentTokenCount").asInt();

        JsonNode candidate = response.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asText("");
        if ("MAX_TOKENS".equals(finishReason)) {
          return unavailable("The answer was too long to return. Try a narrower question.", startedAt);
        }
        if (isRefusal(finishReason)) {
          return unavailable("The assistant declined to answer that question.", startedAt);
        }

        JsonNode parts = candidate.path("content").path("parts");
        List<JsonNode> calls = functionCalls(parts);
        if (calls.isEmpty()) {
          return new AssistantAnswer(
              textOf(parts),
              made,
              new AssistantUsage(
                  inputTokens, outputTokens, cachedTokens, System.currentTimeMillis() - startedAt),
              PROVIDER,
              true);
        }

        turns.add(turn("model", partsOf(candidate)));
        turns.add(turn("user", runAll(calls, made)));
      }

      return unavailable(
          "The assistant kept asking for more billing data and did not reach an answer.", startedAt);

    } catch (RestClientException unreachable) {
      // Rate limits, quota exhaustion, timeouts and malformed requests all land here. They differ in
      // the log and not to the reader, who gets the same answer the other providers give.
      return unavailable("The billing assistant is unavailable at the moment.", startedAt);
    }
  }

  private JsonNode send(List<Map<String, Object>> turns) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", AssistantSystemPrompt.TEXT))));
    body.put("contents", turns);
    body.put("tools", List.of(Map.of("functionDeclarations", declarations())));
    body.put("generationConfig", Map.of("maxOutputTokens", maxTokens));

    JsonNode response =
        http.post()
            .uri("/v1beta/models/{model}:generateContent", model)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

    return response == null ? mapper.createObjectNode() : response;
  }

  /** The registry's own schemas, renamed to the fields Gemini expects. No tool is defined here. */
  private List<Map<String, Object>> declarations() {
    List<Map<String, Object>> declarations = new ArrayList<>();
    for (BillingToolDefinition definition : tools.definitions()) {
      declarations.add(
          Map.of(
              "name", definition.name(),
              "description", definition.description(),
              "parameters", definition.inputSchema()));
    }
    return declarations;
  }

  private String opening(AssistantQuestion question) {
    return "The question is being asked about account "
        + question.accountReference()
        + ", in locale "
        + question.locale().toLanguageTag()
        + ".\n\n"
        + question.text();
  }

  private Map<String, Object> turn(String role, List<?> parts) {
    return Map.of("role", role, "parts", parts);
  }

  private List<JsonNode> functionCalls(JsonNode parts) {
    List<JsonNode> calls = new ArrayList<>();
    for (JsonNode part : parts) {
      if (part.hasNonNull("functionCall")) {
        calls.add(part.get("functionCall"));
      }
    }
    return calls;
  }

  private String textOf(JsonNode parts) {
    StringBuilder text = new StringBuilder();
    for (JsonNode part : parts) {
      if (part.hasNonNull("text")) {
        text.append(part.get("text").asText());
      }
    }
    return text.toString().strip();
  }

  /** The model's own turn, echoed back unchanged so the call it made stays in the conversation. */
  private List<Map<String, Object>> partsOf(JsonNode candidate) {
    List<Map<String, Object>> parts = new ArrayList<>();
    for (JsonNode part : candidate.path("content").path("parts")) {
      parts.add(mapper.convertValue(part, new com.fasterxml.jackson.core.type.TypeReference<>() {}));
    }
    return parts;
  }

  /**
   * Runs every tool the model asked for and returns all the results in one turn.
   *
   * <p>All of them, including the failures: dropping a failed call leaves the model waiting for a
   * result that never comes, and splitting results across turns teaches it not to ask for several at
   * once. The {@code id} is echoed back because that is what pairs a result with its call.
   */
  private List<Map<String, Object>> runAll(List<JsonNode> calls, List<AssistantToolCall> made) {
    List<Map<String, Object>> results = new ArrayList<>();
    for (JsonNode call : calls) {
      String name = call.path("name").asText();
      String argument = argumentOf(call.path("args"));
      made.add(new AssistantToolCall(name, argument));

      Map<String, Object> response = new LinkedHashMap<>();
      try {
        response.put("result", tools.invoke(name, argument));
      } catch (RuntimeException refused) {
        response.put(
            "error",
            refused.getMessage() == null ? refused.getClass().getSimpleName() : refused.getMessage());
      }

      Map<String, Object> functionResponse = new LinkedHashMap<>();
      functionResponse.put("name", name);
      functionResponse.put("response", response);
      if (call.hasNonNull("id")) {
        functionResponse.put("id", call.get("id").asText());
      }
      results.add(Map.of("functionResponse", functionResponse));
    }
    return results;
  }

  /**
   * Every tool takes exactly one string argument, so the first string value in the arguments is it.
   *
   * <p>Read from parsed JSON rather than from the serialised form, for the reason the Anthropic
   * provider gives: models vary in how they escape strings, and a reference pulled out of raw text
   * works until the day an escape appears in it.
   */
  private String argumentOf(JsonNode args) {
    for (JsonNode value : args) {
      if (value.isTextual()) {
        return value.asText();
      }
    }
    return "";
  }

  /**
   * A finish reason that is not a completed answer.
   *
   * <p>Matched as a set rather than on {@code SAFETY} alone: the documented list has grown over time,
   * and a reason nobody here recognised would otherwise be read as a successful answer with no text.
   */
  private boolean isRefusal(String finishReason) {
    return !finishReason.isEmpty() && !"STOP".equals(finishReason);
  }

  /** Unavailable, but carrying the latency: how long a failure took is worth measuring too. */
  private AssistantAnswer unavailable(String reason, long startedAt) {
    return new AssistantAnswer(
        reason,
        List.of(),
        new AssistantUsage(0, 0, 0, System.currentTimeMillis() - startedAt),
        PROVIDER,
        false);
  }
}
