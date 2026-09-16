package com.insurancebilling.assistant;

import java.util.Locale;

/**
 * A question put to the billing assistant, and the context it is asked in.
 *
 * <p>The account reference is carried separately rather than left for the model to find in the text.
 * A policyholder asking "why is my instalment higher this month" has not named an account, and a
 * console that made them type one would be asking them to do the lookup the screen has already done.
 *
 * @param text what the reader typed
 * @param accountReference the account whose screen the question was asked from, never null
 * @param locale the console locale, so the answer comes back in the language being read
 */
public record AssistantQuestion(String text, String accountReference, Locale locale) {

  public AssistantQuestion {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("A question needs text.");
    }
    if (accountReference == null || accountReference.isBlank()) {
      throw new IllegalArgumentException("A question needs the account it was asked from.");
    }
    if (locale == null) {
      throw new IllegalArgumentException("A question needs the locale it will be answered in.");
    }
    text = text.strip();
    accountReference = accountReference.strip();
  }

  /**
   * The form the replay provider matches on: lower-cased, punctuation dropped, whitespace collapsed.
   *
   * <p>Kept here rather than inside the replay provider because the recorder has to write the same
   * form, and two implementations of "the same question" that disagree would mean a transcript that
   * records cleanly and never replays.
   */
  public String normalisedText() {
    return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").strip();
  }
}
