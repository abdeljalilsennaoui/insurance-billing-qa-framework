package com.insurancebilling.qa.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.assertions.Grounding;
import com.insurancebilling.qa.api.client.BillingApiClient;
import com.insurancebilling.qa.api.model.AssistantAnswerDto;
import com.insurancebilling.qa.api.model.AssistantToolCallDto;
import com.insurancebilling.qa.ui.pages.AgentConsolePage;
import com.insurancebilling.qa.ui.pages.TermsPage;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Glue for the billing assistant scenarios.
 *
 * <p>No locator and no {@code WebDriver} reference appears here: the page objects in
 * {@code qa-ui-tests} do the driving, and the grounding check is the same
 * {@link Grounding} helper the API suite uses. One implementation of "every figure came from the
 * data" rather than two that could disagree about what grounded means.
 *
 * <p>The assistant's wording is never asserted. An answer phrased three ways is still the same answer,
 * and a scenario pinned to one phrasing would fail on a correct one. What is asserted is the
 * arithmetic, the provenance and the refusals - the parts that have a right answer.
 */
public class BillingAssistantUiSteps {

  private static final String SEEDED_ACCOUNT = "ACCT-100001";
  private static final String QUESTION = "Why is my balance 1,328.00?";
  private static final String QUESTION_FR = "Pourquoi mon solde est-il de 1 328,00 $ ?";

  private final BillingApiClient billing = new BillingApiClient();

  private TermsPage policyholderPage;
  private AgentConsolePage agentPage;
  private String policyholderAnswer;
  private List<String> policyholderTrace;
  private List<String> policyholderTraceArguments;

  @Given("my billing account is open in {string}")
  public void myBillingAccountIsOpenIn(String language) {
    policyholderPage = new TermsPage().open(SEEDED_ACCOUNT);
    if (!"English".equals(language)) {
      policyholderPage = policyholderPage.switchLanguageTo("fr");
    }
  }

  @When("I ask the assistant why my balance is what it is")
  public void iAskTheAssistantWhyMyBalanceIsWhatItIs() {
    policyholderPage.askAssistant(QUESTION);
    policyholderAnswer = policyholderPage.assistantAnswerText();
    policyholderTrace = policyholderPage.assistantTrace();
    policyholderTraceArguments = policyholderPage.assistantTraceArguments();
  }

  @When("I ask the assistant in French why my balance is what it is")
  public void iAskTheAssistantInFrench() {
    policyholderPage.askAssistant(QUESTION_FR);
    policyholderAnswer = policyholderPage.assistantAnswerText();
    policyholderTrace = policyholderPage.assistantTrace();
    policyholderTraceArguments = policyholderPage.assistantTraceArguments();
  }

  @When("I ask the assistant something it has no answer for")
  public void iAskSomethingItHasNoAnswerFor() {
    policyholderPage.askAssistant("What is the airspeed velocity of an unladen swallow?");
  }

  @When("an agent asks the assistant the same question")
  public void anAgentAsksTheSameQuestion() {
    // On the policyholder's own term. Left to choose, the console selects the first term by policy
    // number, and after any suite has created data that is somebody else's.
    agentPage = new AgentConsolePage().open(policyholderPage.termReference(), "summary");
    agentPage.askAssistant(QUESTION);
  }

  @Then("the assistant answers")
  public void theAssistantAnswers() {
    assertThat(policyholderPage.showsAssistantAnswer()).isTrue();
    assertThat(policyholderPage.assistantAnswered()).isTrue();
  }

  @Then("the assistant says it has no answer")
  public void theAssistantSaysItHasNoAnswer() {
    assertThat(policyholderPage.showsAssistantAnswer()).isTrue();
    assertThat(policyholderPage.assistantAnswered()).isFalse();
  }

  /**
   * The grounding check, in a browser.
   *
   * <p>The records the screen says the answer was read from are fetched over the API, and every figure
   * the answer states has to appear in them. That is the API suite's check fed from the page instead of
   * from JSON. A figure nowhere in those records means the assistant invented something about
   * somebody's money.
   */
  @Then("every figure in the answer appears in my account")
  public void everyFigureInTheAnswerAppearsInMyAccount() {
    List<AssistantToolCallDto> callsOnScreen =
        IntStream.range(0, policyholderTrace.size())
            .mapToObj(
                i ->
                    new AssistantToolCallDto(
                        policyholderTrace.get(i), policyholderTraceArguments.get(i)))
            .toList();
    AssistantAnswerDto onScreen =
        new AssistantAnswerDto(QUESTION, policyholderAnswer, true, null, callsOnScreen, null);

    List<String> quoted = Grounding.figuresIn(policyholderAnswer);
    Set<String> published = Grounding.figuresBehind(billing, onScreen);

    assertThat(quoted).as("an answer about a bill quoting no figure at all").isNotEmpty();
    assertThat(quoted)
        .as("figures on screen that appear in none of %s", callsOnScreen)
        .allSatisfy(figure -> assertThat(published).contains(figure));
  }

  @Then("the answer says which billing records it read")
  public void theAnswerSaysWhichBillingRecordsItRead() {
    assertThat(policyholderTrace).as("an answer about money with no stated provenance").isNotEmpty();
  }

  @Then("the answer cites no billing records")
  public void theAnswerCitesNoBillingRecords() {
    assertThat(policyholderPage.assistantTrace()).isEmpty();
  }

  @Then("both are given the same answer")
  public void bothAreGivenTheSameAnswer() {
    assertThat(agentPage.assistantAnswerText())
        .as("the agent is being told something different from the policyholder")
        .isEqualTo(policyholderAnswer);
  }

  @Then("both answers cite the same billing records")
  public void bothAnswersCiteTheSameBillingRecords() {
    assertThat(agentPage.assistantTrace()).isEqualTo(policyholderTrace);
  }

  @Then("the answer is in French")
  public void theAnswerIsInFrench() {
    // A word that exists in the French answer and in no English one. Asserting on the answer's own
    // language is the one place display copy has to be read - it is the thing under test.
    assertThat(policyholderAnswer).contains("solde");
  }

  @Then("the console says which assistant answered")
  public void theConsoleSaysWhichAssistantAnswered() {
    assertThat(policyholderPage.assistantProvider())
        .as("the screen does not say whether this answer was produced or replayed")
        .isNotBlank();
  }
}
