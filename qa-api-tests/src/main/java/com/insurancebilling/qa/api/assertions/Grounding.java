package com.insurancebilling.qa.api.assertions;

import com.insurancebilling.qa.api.client.BillingApiClient;
import com.insurancebilling.qa.api.model.AssistantAnswerDto;
import com.insurancebilling.qa.api.model.AssistantToolCallDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that every figure in an answer came from the data behind it.
 *
 * <p>This is the assertion that makes a non-deterministic answer testable. The words vary; the
 * arithmetic does not. An answer about a bill may be phrased a hundred ways, but every monetary amount
 * in it has to appear in the billing data the assistant said it read - and if one does not, the
 * assistant invented a number about somebody's money, which is the failure that matters most and the
 * one a human reader is least likely to catch.
 *
 * <p><b>The tool results are fetched over the public API, not from the application's own classes.</b>
 * Each tool the answer names maps to the endpoint that publishes the same data, so this check is
 * black-box like the rest of the suite. Reading the application's tool registry directly would compare
 * the assistant against itself.
 *
 * <p><b>What this deliberately does not allow: arithmetic.</b> An answer saying two payments "come to
 * 263.60" would fail here even though the sum is right. That is the correct trade for a check that has
 * to be trusted: an allowance for derived figures is an allowance for any figure that happens to be
 * the sum of two others, which is most of them. The system prompt tells the assistant to quote figures
 * rather than compute them, so this check is also the test of whether it obeyed.
 */
public final class Grounding {

  /**
   * A decimal amount, not preceded or followed by another digit.
   *
   * <p>The lookarounds matter: without them {@code 1328.00} also matches as {@code 328.00}, and the
   * check would pass on a figure nobody wrote.
   */
  private static final Pattern MONEY = Pattern.compile("(?<![\\d.])\\d+\\.\\d{1,2}(?![\\d])");

  private Grounding() {}

  /**
   * Every monetary figure the answer states.
   *
   * <p>Normalised through {@link BigDecimal} so that {@code 1328.0} and {@code 1328.00} are the same
   * figure. The API serialises {@code 1328.0}; a person writes {@code 1328.00}; a check that compared
   * the strings would fail on a correct answer.
   */
  public static List<String> figuresIn(String text) {
    List<String> figures = new ArrayList<>();
    Matcher matcher = MONEY.matcher(text == null ? "" : text);
    while (matcher.find()) {
      figures.add(normalise(matcher.group()));
    }
    return figures;
  }

  /**
   * Every monetary figure published by the endpoints behind the calls this answer names.
   *
   * <p>A tool nobody recognises is an error rather than an empty set: silently contributing nothing
   * would make the grounding check pass for an answer whose provenance could not be verified at all,
   * which is worse than no check.
   */
  public static Set<String> figuresBehind(BillingApiClient billing, AssistantAnswerDto answer) {
    Set<String> figures = new LinkedHashSet<>();
    for (AssistantToolCallDto call : answer.toolCalls()) {
      figures.addAll(figuresIn(publishedBy(billing, call)));
    }
    return figures;
  }

  /** The raw JSON the public API returns for the data a tool call read. */
  private static String publishedBy(BillingApiClient billing, AssistantToolCallDto call) {
    return switch (call.tool()) {
      case "find_account" -> billing.accountRaw(call.argument()).asString();
      case "find_term" -> billing.termRaw(call.argument()).asString();
      case "get_installment_schedule" -> billing.scheduleRaw(call.argument()).asString();
      case "get_ledger" -> billing.ledgerRaw(call.argument()).asString();
      default ->
          throw new AssertionError(
              "The answer names a tool this suite cannot verify: '"
                  + call.tool()
                  + "'. Add the endpoint that publishes its data, or the grounding check is passing "
                  + "on an answer whose figures nobody checked.");
    };
  }

  private static String normalise(String figure) {
    return new BigDecimal(figure).stripTrailingZeros().toPlainString();
  }
}
