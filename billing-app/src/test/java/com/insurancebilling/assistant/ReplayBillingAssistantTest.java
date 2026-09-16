package com.insurancebilling.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The replay provider, the disabled one, and the configuration that chooses between them.
 *
 * <p>Plain JUnit with no Spring context: the point of the port is that the thing under test here is a
 * map lookup and a switch, and booting an application to prove that would be proving something else.
 */
class ReplayBillingAssistantTest {

  private static final String ACCOUNT = "ACCT-100001";

  private final ReplayBillingAssistant replay = new ReplayBillingAssistant();

  private AssistantQuestion question(String text, String language) {
    return new AssistantQuestion(text, ACCOUNT, Locale.forLanguageTag(language));
  }

  // ---------------------------------------------------------------------------------------------
  // Matching
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("a recorded question replays, and replays identically")
  void aRecordedQuestionReplaysIdentically() {
    AssistantQuestion asked = question("Why is my balance 1,328.00?", "en-CA");

    AssistantAnswer first = replay.ask(asked);
    AssistantAnswer second = replay.ask(asked);

    assertThat(first.available()).isTrue();
    assertThat(first.provider()).isEqualTo("replay");
    assertThat(first.text()).contains("1328.00");
    assertThat(first.toolCalls()).extracting(AssistantToolCall::tool).contains("find_account");
    assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("case, punctuation and spacing do not decide whether a question is the same question")
  void caseAndPunctuationDoNotDecideTheMatch() {
    AssistantAnswer canonical = replay.ask(question("Why is my balance 1,328.00?", "en-CA"));

    for (String variant :
        List.of(
            "why is my balance 1,328.00?",
            "  Why   is my balance 1,328.00 ??? ",
            "WHY IS MY BALANCE 1,328.00")) {
      assertThat(replay.ask(question(variant, "en-CA")).text())
          .as("variant: %s", variant)
          .isEqualTo(canonical.text());
    }
  }

  @Test
  @DisplayName("the same question in French is a different answer, not the English one")
  void theSameQuestionInFrenchIsADifferentAnswer() {
    AssistantAnswer french = replay.ask(question("Pourquoi mon solde est-il de 1 328,00 $ ?", "fr-CA"));

    assertThat(french.available()).isTrue();
    assertThat(french.text()).contains("solde");
    assertThat(french.text())
        .isNotEqualTo(replay.ask(question("Why is my balance 1,328.00?", "en-CA")).text());
  }

  @Test
  @DisplayName("a recorded question asked from another account does not match")
  void aRecordedQuestionAskedFromAnotherAccountDoesNotMatch() {
    AssistantAnswer answer =
        replay.ask(
            new AssistantQuestion(
                "Why is my balance 1,328.00?", "ACCT-100002", Locale.forLanguageTag("en-CA")));

    assertThat(answer.available()).isFalse();
  }

  @Test
  @DisplayName("an unrecorded question is unavailable and says which question it was")
  void anUnrecordedQuestionIsUnavailable() {
    AssistantAnswer answer = replay.ask(question("What is the airspeed of a swallow?", "en-CA"));

    assertThat(answer.available()).isFalse();
    assertThat(answer.provider()).isEqualTo("replay");
    assertThat(answer.text()).contains("airspeed of a swallow");
    assertThat(answer.toolCalls()).isEmpty();
  }

  @Test
  @DisplayName("a transcript files itself under the key the question it answers looks up")
  void aTranscriptFilesItselfUnderTheKeyTheQuestionLooksUp() {
    AssistantTranscript transcript = transcript("Same question?", "First answer");

    assertThat(transcript.key()).isEqualTo(ReplayKey.of(question("Same   QUESTION!", "en-CA")));
  }

  @Test
  @DisplayName("two recordings of the same question fail loudly rather than one quietly winning")
  void twoRecordingsOfTheSameQuestionFailLoudly() {
    List<AssistantTranscript> clashing =
        List.of(transcript("Same question?", "First"), transcript("same question", "Second"));

    assertThatThrownBy(() -> ReplayBillingAssistant.index(clashing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Same question?")
        .hasMessageContaining("same question");
  }

  private AssistantTranscript transcript(String question, String answer) {
    return new AssistantTranscript(
        question,
        "en-CA",
        ACCOUNT,
        List.of(),
        answer,
        null,
        AssistantTranscript.Source.HAND_WRITTEN,
        null,
        null);
  }

  // ---------------------------------------------------------------------------------------------
  // Behaviour under load, and when switched off
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("four threads asking at once get the same answer")
  void fourThreadsAskingAtOnceGetTheSameAnswer() throws Exception {
    AssistantQuestion asked = question("Why is my balance 1,328.00?", "en-CA");
    String expected = replay.ask(asked).text();

    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      List<Callable<String>> calls =
          IntStream.range(0, 40).<Callable<String>>mapToObj(i -> () -> replay.ask(asked).text()).toList();
      for (Future<String> result : pool.invokeAll(calls)) {
        assertThat(result.get()).isEqualTo(expected);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("the disabled assistant answers every question the same way and never throws")
  void theDisabledAssistantNeverThrows() {
    DisabledBillingAssistant disabled = new DisabledBillingAssistant();

    AssistantAnswer answer = disabled.ask(question("Anything at all", "en-CA"));

    assertThat(answer.available()).isFalse();
    assertThat(answer.provider()).isEqualTo("disabled");
    assertThat(answer.toolCalls()).isEmpty();
    assertThat(answer.usage()).isEqualTo(AssistantUsage.none());
    assertThat(disabled.providerName()).isEqualTo("disabled");
  }

  @Test
  @DisplayName("a transcript with no locale or account still files under a key rather than blowing up")
  void aTranscriptWithNoLocaleOrAccountStillFilesUnderAKey() {
    AssistantTranscript incomplete =
        new AssistantTranscript(
            "Orphan question?", null, null, List.of(), "An answer", null,
            AssistantTranscript.Source.HAND_WRITTEN, null, null);

    assertThat(incomplete.key()).isEqualTo("orphan question||");
    assertThat(ReplayBillingAssistant.index(List.of(incomplete))).containsKey("orphan question||");
  }

  // ---------------------------------------------------------------------------------------------
  // Choosing a provider
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("the configured provider is the one that is built")
  void theConfiguredProviderIsTheOneThatIsBuilt() {
    AssistantConfiguration configuration = new AssistantConfiguration();

    assertThat(configuration.billingAssistant("replay")).isInstanceOf(ReplayBillingAssistant.class);
    assertThat(configuration.billingAssistant("  REPLAY ")).isInstanceOf(ReplayBillingAssistant.class);
    assertThat(configuration.billingAssistant("disabled")).isInstanceOf(DisabledBillingAssistant.class);
  }

  @Test
  @DisplayName("an unknown provider fails startup, naming itself and the accepted values")
  void anUnknownProviderFailsStartup() {
    assertThatThrownBy(() -> new AssistantConfiguration().billingAssistant("antropic"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("antropic")
        .hasMessageContaining("replay")
        .hasMessageContaining("disabled");
  }

  // ---------------------------------------------------------------------------------------------
  // The question itself
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("a question without text, an account or a locale is refused at construction")
  void anIncompleteQuestionIsRefused() {
    Locale locale = Locale.CANADA;
    assertThatThrownBy(() -> new AssistantQuestion("  ", ACCOUNT, locale))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AssistantQuestion("Why?", null, locale))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AssistantQuestion("Why?", ACCOUNT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("an empty transcript set answers nothing rather than failing to start")
  void anEmptyTranscriptSetAnswersNothing() {
    ReplayBillingAssistant empty = new ReplayBillingAssistant(Map.of());

    assertThat(empty.ask(question("Why is my balance 1,328.00?", "en-CA")).available()).isFalse();
    assertThat(empty.providerName()).isEqualTo("replay");
  }
}
