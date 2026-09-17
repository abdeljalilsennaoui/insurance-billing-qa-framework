package com.insurancebilling.api;

import com.insurancebilling.api.dto.AssistantAnswerResponse;
import com.insurancebilling.assistant.AssistantQuestion;
import com.insurancebilling.assistant.BillingAssistant;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * The assistant, as the two consoles use it.
 *
 * <p>Both screens ask their questions through this one class for the same reason they render the same
 * Thymeleaf fragment: an agent quoting the policyholder a different answer than the policyholder can
 * read for themselves is the failure these consoles exist to avoid, and two copies of four lines is how
 * that starts.
 *
 * <p>The locale comes from the request rather than being passed in, because it is already resolved by
 * the {@code LocaleChangeInterceptor} for the page being rendered. Taking it as an argument would let a
 * caller answer in one language on a page written in another.
 */
@Component
public class AssistantPanel {

  /** The longest question the console will put to the assistant. Mirrors the REST endpoint's cap. */
  static final int MAX_QUESTION_LENGTH = 500;

  private final BillingAssistant assistant;

  public AssistantPanel(BillingAssistant assistant) {
    this.assistant = assistant;
  }

  /** The provider in use, so the screen can say where an answer came from. */
  public String provider() {
    return assistant.providerName();
  }

  /**
   * Answers a question, or returns null when there is no question to answer.
   *
   * <p>Null rather than an empty answer: the panel renders nothing at all before anybody has asked
   * anything, and an empty answer object would make "not asked yet" and "asked and got nothing" the
   * same state on screen.
   */
  public AssistantAnswerResponse answer(String accountReference, String question) {
    if (question == null || question.isBlank()) {
      return null;
    }
    String asked = question.strip();
    if (asked.length() > MAX_QUESTION_LENGTH) {
      asked = asked.substring(0, MAX_QUESTION_LENGTH);
    }

    Locale locale = LocaleContextHolder.getLocale();
    return AssistantAnswerResponse.from(
        asked, assistant.ask(new AssistantQuestion(asked, accountReference, locale)));
  }
}
