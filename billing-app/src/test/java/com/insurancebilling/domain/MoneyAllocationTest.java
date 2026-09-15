package com.insurancebilling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Splitting a total into installments that add back up to it.
 *
 * <p>This is the arithmetic every figure on a payment schedule rests on, so it is tested on its own
 * before anything builds a schedule out of it. The cases that matter are the ones where the division is
 * not exact — which, for real premiums over twelve months, is most of them.
 */
class MoneyAllocationTest {

  @Test
  @DisplayName("a total that divides evenly produces identical parts")
  void evenDivisionProducesIdenticalParts() {
    List<BigDecimal> parts = Money.allocate(new BigDecimal("1440.00"), 12);

    assertThat(parts).hasSize(12);
    assertThat(parts).allSatisfy(part -> assertThat(part).isEqualByComparingTo("120.00"));
  }

  @Test
  @DisplayName("the remainder of an uneven division lands on the first part")
  void remainderLandsOnTheFirstPart() {
    List<BigDecimal> parts = Money.allocate(new BigDecimal("1000.00"), 12);

    assertThat(parts.get(0))
        .as("the first part absorbs the four cents twelve parts of 83.33 would lose")
        .isEqualByComparingTo("83.37");
    assertThat(parts.subList(1, 12))
        .as("every part after the first is the same, which is what the policyholder is quoted")
        .allSatisfy(part -> assertThat(part).isEqualByComparingTo("83.33"));
  }

  @ParameterizedTest(name = "{0} over {1} parts sums back to {0}")
  @CsvSource({
    "1440.00, 12",
    "1000.00, 12",
    "0.01, 12",
    "0.05, 12",
    "0.11, 12",
    "999.99, 7",
    "1234.56, 5",
    "100.00, 3",
    "0.00, 12",
    "8.33, 2",
    "2400.00, 4",
    "1080.00, 1"
  })
  @DisplayName("every allocation sums back to the total it came from")
  void everyAllocationSumsBackToItsTotal(String total, int parts) {
    BigDecimal original = new BigDecimal(total);

    List<BigDecimal> allocation = Money.allocate(original, parts);

    assertThat(allocation).hasSize(parts);
    assertThat(Money.sum(allocation))
        .as("an allocation that does not sum back to its total has lost or invented money")
        .isEqualByComparingTo(original);
  }

  @Test
  @DisplayName("a single cent over twelve parts puts the cent first and leaves the rest at zero")
  void oneCentOverTwelvePartsIsNotRoundedAway() {
    List<BigDecimal> parts = Money.allocate(new BigDecimal("0.01"), 12);

    assertThat(parts.get(0)).isEqualByComparingTo("0.01");
    assertThat(parts.subList(1, 12)).allSatisfy(part -> assertThat(part).isEqualByComparingTo("0.00"));
  }

  @Test
  @DisplayName("splitting into one part returns the total unchanged")
  void singlePartReturnsTheTotal() {
    assertThat(Money.allocate(new BigDecimal("1080.00"), 1))
        .singleElement()
        .satisfies(part -> assertThat(part).isEqualByComparingTo("1080.00"));
  }

  @Test
  @DisplayName("every allocated part carries the platform's canonical scale")
  void everyPartIsAtTheCanonicalScale() {
    assertThat(Money.allocate(new BigDecimal("1000.00"), 7))
        .allSatisfy(part -> assertThat(part.scale()).isEqualTo(Money.SCALE));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, -12})
  @DisplayName("a part count below one is refused rather than returning an empty schedule")
  void partCountBelowOneIsRefused(int parts) {
    assertThatThrownBy(() -> Money.allocate(new BigDecimal("100.00"), parts))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot split an amount into");
  }

  @Test
  @DisplayName("a negative total is refused rather than silently producing negative installments")
  void negativeTotalIsRefused() {
    assertThatThrownBy(() -> Money.allocate(new BigDecimal("-100.00"), 12))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot split a negative amount");
  }

  @Test
  @DisplayName("a total carrying more precision than the platform stores is refused, not rounded")
  void overPreciseTotalIsRefusedRatherThanRounded() {
    assertThatThrownBy(() -> Money.allocate(new BigDecimal("100.005"), 12))
        .as("rounding here would silently change what the policyholder owes")
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  @DisplayName("the returned allocation cannot be modified by its caller")
  void allocationIsImmutable() {
    List<BigDecimal> parts = Money.allocate(new BigDecimal("120.00"), 2);

    assertThatThrownBy(() -> parts.set(0, BigDecimal.ZERO))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("summing an empty list gives zero at the canonical scale")
  void sumOfNothingIsZero() {
    assertThat(Money.sum(List.of())).isEqualByComparingTo("0.00");
  }
}
