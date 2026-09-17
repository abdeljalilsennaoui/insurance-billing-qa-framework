package com.insurancebilling.api;

import com.insurancebilling.api.dto.AssistantAnswerResponse;
import com.insurancebilling.api.dto.AssistantQuestionRequest;
import com.insurancebilling.assistant.AssistantQuestion;
import com.insurancebilling.assistant.BillingAssistant;
import com.insurancebilling.service.BillingService;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Questions about one billing account, answered by the assistant.
 *
 * <p>Scoped under the account rather than sitting at {@code /api/assistant}, because every question is
 * about one account's money and the account belongs where the rest of the API already puts it. An
 * endpoint that took the account in the body as well as the path would have two sources of truth for
 * the only thing deciding whose figures are being discussed.
 *
 * <p><b>The account is resolved before the assistant is called.</b> A question about an account that
 * does not exist is a 404 from {@link BillingService}, exactly as it is on every other endpoint, rather
 * than a model being asked to explain an account it will not be able to find. That keeps the 404 a
 * property of the platform rather than of whatever the assistant happens to say.
 *
 * <p>An assistant that cannot answer still returns 200. {@code available} on the body says whether
 * there is an answer in it, and a console that got a 500 every time its assistant was switched off or
 * rate limited would be reporting a fault in the billing platform that does not exist.
 */
@RestController
@RequestMapping("/api/accounts/{accountReference}/assistant")
public class AssistantController {

  private final BillingAssistant assistant;
  private final BillingService billing;

  public AssistantController(BillingAssistant assistant, BillingService billing) {
    this.assistant = assistant;
    this.billing = billing;
  }

  @PostMapping
  public AssistantAnswerResponse ask(
      @PathVariable String accountReference, @Valid @RequestBody AssistantQuestionRequest request) {

    // Throws ResourceNotFoundException, which GlobalExceptionHandler turns into the same 404 shape
    // every other endpoint returns.
    billing.findAccount(accountReference);

    Locale locale = LocaleContextHolder.getLocale();
    AssistantQuestion question =
        new AssistantQuestion(request.question(), accountReference, locale);

    return AssistantAnswerResponse.from(request.question(), assistant.ask(question));
  }
}
