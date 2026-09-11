package com.insurancebilling.qa.api.model;

import java.util.Map;

/**
 * The error shape the API returns for every failure.
 *
 * <p>{@code code} is what the negative tests assert on. A status code alone cannot distinguish an
 * overpayment refusal from a cancelled-invoice refusal, and both are 422.
 */
public record ApiError(
    String timestamp,
    int status,
    String code,
    String message,
    String path,
    Map<String, String> fieldErrors) {}
