package com.insurancebilling.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Answers from recorded exchanges instead of calling a model.
 *
 * <p>This is the configuration the application ships with, and it is the reason the black-box suites
 * run on every pull request with no API key, no network and no cost. A reviewer who clones the
 * repository and starts it gets a working assistant; the tests that exercise it get the same answer
 * every time, which is what makes an assertion about a model's output possible at all.
 *
 * <p>Transcripts are read once at construction into an immutable map. The API suite drives four
 * threads against one application, so a provider that loaded lazily or cached as it went would be a
 * race condition sitting behind an endpoint nobody suspects of having state.
 *
 * <p><b>A question with no recording is answered as unavailable, not as an error.</b> A console whose
 * assistant returns a 500 for an unanticipated question is worse than one that says it has nothing
 * recorded, and the distinction between "no answer" and "this answer" is carried on
 * {@link AssistantAnswer#available} so a caller can tell them apart without reading the text.
 */
public class ReplayBillingAssistant implements BillingAssistant {

  static final String PROVIDER = "replay";

  private static final String LOCATION = "classpath:transcripts/*.json";

  private final Map<String, AssistantTranscript> transcripts;

  public ReplayBillingAssistant() {
    this(load());
  }

  ReplayBillingAssistant(Map<String, AssistantTranscript> transcripts) {
    this.transcripts = Map.copyOf(transcripts);
  }

  private static Map<String, AssistantTranscript> load() {
    ObjectMapper mapper = new ObjectMapper();
    List<AssistantTranscript> read = new ArrayList<>();
    try {
      for (Resource resource : new PathMatchingResourcePatternResolver().getResources(LOCATION)) {
        try (InputStream stream = resource.getInputStream()) {
          read.add(mapper.readValue(stream, AssistantTranscript.class));
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not read the recorded transcripts from " + LOCATION, e);
    }
    return index(read);
  }

  /**
   * Files transcripts by key, refusing two that answer the same question.
   *
   * <p>Separate from reading them so the refusal can be tested without writing a clashing file into
   * the shipped resources. Whichever of two clashing recordings won would be an accident of file
   * order, and the application's answer would change when somebody renamed a file.
   */
  static Map<String, AssistantTranscript> index(List<AssistantTranscript> transcripts) {
    Map<String, AssistantTranscript> indexed = new LinkedHashMap<>();
    for (AssistantTranscript transcript : transcripts) {
      AssistantTranscript clash = indexed.put(transcript.key(), transcript);
      if (clash != null) {
        // Both are named. Reporting only the second would send whoever reads this looking for a
        // duplicate of a question that is not written down anywhere in that form.
        throw new IllegalStateException(
            "Two transcripts answer the same question for the same account and language: \""
                + clash.question()
                + "\" and \""
                + transcript.question()
                + "\". One of them has to go.");
      }
    }
    return indexed;
  }

  @Override
  public AssistantAnswer ask(AssistantQuestion question) {
    AssistantTranscript transcript = transcripts.get(ReplayKey.of(question));
    if (transcript == null) {
      return AssistantAnswer.unavailable(
          "No recorded answer for: \"" + question.text() + "\"", PROVIDER);
    }
    return new AssistantAnswer(
        transcript.answer(),
        transcript.toolCalls(),
        transcript.usage() == null ? AssistantUsage.none() : transcript.usage(),
        PROVIDER,
        true);
  }

  @Override
  public String providerName() {
    return PROVIDER;
  }

  /** Every recording loaded, for the tests that hold the shipped set to its own rules. */
  Map<String, AssistantTranscript> loaded() {
    return transcripts;
  }
}
