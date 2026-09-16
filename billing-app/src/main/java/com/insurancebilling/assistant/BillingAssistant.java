package com.insurancebilling.assistant;

/**
 * Answers a billing question, by whatever means the running configuration selects.
 *
 * <p>The port exists so the console, the REST endpoint and every test above them are written against
 * one thing, and the choice between calling a model, replaying a recording and being switched off is
 * a property rather than a code path. That is what lets the black-box suites run on every pull
 * request with no API key, no network and no cost, against the same endpoint a live configuration
 * serves.
 *
 * <p>Implementations do not throw for an answer they cannot give. A question that cannot be answered
 * comes back as {@link AssistantAnswer#unavailable}, because a billing console that returns a 500
 * when its assistant is having a bad day is worse than one that says so.
 */
public interface BillingAssistant {

  AssistantAnswer ask(AssistantQuestion question);

  /** {@code anthropic}, {@code replay} or {@code disabled}. Rendered to the reader. */
  String providerName();
}
