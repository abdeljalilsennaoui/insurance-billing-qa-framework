package com.insurancebilling.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
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
      @Value("${billing.assistant.provider:replay}") String provider,
      @Value("${billing.assistant.model:claude-opus-5}") String model,
      @Value("${billing.assistant.max-tokens:2048}") long maxTokens,
      @Value("${billing.assistant.base-url:}") String baseUrl,
      BillingReadTools tools) {
    return switch (provider.trim().toLowerCase()) {
      case ReplayBillingAssistant.PROVIDER -> new ReplayBillingAssistant();
      case DisabledBillingAssistant.PROVIDER -> new DisabledBillingAssistant();
      case AnthropicBillingAssistant.PROVIDER ->
          new AnthropicBillingAssistant(
              anthropicClient(baseUrl, System.getenv("ANTHROPIC_API_KEY")), tools, model, maxTokens);
      default ->
          throw new IllegalStateException(
              "billing.assistant.provider is '"
                  + provider
                  + "'. Accepted values are: "
                  + ReplayBillingAssistant.PROVIDER
                  + ", "
                  + DisabledBillingAssistant.PROVIDER
                  + ", "
                  + AnthropicBillingAssistant.PROVIDER
                  + ".");
    };
  }

  /**
   * The API client.
   *
   * <p>The key is read from the environment and passed in rather than fetched here, so that this
   * method can be called with one in a test. There is deliberately no {@code billing.assistant.api-key}
   * property: a credential belongs in the environment or a secret store, and a repository that bills
   * itself on handling money should not model one as ordinary configuration.
   *
   * <p>{@code billing.assistant.base-url} exists so the tests can point the real client at a server on
   * localhost and exercise the whole adapter - the tool loop, the retries, the error mapping - without
   * a key and without the network. It is empty everywhere else, and empty means the SDK's own default
   * rather than a partially configured client.
   */
  AnthropicClient anthropicClient(String baseUrl, String apiKey) {
    AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder();
    if (apiKey == null || apiKey.isBlank()) {
      builder.fromEnv();
    } else {
      builder.apiKey(apiKey);
    }
    if (baseUrl != null && !baseUrl.isBlank()) {
      builder.baseUrl(baseUrl.strip());
    }
    return builder.build();
  }
}
