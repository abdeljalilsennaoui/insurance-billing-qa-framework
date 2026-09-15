package com.insurancebilling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the platform will and will not hold about a policyholder's bank account.
 *
 * <p>These tests are the reason the masking claim elsewhere is worth anything. A platform that stores a
 * full account number and masks it on the way out can leak it through any new endpoint, log line or
 * {@code toString} nobody thought about. This one has nowhere to put a full account number, and these
 * tests are what say so.
 */
class BankAccountReferenceTest {

  @Test
  @DisplayName("the account number is rendered as a mask over the three digits held")
  void theAccountNumberIsRenderedAsAMask() {
    BankAccountReference reference = BankAccountReference.of("Dominique Fortin", "204");

    assertThat(reference.getMaskedAccountNumber()).isEqualTo("****204");
    assertThat(reference.getAccountLastDigits()).isEqualTo("204");
  }

  @Test
  @DisplayName("the institution and branch numbers are placeholders, because neither is held")
  void theInstitutionAndBranchNumbersAreNeverHeld() {
    BankAccountReference reference = BankAccountReference.of("Dominique Fortin", "204");

    assertThat(reference.getMaskedInstitutionNumber()).matches("\\*+");
    assertThat(reference.getMaskedBranchNumber()).matches("\\*+");
  }

  @ParameterizedTest
  @ValueSource(strings = {"4829471203204", "0204", "20", "2", "12345678901234567890"})
  @DisplayName("anything that is not exactly three digits is refused, including a whole account number")
  void anythingOtherThanThreeDigitsIsRefused(String digits) {
    assertThatThrownBy(() -> BankAccountReference.of("Dominique Fortin", digits))
        .as("truncating a whole account number would hide the caller's mistake instead of surfacing it")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("never a whole account number");
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "20x", "-04", "  4"})
  @DisplayName("three characters that are not digits are refused")
  void threeNonDigitCharactersAreRefused(String digits) {
    assertThatThrownBy(() -> BankAccountReference.of("Dominique Fortin", digits))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be numeric");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("a reference without an account holder is refused")
  void aReferenceWithoutAnAccountHolderIsRefused(String holder) {
    assertThatThrownBy(() -> BankAccountReference.of(holder, "204"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("account holder");
  }

  @Test
  @DisplayName("the string form is the masked number, so a stray log line cannot leak digits")
  void theStringFormIsAlreadyMasked() {
    BankAccountReference reference = BankAccountReference.of("Dominique Fortin", "204");

    assertThat(reference.toString()).isEqualTo("****204");
    assertThat(reference.toString()).doesNotContain("Dominique");
  }

  @Test
  @DisplayName("two references holding the same details are equal")
  void referencesHoldingTheSameDetailsAreEqual() {
    BankAccountReference first = BankAccountReference.of("Dominique Fortin", "204");
    BankAccountReference second = BankAccountReference.of("Dominique Fortin", "204");
    BankAccountReference other = BankAccountReference.of("Dominique Fortin", "871");

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    assertThat(first).isNotEqualTo(other);
  }

  @Test
  @DisplayName("the class has no field that could hold a whole account number")
  void theClassHasNowhereToPutAWholeAccountNumber() {
    long stringFields =
        java.util.Arrays.stream(BankAccountReference.class.getDeclaredFields())
            .filter(field -> !field.isSynthetic())
            .filter(field -> field.getType() == String.class)
            .count();

    assertThat(stringFields)
        .as("the holder's name and three digits, and nothing else — a new String field here needs a new test")
        .isEqualTo(2);
  }
}
