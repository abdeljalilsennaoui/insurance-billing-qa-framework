package com.insurancebilling.assistant;

/**
 * One call the assistant made while answering, in the order it made it.
 *
 * <p>Returned to the caller and rendered in the console, because an answer about money that cannot be
 * traced back to the figures it came from is not evidence of anything. It is also what the grounding
 * assertions read: every figure in the answer has to appear in the results of the calls listed here.
 */
public record AssistantToolCall(String tool, String argument) {}
