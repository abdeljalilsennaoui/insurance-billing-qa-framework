package com.insurancebilling.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One recorded exchange: a question, the tool calls that answered it, and the answer.
 *
 * <p>Transcripts ship on the main classpath rather than the test classpath, because replay is the
 * configuration a reader gets when they clone the repository and run it. They are the assistant's
 * equivalent of the seeded rows {@code SeedDataLoader} writes: enough real data for the application
 * to be worth looking at, with nothing about it pretending to be live.
 *
 * @param source whether this came off a real model call or was written by hand. Rendered to the
 *     reader and asserted in tests; a hand-written transcript presented as a recording would be the
 *     kind of claim this repository exists to avoid making.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantTranscript(
    String question,
    String locale,
    String accountReference,
    List<AssistantToolCall> toolCalls,
    String answer,
    AssistantUsage usage,
    Source source,
    String model,
    String recordedAt) {

  public enum Source {
    /** Captured from a real model call by the recorder. */
    RECORDED,
    /** Written by hand, because no recording existed yet. */
    HAND_WRITTEN
  }

  /**
   * The key the replay provider matches on.
   *
   * <p>Question, language and account together. Language is part of it because the same question
   * answered in French is a different answer, and the account is part of it because the same question
   * asked from a different screen is about different money.
   */
  public String key() {
    return ReplayKey.of(question, locale, accountReference);
  }
}
