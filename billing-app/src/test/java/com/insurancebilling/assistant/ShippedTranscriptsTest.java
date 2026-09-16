package com.insurancebilling.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Holds the transcripts the application ships to the data they claim to describe.
 *
 * <p>The shipped transcripts are hand-written, because the recorder that replaces them needs an API
 * key and does not exist yet. A hand-written answer about money is a claim somebody made up, and this
 * repository's argument is that its claims can be checked — so they are checked here, against the
 * same tool results a live assistant would have been given.
 *
 * <p>{@code everyFigureQuotedAppearsInTheToolResults} is the one that matters. It is a first,
 * deliberately strict form of the grounding assertion that the API suite will make against live
 * answers: every monetary figure in the answer has to appear in the results of the tools that
 * transcript says it called. Strict, because it has no allowance for arithmetic — an answer saying
 * two payments "come to 263.60" would fail even though the sum is right. That is the correct trade
 * here: the transcripts are written to quote figures rather than derive them, and a check with no
 * exceptions in it cannot be argued with. Answers that legitimately compute are the harder problem
 * and belong with the live assistant, not with four fixtures.
 */
@SpringBootTest
class ShippedTranscriptsTest {

  /**
   * A decimal amount, not preceded or followed by another digit.
   *
   * <p>The lookarounds are load-bearing: without them {@code 1328.00} also matches as {@code 328.00},
   * and the check would pass on a figure nobody wrote.
   */
  private static final Pattern MONEY = Pattern.compile("(?<![\\d.])\\d+\\.\\d{2}(?![\\d])");

  @Autowired private BillingReadTools tools;

  private final ReplayBillingAssistant replay = new ReplayBillingAssistant();

  private List<AssistantTranscript> shipped() {
    List<AssistantTranscript> all = new ArrayList<>(replay.loaded().values());
    assertThat(all).as("the application ships no transcripts at all").isNotEmpty();
    return all;
  }

  @Test
  @DisplayName("every figure quoted in a shipped answer appears in the tool results behind it")
  void everyFigureQuotedAppearsInTheToolResults() throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    for (AssistantTranscript transcript : shipped()) {
      Set<String> grounded = new java.util.HashSet<>();
      for (AssistantToolCall call : transcript.toolCalls()) {
        String json = mapper.writeValueAsString(tools.invoke(call.tool(), call.argument()));
        Matcher matcher = MONEY.matcher(json);
        while (matcher.find()) {
          grounded.add(new BigDecimal(matcher.group()).stripTrailingZeros().toPlainString());
        }
      }

      List<String> quoted = new ArrayList<>();
      Matcher matcher = MONEY.matcher(transcript.answer());
      while (matcher.find()) {
        quoted.add(new BigDecimal(matcher.group()).stripTrailingZeros().toPlainString());
      }

      assertThat(quoted)
          .as("transcript \"%s\" quotes figures that are in no tool result", transcript.question())
          .isNotEmpty()
          .allSatisfy(figure -> assertThat(grounded).contains(figure));
    }
  }

  @Test
  @DisplayName("every tool a shipped transcript names is a tool that exists")
  void everyToolNamedIsAToolThatExists() {
    for (AssistantTranscript transcript : shipped()) {
      assertThat(transcript.toolCalls())
          .as("transcript: %s", transcript.question())
          .isNotEmpty()
          .allSatisfy(call -> assertThat(tools.toolNames()).contains(call.tool()));
    }
  }

  @Test
  @DisplayName("every shipped transcript says where it came from")
  void everyShippedTranscriptSaysWhereItCameFrom() {
    assertThat(shipped())
        .allSatisfy(transcript -> assertThat(transcript.source()).isNotNull())
        .extracting(AssistantTranscript::source)
        .containsOnly(AssistantTranscript.Source.HAND_WRITTEN);
  }

  @Test
  @DisplayName("a recorded transcript names the model it came from; a hand-written one does not")
  void aRecordedTranscriptNamesItsModel() {
    for (AssistantTranscript transcript : shipped()) {
      if (transcript.source() == AssistantTranscript.Source.RECORDED) {
        assertThat(transcript.model())
            .as("recorded transcript with no model: %s", transcript.question())
            .isNotBlank();
        assertThat(transcript.recordedAt()).isNotBlank();
      } else {
        assertThat(transcript.model())
            .as("hand-written transcript claiming a model: %s", transcript.question())
            .isNull();
      }
    }
  }

  @Test
  @DisplayName("both console languages have at least one recorded answer")
  void bothConsoleLanguagesHaveAnAnswer() {
    Set<String> languages =
        shipped().stream()
            .map(t -> Locale.forLanguageTag(t.locale()).getLanguage())
            .collect(Collectors.toSet());

    assertThat(languages).contains("en", "fr");
  }

  @Test
  @DisplayName("no shipped answer contains anything that could be a full bank account number")
  void noShippedAnswerLooksLikeABankAccountNumber() {
    assertThat(shipped())
        .allSatisfy(
            transcript ->
                assertThat(transcript.answer())
                    .as("transcript: %s", transcript.question())
                    .doesNotMatch("(?s).*(?<![\\d.])\\d{7,}(?![\\d]).*"));
  }
}
