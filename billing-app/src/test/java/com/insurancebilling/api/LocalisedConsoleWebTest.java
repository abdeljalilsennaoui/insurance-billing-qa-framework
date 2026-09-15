package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The console rendered in each of its two languages.
 *
 * <p>The assertion that matters most is the one about {@code ??}: Thymeleaf renders an unresolved
 * message key as {@code ??key??} rather than failing, so a missing translation reaches production as a
 * page that renders perfectly well and says nothing. Scanning the rendered HTML for that marker turns a
 * silent defect into a failing test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LocalisedConsoleWebTest {

  @Autowired private MockMvc mockMvc;

  private String render(String path) throws Exception {
    return mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @DisplayName("the invoice list renders in English by default")
  void theInvoiceListRendersInEnglishByDefault() throws Exception {
    String html = render("/invoices");

    Assertions.assertThat(html).contains("Filter by status").contains("Outstanding");
  }

  @Test
  @DisplayName("the invoice list renders in French when asked")
  void theInvoiceListRendersInFrench() throws Exception {
    String html = render("/invoices?lang=fr");

    Assertions.assertThat(html).contains("Filtrer par statut").contains("Solde");
    Assertions.assertThat(html).doesNotContain("Filter by status");
  }

  @Test
  @DisplayName("no page in either language renders an unresolved message key")
  void noPageRendersAnUnresolvedMessageKey() throws Exception {
    for (String path :
        new String[] {
          "/invoices", "/invoices?lang=fr", "/invoices?lang=en",
          "/invoices?status=OVERDUE", "/invoices?status=OVERDUE&lang=fr"
        }) {
      Assertions.assertThat(render(path))
          .as("%s renders an unresolved key; Thymeleaf writes ??key?? rather than failing", path)
          .doesNotContain("??");
    }
  }

  @Test
  @DisplayName("an invoice page and the not-found page resolve every key in both languages")
  void theRemainingPagesResolveEveryKeyInBothLanguages() throws Exception {
    Assertions.assertThat(render("/invoices/1")).doesNotContain("??");
    Assertions.assertThat(render("/invoices/1?lang=fr")).doesNotContain("??");

    for (String path : new String[] {"/invoices/999999", "/invoices/999999?lang=fr"}) {
      String html =
          mockMvc
              .perform(get(path))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse()
              .getContentAsString();
      Assertions.assertThat(html).as("%s", path).doesNotContain("??");
    }
  }

  @Test
  @DisplayName("amounts are written the way the reader's language writes them")
  void amountsAreWrittenInTheReadersLanguage() throws Exception {
    Assertions.assertThat(render("/invoices/1")).contains("$360.00");
    Assertions.assertThat(render("/invoices/1?lang=fr")).contains("360,00 $");
  }

  @Test
  @DisplayName("the raw status rides on an attribute so automation need not read display copy")
  void theRawStatusRidesOnAnAttribute() throws Exception {
    String english = render("/invoices");
    String french = render("/invoices?lang=fr");

    Assertions.assertThat(english).contains("data-status=\"PARTIALLY_PAID\"").contains("Partially paid");
    Assertions.assertThat(french)
        .as("the attribute is the same in both languages; only the words change")
        .contains("data-status=\"PARTIALLY_PAID\"")
        .contains("Partiellement payée");
  }

  @Test
  @DisplayName("the language switch keeps the reader on the page they were looking at")
  void theLanguageSwitchKeepsTheReaderInPlace() throws Exception {
    String html = render("/invoices/1");

    Assertions.assertThat(html)
        .as("a switch that sent everyone back to the list would lose their place")
        .contains("/invoices/1?lang=fr")
        .contains("/invoices/1?lang=en");
  }

  @Test
  @DisplayName("the language switch keeps the query string, not just the path")
  void theLanguageSwitchKeepsTheQueryString() throws Exception {
    // Found by a browser test: switching language on the schedule tab dropped the reader back to the
    // summary, because the link kept only the path. The same fault lost the status filter on the
    // invoice list. Asserted here as well because this runs in milliseconds.
    Assertions.assertThat(render("/invoices?status=OVERDUE"))
        .contains("/invoices?status=OVERDUE&amp;lang=fr")
        .contains("/invoices?status=OVERDUE&amp;lang=en");
  }

  @Test
  @DisplayName("the switch does not carry the previous language alongside the new one")
  void theSwitchDoesNotCarryThePreviousLanguage() throws Exception {
    String html = render("/invoices?status=OVERDUE&lang=fr");

    Assertions.assertThat(html)
        .as("lang must be replaced, not appended, or the first value would win")
        .contains("/invoices?status=OVERDUE&amp;lang=en")
        .doesNotContain("lang=fr&amp;lang=en");
  }

  @Test
  @DisplayName("the page declares the language it is written in")
  void thePageDeclaresItsLanguage() throws Exception {
    Assertions.assertThat(render("/invoices")).contains("lang=\"en\"");
    Assertions.assertThat(render("/invoices?lang=fr")).contains("lang=\"fr\"");
  }

  @Test
  @DisplayName("a language the console is not published in leaves it as it was")
  void anUnknownLanguageLeavesTheConsoleAsItWas() throws Exception {
    Assertions.assertThat(render("/invoices?lang=de"))
        .as("a mistyped link should show the previous language, not an error page")
        .contains("Filter by status");
  }

  @Test
  @DisplayName("the response is served as UTF-8, so accented French reaches the browser intact")
  void theResponseIsServedAsUtf8() throws Exception {
    mockMvc
        .perform(get("/invoices?lang=fr"))
        .andExpect(status().isOk())
        .andExpect(content().encoding("UTF-8"));
  }
}
