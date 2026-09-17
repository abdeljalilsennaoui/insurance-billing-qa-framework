package com.insurancebilling.qa.api.model;

/**
 * One call the assistant made while answering.
 *
 * <p>{@code tool} is modelled as a String rather than an enum on purpose, in line with every other
 * model in this suite: a suite that shared a type with the application would compile past a renamed
 * constant and keep passing while the published contract had broken.
 */
public record AssistantToolCallDto(String tool, String argument) {}
