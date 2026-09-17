package com.insurancebilling.api.dto;

import com.insurancebilling.assistant.AssistantAnswer;
import java.util.List;

/**
 * What the assistant answered, and how it got there.
 *
 * <p>The tool calls are part of the response rather than a server-side log. An answer about money that
 * cannot be traced back to the figures behind it is not evidence of anything, and this list is what the
 * grounding assertions read: every figure in the text has to appear in the results of the calls named
 * here.
 *
 * <p>{@code provider} is returned for the same reason it is rendered on screen. A recorded answer
 * presented as though a model had just produced it would be the application misrepresenting itself.
 *
 * @param available false when the assistant could not answer at all, so a caller can tell an apology
 *     apart from an answer without reading the prose
 */
public record AssistantAnswerResponse(
    String question,
    String answer,
    boolean available,
    String provider,
    List<ToolCall> toolCalls,
    Usage usage) {

  /** One call the assistant made, in the order it made it. */
  public record ToolCall(String tool, String argument) {}

  /** What the answer cost. Zero throughout when the answer came from a recording. */
  public record Usage(int inputTokens, int outputTokens, int cacheReadTokens, long latencyMillis) {}

  public static AssistantAnswerResponse from(String question, AssistantAnswer answer) {
    return new AssistantAnswerResponse(
        question,
        answer.text(),
        answer.available(),
        answer.provider(),
        answer.toolCalls().stream().map(c -> new ToolCall(c.tool(), c.argument())).toList(),
        new Usage(
            answer.usage().inputTokens(),
            answer.usage().outputTokens(),
            answer.usage().cacheReadTokens(),
            answer.usage().latencyMillis()));
  }
}
