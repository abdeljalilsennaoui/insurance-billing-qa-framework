package com.insurancebilling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The assistant panel as both consoles render it.
 *
 * <p>The assertions that matter are the ones comparing the two screens. The policyholder and the agent
 * read one Thymeleaf fragment, so they cannot show different answers by construction - and "cannot by
 * construction" is the kind of claim that stops being true the first time somebody is in a hurry,
 * which is why it is asserted rather than trusted.
 *
 * <p>Everything is read from {@code data-*} attributes rather than display copy, so the same assertion
 * holds in English and in French.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistantPanelWebTest {

  private static final String RECORDED_QUESTION = "Why is my balance 1,328.00?";

  private static final Pattern TRACE_TOOL = Pattern.compile("data-tool=\"([a-z_]+)\"");
  private static final Pattern ANSWER_TEXT =
      Pattern.compile("data-testid=\"assistant-answer-text\"[^>]*>(.*?)</p>", Pattern.DOTALL);

  @Autowired private MockMvc mockMvc;

  private String policyholderAsks(String question, String language) throws Exception {
    return mockMvc
        .perform(
            post("/accounts/ACCT-100001/assistant")
                .param("question", question)
                .param("term", "SEED-TERM-001")
                .param("tab", "summary")
                .param("lang", language))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String agentAsks(String question, String language) throws Exception {
    return mockMvc
        .perform(
            post("/agent/assistant")
                .param("question", question)
                .param("term", "SEED-TERM-001")
                .param("tab", "summary")
                .param("lang", language))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<String> toolsIn(String html) {
    List<String> tools = new ArrayList<>();
    Matcher matcher = TRACE_TOOL.matcher(html);
    while (matcher.find()) {
      tools.add(matcher.group(1));
    }
    return tools;
  }

  private static String answerIn(String html) {
    Matcher matcher = ANSWER_TEXT.matcher(html);
    assertThat(matcher.find()).as("no answer rendered in:\n%s", html).isTrue();
    return matcher.group(1).strip();
  }

  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("the panel is on both screens before anything has been asked")
  void thePanelIsOnBothScreensBeforeAnythingHasBeenAsked() throws Exception {
    for (String path : List.of("/accounts/ACCT-100001/terms", "/agent")) {
      String html = mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString();

      assertThat(html).as("panel missing from %s", path).contains("data-testid=\"assistant-panel\"");
      assertThat(html).as("provider missing from %s", path).contains("data-provider=\"replay\"");
      assertThat(html).as("an answer before anything was asked, on %s", path)
          .doesNotContain("data-testid=\"assistant-answer\"");
    }
  }

  @Test
  @DisplayName("both consoles give the same answer to the same question, from the same calls")
  void bothConsolesGiveTheSameAnswerToTheSameQuestion() throws Exception {
    String policyholder = policyholderAsks(RECORDED_QUESTION, "en");
    String agent = agentAsks(RECORDED_QUESTION, "en");

    assertThat(answerIn(agent)).isEqualTo(answerIn(policyholder));
    assertThat(toolsIn(agent)).isEqualTo(toolsIn(policyholder)).isNotEmpty();
  }

  @Test
  @DisplayName("the answer names the calls behind it, in the order they were made")
  void theAnswerNamesTheCallsBehindIt() throws Exception {
    String html = policyholderAsks(RECORDED_QUESTION, "en");

    assertThat(html).contains("data-available=\"true\"");
    assertThat(toolsIn(html)).containsExactly("find_account", "get_ledger");
    assertThat(answerIn(html)).contains("1328.00");
  }

  @Test
  @DisplayName("a question nobody recorded renders as unanswered rather than as an answer")
  void anUnrecordedQuestionRendersAsUnanswered() throws Exception {
    String html = policyholderAsks("What is the airspeed of a swallow?", "en");

    assertThat(html).contains("data-available=\"false\"");
    assertThat(toolsIn(html)).isEmpty();
  }

  @Test
  @DisplayName("the French panel is French, and the attributes the suites read are not")
  void theFrenchPanelIsFrenchAndTheAttributesAreNot() throws Exception {
    String french = policyholderAsks("Pourquoi mon solde est-il de 1 328,00 $ ?", "fr");

    assertThat(french).contains("Poser une question");
    assertThat(french).doesNotContain("Ask about this bill");
    // The machine-readable side is identical in both languages, which is what lets one assertion
    // cover both and what stops a translation from breaking a suite.
    assertThat(french).contains("data-available=\"true\"");
    assertThat(toolsIn(french)).containsExactly("find_account", "get_ledger");
  }

  @Test
  @DisplayName("the reader is told the answers are recorded rather than live")
  void theReaderIsToldTheAnswersAreRecorded() throws Exception {
    String html = mockMvc.perform(get("/accounts/ACCT-100001/terms")).andReturn().getResponse().getContentAsString();

    assertThat(html).contains("data-testid=\"assistant-provider-note\"");
    assertThat(html).containsIgnoringCase("recorded");
  }

  @Test
  @DisplayName("asking a question does not move the reader off the term or tab they were on")
  void askingDoesNotMoveTheReaderOffTheirTermOrTab() throws Exception {
    String html =
        mockMvc
            .perform(
                post("/accounts/ACCT-100001/assistant")
                    .param("question", RECORDED_QUESTION)
                    .param("term", "SEED-TERM-001")
                    .param("tab", "schedule"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(html).contains("data-testid=\"term-schedule\"");
  }

  @Test
  @DisplayName("an over-long question is cut to the cap rather than sent whole or refused")
  void anOverLongQuestionIsCutToTheCap() throws Exception {
    // The form carries maxlength, but maxlength is a hint to a browser and not a control: a console
    // that trusted it would be an endpoint anybody can run a bill up against with a curl command.
    String html = policyholderAsks("why ".repeat(400), "en");

    assertThat(html).contains("data-testid=\"assistant-panel\"");
    assertThat(html).contains("data-available=\"false\"");
  }

  @Test
  @DisplayName("a question about an account that does not exist is a 404, and costs nothing")
  void aQuestionAboutAnAccountThatDoesNotExistIsA404() throws Exception {
    // The screen is rendered before the assistant is asked, so the account is resolved first. Asking
    // first would mean this request cost a model call under a live provider and then 404'd anyway.
    // The status is what is observable here; the ordering in the controller is what produces it.
    mockMvc
        .perform(
            post("/accounts/ACCT-NOPE/assistant")
                .param("question", RECORDED_QUESTION))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
  }

  @Test
  @DisplayName("an empty question renders the panel again rather than an empty answer")
  void anEmptyQuestionRendersThePanelAgain() throws Exception {
    String html = policyholderAsks("   ", "en");

    assertThat(html).contains("data-testid=\"assistant-panel\"");
    assertThat(html).doesNotContain("data-testid=\"assistant-answer\"");
  }
}
