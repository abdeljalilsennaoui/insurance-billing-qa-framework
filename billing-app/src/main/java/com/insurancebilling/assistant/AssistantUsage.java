package com.insurancebilling.assistant;

/**
 * What one answer cost.
 *
 * <p>Recorded on every answer rather than logged, so a test can assert on it. Two of these matter
 * beyond reporting: {@code cacheReadTokens} being zero across repeated questions is the symptom of a
 * prompt prefix that is not stable, which costs money silently and shows up nowhere else; and
 * {@code latencyMillis} is what a budget assertion is written against.
 */
public record AssistantUsage(
    int inputTokens, int outputTokens, int cacheReadTokens, long latencyMillis) {

  public static AssistantUsage none() {
    return new AssistantUsage(0, 0, 0, 0L);
  }
}
