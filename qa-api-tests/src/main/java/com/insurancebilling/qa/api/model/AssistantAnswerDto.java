package com.insurancebilling.qa.api.model;

import java.util.List;

/** What the assistant answered, the calls behind it, and what it cost. */
public record AssistantAnswerDto(
    String question,
    String answer,
    boolean available,
    String provider,
    List<AssistantToolCallDto> toolCalls,
    UsageDto usage) {

  /** Token and latency figures. Zero throughout when the answer came from a recording. */
  public record UsageDto(
      int inputTokens, int outputTokens, int cacheReadTokens, long latencyMillis) {}
}
