package com.insurancebilling.assistant;

/**
 * The assistant, switched off.
 *
 * <p>Exists so that "off" is a configuration rather than a null check spread through the console and
 * the controller. It answers every question the same way and never throws, which is what the console
 * renders as an unavailable panel and what the tests assert when they check that the rest of the
 * screen is unaffected by the assistant being absent.
 *
 * <p>An operator turning the assistant off is a thing that will happen - a cost ceiling, an incident,
 * a provider outage - and the behaviour it produces is worth having a test for rather than
 * discovering.
 */
public class DisabledBillingAssistant implements BillingAssistant {

  static final String PROVIDER = "disabled";

  @Override
  public AssistantAnswer ask(AssistantQuestion question) {
    return AssistantAnswer.unavailable("The billing assistant is switched off.", PROVIDER);
  }

  @Override
  public String providerName() {
    return PROVIDER;
  }
}
