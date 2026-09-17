package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insurancebilling.qa.api.assertions.Grounding;
import com.insurancebilling.qa.api.model.AssistantAnswerDto;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;

/**
 * Whether the assistant's answers are made of the platform's figures or of its own.
 *
 * <p>This is the assertion the rest of the assistant work exists to make possible. The words in an
 * answer vary and cannot be asserted on; the arithmetic cannot. Every monetary figure in an answer has
 * to appear in the billing data the answer says it read, fetched here over the public API rather than
 * from the application's own classes - otherwise the check would be comparing the assistant against
 * itself.
 *
 * <p>These run against whichever provider the application is configured with. Under the shipped
 * {@code replay} provider they hold recorded answers to the live database, which is a real check: the
 * recordings were written against this data and a change to either has to break one of these. Under a
 * live provider they are the same assertion against a model's own words.
 */
public class AssistantGroundingApiIT extends BaseApiTest {

  private static final String SEEDED_ACCOUNT = "ACCT-100001";
  private static final String RECORDED_QUESTION = "Why is my balance 1,328.00?";

  @Test(groups = {"smoke", "regression"})
  public void everyFigureInAnAnswerAppearsInTheDataBehindIt() {
    AssistantAnswerDto answer = assistant.ask(SEEDED_ACCOUNT, RECORDED_QUESTION);
    assertThat(answer.available()).as("nothing to check: the assistant gave no answer").isTrue();

    List<String> quoted = Grounding.figuresIn(answer.answer());
    Set<String> published = Grounding.figuresBehind(billing, answer);

    assertThat(quoted).as("an answer about a bill that quotes no figure at all").isNotEmpty();
    assertThat(quoted)
        .as("figures in the answer that appear in none of %s", answer.toolCalls())
        .allSatisfy(figure -> assertThat(published).contains(figure));
  }

  @Test(groups = "regression")
  public void anAnswerNamesTheCallsItWasBuiltFrom() {
    AssistantAnswerDto answer = assistant.ask(SEEDED_ACCOUNT, RECORDED_QUESTION);

    assertThat(answer.toolCalls())
        .as("an answer about money with no stated provenance")
        .isNotEmpty();
    assertThat(answer.toolCalls())
        .allSatisfy(call -> assertThat(call.argument()).isNotBlank());
  }

  /**
   * The check has to be able to fail, or it is decoration.
   *
   * <p>A grounding assertion nobody has seen fail is a grounding assertion nobody knows works. This
   * feeds it an answer with a figure that is not in the account and proves it is rejected.
   */
  @Test(groups = "regression")
  public void theGroundingCheckRejectsAFigureThatIsNotInTheData() {
    AssistantAnswerDto real = assistant.ask(SEEDED_ACCOUNT, RECORDED_QUESTION);

    // The same answer with one figure replaced by one the account does not contain. Everything else -
    // the tool calls, and therefore the data the check reads - is exactly what the real answer had.
    AssistantAnswerDto tampered =
        new AssistantAnswerDto(
            real.question(),
            "Your balance is 9999.99.",
            true,
            real.provider(),
            real.toolCalls(),
            real.usage());

    List<String> quoted = Grounding.figuresIn(tampered.answer());
    Set<String> published = Grounding.figuresBehind(billing, tampered);

    assertThat(published)
        .as("9999.99 is in the seeded data, so this test would prove nothing")
        .doesNotContain("9999.99");

    // The assertion the other tests rely on, run against a bad answer, must fail. A grounding check
    // nobody has watched fail is a grounding check nobody knows works.
    assertThatThrownBy(
            () -> assertThat(quoted).allSatisfy(figure -> assertThat(published).contains(figure)))
        .isInstanceOf(AssertionError.class);
  }

  @Test(groups = "regression")
  public void theFigureExtractorDoesNotInventFiguresOutOfLongerOnes() {
    // 1328.00 must not also read as 328.00: without that, a check could pass on a figure nobody wrote.
    assertThat(Grounding.figuresIn("The balance is 1328.00 today")).containsExactly("1328");
    assertThat(Grounding.figuresIn("no figures here")).isEmpty();
  }

  @Test(groups = "regression")
  public void anUnansweredQuestionCarriesNoFiguresAndNoProvenance() {
    AssistantAnswerDto answer =
        assistant.ask(SEEDED_ACCOUNT, "What is the airspeed velocity of an unladen swallow?");

    assertThat(answer.available()).isFalse();
    assertThat(answer.toolCalls()).isEmpty();
    assertThat(Grounding.figuresIn(answer.answer())).isEmpty();
  }
}
