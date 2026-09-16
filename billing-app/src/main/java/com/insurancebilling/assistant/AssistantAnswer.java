package com.insurancebilling.assistant;

import java.util.List;

/**
 * What the assistant answered, how it got there, and what it cost.
 *
 * <p>{@code provider} is part of the answer rather than a detail of the configuration, and it is
 * rendered in the console. A reader looking at a recorded answer has to be able to tell that is what
 * they are looking at: an application that presented a replayed transcript as though a model had just
 * produced it would be misrepresenting itself, and this repository's whole argument is that its
 * claims can be checked.
 *
 * @param text the answer, in the locale of the question
 * @param toolCalls the calls made to produce it, in order
 * @param usage what it cost
 * @param provider which implementation produced it: {@code anthropic}, {@code replay} or
 *     {@code disabled}
 * @param available false when the assistant could not answer at all, so a caller can degrade rather
 *     than render an apology as though it were an answer
 */
public record AssistantAnswer(
    String text,
    List<AssistantToolCall> toolCalls,
    AssistantUsage usage,
    String provider,
    boolean available) {

  public AssistantAnswer {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }

  /** An answer from a live model call. */
  public static AssistantAnswer live(
      String text, List<AssistantToolCall> toolCalls, AssistantUsage usage, String provider) {
    return new AssistantAnswer(text, toolCalls, usage, provider, true);
  }

  /** The assistant could not answer. The reason is for the reader, not a stack trace. */
  public static AssistantAnswer unavailable(String reason, String provider) {
    return new AssistantAnswer(reason, List.of(), AssistantUsage.none(), provider, false);
  }
}
