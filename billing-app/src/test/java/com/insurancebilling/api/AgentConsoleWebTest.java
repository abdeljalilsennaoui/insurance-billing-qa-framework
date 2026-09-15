package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The agent console: the portfolio grid, and the term panels underneath it.
 *
 * <p>These run against rendered HTML rather than a browser because what is being checked is what the
 * server decided - which rows, in which order, adding up to what - and none of that needs a browser to
 * be wrong. The browser suite checks the things only a browser can: that the tab links navigate and
 * that the selected row stays selected.
 *
 * <p>The total is asserted against the sum of the rows on screen rather than against a literal. A
 * literal would have to be rewritten every time the seed data changed, and would stop being a check on
 * the arithmetic the moment somebody updated it to match a wrong answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentConsoleWebTest {

  private static final Pattern ROW =
      Pattern.compile("<tr [^>]*data-testid=\"portfolio-row\"[^>]*>(.*?)</tr>", Pattern.DOTALL);
  private static final Pattern TERM_OF_ROW = Pattern.compile("data-term=\"([^\"]+)\"");
  private static final Pattern ROW_BALANCE =
      Pattern.compile("data-testid=\"portfolio-balance\"\\s+data-amount=\"([^\"]+)\"");
  private static final Pattern TOTAL =
      Pattern.compile("data-testid=\"portfolio-total\"\\s+data-amount=\"([^\"]+)\"");

  @Autowired private MockMvc mockMvc;

  private String render(String path) throws Exception {
    return mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  // ------------------------------------------------------------------ grid

  @Test
  @DisplayName("the grid lists every term on the books")
  void theGridListsEveryTermOnTheBooks() throws Exception {
    String html = render("/agent");

    Assertions.assertThat(termReferencesIn(html))
        .as("both seeded terms should appear; an agent works from the whole book, not one account")
        .contains("SEED-TERM-001", "SEED-TERM-002");
  }

  @Test
  @DisplayName("the total is the sum of the balances shown above it")
  void theTotalIsTheSumOfTheBalancesShownAboveIt() throws Exception {
    String html = render("/agent");

    BigDecimal summed =
        rowBalancesIn(html).stream().reduce(BigDecimal.ZERO, BigDecimal::add);

    Assertions.assertThat(totalIn(html))
        .as("a total that disagrees with its own column is the one error a reader will catch by hand")
        .isEqualByComparingTo(summed);
  }

  @Test
  @DisplayName("the grid is ordered by policy number, not by the order the data was written")
  void theGridIsOrderedByPolicyNumber() throws Exception {
    List<String> policyNumbers = policyNumbersIn(render("/agent"));

    Assertions.assertThat(policyNumbers).isSorted();
  }

  @Test
  @DisplayName("a row carries its term and account references, so no test need count rows")
  void aRowCarriesItsTermAndAccountReferences() throws Exception {
    String html = render("/agent");

    Assertions.assertThat(html)
        .contains("data-term=\"SEED-TERM-001\"")
        .contains("data-account=\"ACCT-100001\"");
  }

  @Test
  @DisplayName("the product and the term status ride on attributes, not on display copy")
  void theProductAndStatusRideOnAttributes() throws Exception {
    String english = render("/agent");
    String french = render("/agent?lang=fr");

    Assertions.assertThat(english).contains("data-product=\"AUTO\"").contains("Auto");
    Assertions.assertThat(french)
        .as("the attribute is the same in both languages; only the words change")
        .contains("data-product=\"AUTO\"")
        .contains("Automobile");
  }

  @Test
  @DisplayName("an accented insured name survives the round trip to the page")
  void anAccentedInsuredNameSurvivesTheRoundTrip() throws Exception {
    Assertions.assertThat(render("/agent"))
        .as("a bundle or response read as ISO-8859-1 would render this as Ã‰lise")
        .contains("Élise Marchand");
  }

  // --------------------------------------------------------------- selection

  @Test
  @DisplayName("naming a term shows that term's figures")
  void namingATermShowsThatTermsFigures() throws Exception {
    String html = render("/agent?term=SEED-TERM-002");

    Assertions.assertThat(html).contains("data-testid=\"term-header\" data-term=\"SEED-TERM-002\"");
  }

  @Test
  @DisplayName("naming no term falls back to the first row rather than showing nothing")
  void namingNoTermFallsBackToTheFirstRow() throws Exception {
    String html = render("/agent");

    Assertions.assertThat(html)
        .as("/agent must be a URL an agent can bookmark without knowing a term reference")
        .contains("data-testid=\"term-header\" data-term=\"SEED-TERM-001\"");
  }

  @Test
  @DisplayName("a term reference that does not exist falls back rather than failing")
  void anUnknownTermFallsBack() throws Exception {
    String html = render("/agent?term=NOT-A-TERM");

    Assertions.assertThat(html).contains("data-testid=\"term-header\"");
  }

  @Test
  @DisplayName("the selected row is marked as selected")
  void theSelectedRowIsMarkedAsSelected() throws Exception {
    String html = render("/agent?term=SEED-TERM-002");

    Assertions.assertThat(selectedRowTermIn(html))
        .as("the panels below belong to one row; the reader has to be able to see which")
        .isEqualTo("SEED-TERM-002");
  }

  // -------------------------------------------------------------------- tabs

  @Test
  @DisplayName("each tab renders its own panel")
  void eachTabRendersItsOwnPanel() throws Exception {
    Assertions.assertThat(render("/agent?tab=summary")).contains("data-testid=\"term-summary\"");
    Assertions.assertThat(render("/agent?tab=schedule")).contains("data-testid=\"schedule-table\"");
    Assertions.assertThat(render("/agent?tab=transactions"))
        .contains("data-testid=\"ledger-table\"");
  }

  @Test
  @DisplayName("a tab the console does not have falls back to the summary")
  void anUnknownTabFallsBackToTheSummary() throws Exception {
    Assertions.assertThat(render("/agent?tab=nonsense"))
        .as("a mistyped link should show a panel, not a blank page")
        .contains("data-testid=\"term-summary\"");
  }

  @Test
  @DisplayName("the tab links stay on the agent console and keep the term they were opened from")
  void theTabLinksStayOnTheAgentConsole() throws Exception {
    String html = render("/agent?term=SEED-TERM-002");

    Assertions.assertThat(html)
        .as("tabs built for the policyholder's screen would navigate an agent off their own console")
        .contains("/agent?term=SEED-TERM-002&amp;tab=schedule")
        .contains("/agent?term=SEED-TERM-002&amp;tab=transactions");
  }

  // ------------------------------------------- the same figures, both consoles

  @Test
  @DisplayName("the agent and the policyholder are shown the same figures for the same term")
  void bothConsolesShowTheSameFigures() throws Exception {
    // The whole reason the panels are one fragment rather than two copies. An agent quoting a balance
    // the policyholder cannot see on their own screen is the failure this guards.
    String agent = render("/agent?term=SEED-TERM-002&tab=schedule");
    String policyholder = render("/accounts/ACCT-100002/terms?tab=schedule");

    Assertions.assertThat(amountsDueIn(agent))
        .as("the schedule is the same schedule; only the surrounding page differs")
        .isEqualTo(amountsDueIn(policyholder))
        .isNotEmpty();
  }

  // ------------------------------------------------------------ localisation

  @Test
  @DisplayName("no panel of the agent console renders an unresolved message key")
  void noPanelRendersAnUnresolvedMessageKey() throws Exception {
    for (String path :
        new String[] {
          "/agent",
          "/agent?lang=fr",
          "/agent?tab=schedule",
          "/agent?tab=schedule&lang=fr",
          "/agent?tab=transactions",
          "/agent?tab=transactions&lang=fr"
        }) {
      Assertions.assertThat(render(path))
          .as("%s renders an unresolved key; Thymeleaf writes ??key?? rather than failing", path)
          .doesNotContain("??");
    }
  }

  @Test
  @DisplayName("the language switch keeps the agent on the term and tab they were reading")
  void theLanguageSwitchKeepsTheAgentInPlace() throws Exception {
    String html = render("/agent?term=SEED-TERM-002&tab=schedule");

    Assertions.assertThat(html)
        .as("a switch that dropped the query would send the agent back to the first term's summary")
        .contains("/agent?term=SEED-TERM-002&amp;tab=schedule&amp;lang=fr");
  }

  // ---------------------------------------------------------------- helpers

  private static List<String> termReferencesIn(String html) {
    return ROW.matcher(html)
        .results()
        .map(match -> firstGroup(TERM_OF_ROW, match.group()))
        .toList();
  }

  private static List<String> policyNumbersIn(String html) {
    return ROW.matcher(html)
        .results()
        .map(
            match ->
                firstGroup(
                    Pattern.compile("data-testid=\"portfolio-policy-number\"[^>]*>([^<]*)"),
                    match.group()))
        .map(String::trim)
        .toList();
  }

  private static List<BigDecimal> rowBalancesIn(String html) {
    return ROW.matcher(html)
        .results()
        .map(match -> new BigDecimal(firstGroup(ROW_BALANCE, match.group())))
        .toList();
  }

  private static List<String> amountsDueIn(String html) {
    return Pattern.compile("data-testid=\"installment-amount\"\\s+data-amount=\"([^\"]+)\"")
        .matcher(html)
        .results()
        .map(match -> match.group(1))
        .toList();
  }

  private static String selectedRowTermIn(String html) {
    return ROW.matcher(html)
        .results()
        .map(java.util.regex.MatchResult::group)
        .filter(row -> row.contains("class=\"current\""))
        .map(row -> firstGroup(TERM_OF_ROW, row))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No row on the grid is marked as selected"));
  }

  private static BigDecimal totalIn(String html) {
    return new BigDecimal(firstGroup(TOTAL, html));
  }

  private static String firstGroup(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    if (!matcher.find()) {
      throw new AssertionError("No match for " + pattern + " in:\n" + text);
    }
    return matcher.group(1);
  }
}
