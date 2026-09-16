package com.insurancebilling.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses which assistant the application runs with.
 *
 * <p>One {@code @Bean} method that switches, rather than three beans each behind their own
 * {@code @ConditionalOnProperty}. With conditionals, a misspelt provider matches none of them, no
 * bean is created, and the application fails at injection with a
 * {@code NoSuchBeanDefinitionException} naming {@code BillingAssistant} - a message that describes
 * the symptom and not the cause, on a startup nobody expected to fail. Switching here means the
 * misspelling is reported as a misspelling, with the accepted values listed, which is the same
 * bargain {@code billing.time-zone} makes in {@code BillingTimeConfiguration}.
 *
 * <p>The default is {@code replay}, and that is deliberate. A reader who clones this repository and
 * runs it has no API key, and an application whose headline feature is dead on arrival teaches them
 * nothing. Replay gives them a working assistant answering from recorded exchanges, labelled as such
 * on the screen so nothing about it pretends to be live.
 */
@Configuration
public class AssistantConfiguration {

  @Bean
  public BillingAssistant billingAssistant(
      @Value("${billing.assistant.provider:replay}") String provider) {
    return switch (provider.trim().toLowerCase()) {
      case ReplayBillingAssistant.PROVIDER -> new ReplayBillingAssistant();
      case DisabledBillingAssistant.PROVIDER -> new DisabledBillingAssistant();
      default ->
          throw new IllegalStateException(
              "billing.assistant.provider is '"
                  + provider
                  + "'. Accepted values are: "
                  + ReplayBillingAssistant.PROVIDER
                  + ", "
                  + DisabledBillingAssistant.PROVIDER
                  + ".");
    };
  }
}
