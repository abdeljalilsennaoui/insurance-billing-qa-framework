package com.insurancebilling.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Money and dates written the way the reader's language writes them.
 *
 * <p>This is the class of defect a second locale exists to expose. {@code 1591.60} is not wrong in
 * English and wrong in French — it is wrong in French only, which means a suite that only ever renders
 * English cannot see it at all.
 */
class BillingFormatsTest {

  private static final Locale EN = Locale.of("en", "CA");
  private static final Locale FR = Locale.of("fr", "CA");

  private final BillingFormats formats = new BillingFormats();

  @Test
  @DisplayName("English writes the symbol first and groups with commas")
  void englishWritesTheSymbolFirst() {
    assertThat(formats.money(new BigDecimal("1591.60"), EN)).isEqualTo("$1,591.60");
    assertThat(formats.money(new BigDecimal("0.00"), EN)).isEqualTo("$0.00");
  }

  @Test
  @DisplayName("French writes the symbol last and uses a comma for the decimal")
  void frenchWritesTheSymbolLast() {
    assertThat(formats.money(new BigDecimal("1591.60"), FR)).isEqualTo("1 591,60 $");
    assertThat(formats.money(new BigDecimal("92.83"), FR)).isEqualTo("92,83 $");
  }

  @Test
  @DisplayName("the thousands separator is a space anyone can type, not one only a font can tell apart")
  void theThousandsSeparatorIsAnOrdinarySpace() {
    String french = formats.money(new BigDecimal("1591.60"), FR);

    assertThat(french)
        .as("a narrow no-break space renders identically and fails assertions invisibly")
        .doesNotContain(" ")
        .doesNotContain(" ");
    assertThat(french.chars().filter(character -> character == ' ').count()).isEqualTo(2);
  }

  @Test
  @DisplayName("a negative amount keeps its sign in both languages")
  void aNegativeAmountKeepsItsSign() {
    assertThat(formats.money(new BigDecimal("-130.80"), EN)).contains("130.80").contains("-");
    assertThat(formats.money(new BigDecimal("-130.80"), FR)).contains("130,80").contains("-");
  }

  @Test
  @DisplayName("dates are written in the reader's language")
  void datesAreWrittenInTheReadersLanguage() {
    LocalDate date = LocalDate.of(2026, 9, 14);

    String english = formats.date(date, EN);
    String french = formats.date(date, FR);

    assertThat(english).contains("2026").contains("14");
    assertThat(french).contains("2026").contains("14");
    assertThat(french)
        .as("if the two locales render a date identically, nothing here is locale-driven")
        .isNotEqualTo(english);
  }

  @Test
  @DisplayName("a locale the console is not published in falls back rather than mixing conventions")
  void anUnsupportedLocaleFallsBack() {
    assertThat(formats.money(new BigDecimal("1591.60"), Locale.GERMANY))
        .as("German number formatting beside English words is worse than either alone")
        .isEqualTo("$1,591.60");
  }

  @Test
  @DisplayName("a missing locale falls back to the default rather than throwing")
  void aMissingLocaleFallsBack() {
    assertThat(formats.money(new BigDecimal("10.00"), null)).isEqualTo("$10.00");
    assertThat(formats.date(LocalDate.of(2026, 9, 14), null)).isNotBlank();
  }

  @Test
  @DisplayName("a missing amount or date renders as nothing, not as the word null")
  void missingValuesRenderAsNothing() {
    assertThat(formats.money(null, EN)).isEmpty();
    assertThat(formats.date(null, EN)).isEmpty();
  }
}
