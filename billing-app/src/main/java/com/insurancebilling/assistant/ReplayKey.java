package com.insurancebilling.assistant;

import java.util.Locale;

/**
 * Builds the key a transcript is filed and found under.
 *
 * <p>One class so the recorder and the replay provider cannot disagree about what counts as the same
 * question. Two implementations of that would mean transcripts that record cleanly and never replay,
 * and the failure would look like a missing recording rather than a mismatched key.
 */
final class ReplayKey {

  private ReplayKey() {}

  static String of(String question, String locale, String accountReference) {
    String language = locale == null ? "" : Locale.forLanguageTag(locale.replace('_', '-')).getLanguage();
    return normalise(question) + "|" + language + "|" + (accountReference == null ? "" : accountReference.strip());
  }

  static String of(AssistantQuestion question) {
    return question.normalisedText()
        + "|"
        + question.locale().getLanguage()
        + "|"
        + question.accountReference();
  }

  private static String normalise(String question) {
    if (question == null) {
      return "";
    }
    return question
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
        .replaceAll("\\s+", " ")
        .strip();
  }
}
