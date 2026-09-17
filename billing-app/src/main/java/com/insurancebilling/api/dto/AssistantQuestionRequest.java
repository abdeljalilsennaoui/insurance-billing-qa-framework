package com.insurancebilling.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A question put to the billing assistant.
 *
 * <p>The account is not in the body. It is in the path, because the question is about one account and
 * a caller that could name a different account in the body than in the URL would be two sources of
 * truth for the only thing that decides whose money is being discussed.
 *
 * <p>The length cap is not a validation nicety. Input length is what a request to a model costs, and
 * an endpoint that accepts an unbounded string is an endpoint somebody can run up a bill with.
 */
public record AssistantQuestionRequest(
    @NotBlank @Size(max = 500) String question) {}
