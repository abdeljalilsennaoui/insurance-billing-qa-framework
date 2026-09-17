package com.insurancebilling.qa.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.ui.pages.AgentConsolePage;
import com.insurancebilling.qa.ui.pages.TermsPage;
import java.util.List;
import org.testng.annotations.Test;

/**
 * The assistant panel in a real browser, on both consoles.
 *
 * <p>The API suite already checks what the endpoint returns. What a browser adds is whether a person
 * can actually use it: that the form submits, that the answer comes back on the page they were on, and
 * that the two consoles show the same thing. A panel that returns the right JSON and renders nowhere
 * is not a feature.
 *
 * <p>Every assertion reads a {@code data-*} attribute rather than display copy, so the same test holds
 * when the page is in French.
 */
public class AssistantPanelUiIT extends BaseUiTest {

  private static final String SEEDED_ACCOUNT = "ACCT-100001";
  private static final String RECORDED_QUESTION = "Why is my balance 1,328.00?";
  private static final String RECORDED_QUESTION_FR = "Pourquoi mon solde est-il de 1 328,00 $ ?";

  @Test(groups = {"ui-smoke", "ui-regression"})
  public void thePolicyholderCanAskAQuestionAndReadTheAnswer() {
    TermsPage page = new TermsPage().open(SEEDED_ACCOUNT);

    assertThat(page.showsAssistantPanel()).isTrue();
    assertThat(page.showsAssistantAnswer()).as("an answer before anything was asked").isFalse();

    page.askAssistant(RECORDED_QUESTION);

    assertThat(page.showsAssistantAnswer()).isTrue();
    assertThat(page.assistantAnswered()).isTrue();
    assertThat(page.assistantAnswerText()).contains("1328.00");
  }

  @Test(groups = "ui-regression")
  public void theAnswerShowsTheCallsItWasBuiltFrom() {
    TermsPage page = new TermsPage().open(SEEDED_ACCOUNT);
    String termOnScreen = page.termReference();
    page.askAssistant(RECORDED_QUESTION);

    assertThat(page.assistantTrace()).containsExactly("find_account", "get_ledger");
    assertThat(page.assistantTraceArguments())
        .as("a call reaching outside the account being looked at")
        .allSatisfy(argument -> assertThat(argument).isIn(SEEDED_ACCOUNT, termOnScreen));
  }

  @Test(groups = "ui-regression")
  public void bothConsolesAnswerTheSameQuestionTheSameWay() {
    TermsPage policyholder = new TermsPage().open(SEEDED_ACCOUNT);
    policyholder.askAssistant(RECORDED_QUESTION);
    String policyholderAnswer = policyholder.assistantAnswerText();
    List<String> policyholderTrace = policyholder.assistantTrace();

    // Opened on the policyholder's own term. Left to choose, the console selects the first term by
    // policy number, and after any suite has created data that is somebody else's.
    AgentConsolePage agent = new AgentConsolePage().open(policyholder.termReference(), "summary");
    agent.askAssistant(RECORDED_QUESTION);

    assertThat(agent.assistantAnswerText())
        .as("the agent is being told something different from the policyholder")
        .isEqualTo(policyholderAnswer);
    assertThat(agent.assistantTrace()).isEqualTo(policyholderTrace);
  }

  @Test(groups = "ui-regression")
  public void aQuestionNobodyRecordedRendersAsUnanswered() {
    TermsPage page = new TermsPage().open(SEEDED_ACCOUNT);

    page.askAssistant("What is the airspeed velocity of an unladen swallow?");

    assertThat(page.showsAssistantAnswer()).isTrue();
    assertThat(page.assistantAnswered()).as("an invented answer to an unrecorded question").isFalse();
    assertThat(page.assistantTrace()).isEmpty();
  }

  @Test(groups = "ui-regression")
  public void theReaderIsToldWhichAssistantAnswered() {
    TermsPage page = new TermsPage().open(SEEDED_ACCOUNT);

    // Not asserted as "replay" specifically: the provider is configuration, and a suite that failed
    // when the application was pointed at a live model would be testing the configuration. What must
    // hold is that the screen says which one it is, so a reader can tell a recording from a live call.
    assertThat(page.assistantProvider()).isNotBlank();
  }

  @Test(groups = "ui-regression")
  public void theFrenchPanelAnswersInFrenchAndReadsTheSame() {
    TermsPage page = new TermsPage().open(SEEDED_ACCOUNT).switchLanguageTo("fr");

    page.askAssistant(RECORDED_QUESTION_FR);

    assertThat(page.assistantAnswered()).isTrue();
    assertThat(page.assistantAnswerText()).contains("solde");
    // The machine-readable side is language-independent, which is what lets this assertion be the
    // same one the English test makes.
    assertThat(page.assistantTrace()).containsExactly("find_account", "get_ledger");
  }

  @Test(groups = "ui-regression")
  public void askingAQuestionLeavesTheReaderOnTheTabTheyWereOn() {
    TermsPage page = new TermsPage().open(SEEDED_ACCOUNT, "schedule");
    assertThat(page.showsSchedule()).isTrue();

    page.askAssistant(RECORDED_QUESTION);

    assertThat(page.showsSchedule())
        .as("asking a question moved the reader off the schedule they were reading")
        .isTrue();
  }
}
