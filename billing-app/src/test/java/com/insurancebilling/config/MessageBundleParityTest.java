package com.insurancebilling.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the two message bundles to each other.
 *
 * <p>A key present in English and missing from French renders as {@code ??key??} to half the readers,
 * and is invisible to anyone testing in the other language — which, on a project where most people
 * develop in one language, is most of the time. The failure is silent, cosmetic-looking and ships.
 *
 * <p>This is the cheapest test in the repository and one of the most valuable: it reads two files and
 * needs no Spring context, no database and no browser, and it catches the defect at the moment the key
 * is added rather than when a French-speaking reader finds it.
 */
class MessageBundleParityTest {

  private static Properties english;
  private static Properties french;

  @BeforeAll
  static void loadBundles() throws IOException {
    english = load("/messages.properties");
    french = load("/messages_fr.properties");
  }

  private static Properties load(String resource) throws IOException {
    Properties properties = new Properties();
    try (InputStream stream = MessageBundleParityTest.class.getResourceAsStream(resource)) {
      assertThat(stream).as("%s is missing from the classpath", resource).isNotNull();
      properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    return properties;
  }

  @Test
  @DisplayName("every English key has a French counterpart")
  void everyEnglishKeyHasAFrenchCounterpart() {
    Set<String> missing = new TreeSet<>(english.stringPropertyNames());
    missing.removeAll(french.stringPropertyNames());

    assertThat(missing)
        .as("these keys would render as ??key?? for a French reader")
        .isEmpty();
  }

  @Test
  @DisplayName("every French key has an English counterpart")
  void everyFrenchKeyHasAnEnglishCounterpart() {
    Set<String> orphaned = new TreeSet<>(french.stringPropertyNames());
    orphaned.removeAll(english.stringPropertyNames());

    assertThat(orphaned)
        .as("a French key with no English original is either a typo or dead weight")
        .isEmpty();
  }

  @Test
  @DisplayName("no message in either bundle is blank")
  void noMessageIsBlank() {
    assertThat(blankKeysIn(english)).as("blank English messages").isEmpty();
    assertThat(blankKeysIn(french)).as("blank French messages").isEmpty();
  }

  @Test
  @DisplayName("the bundles are read as UTF-8, so accented French survives")
  void theFrenchBundleIsReadAsUtf8() {
    assertThat(french.getProperty("invoices.overdue")).isEqualTo("en retard");
    assertThat(french.getProperty("payments.column.reference"))
        .as("a bundle read as ISO-8859-1 would render this as RÃ©fÃ©rence")
        .isEqualTo("Référence");
    assertThat(french.getProperty("invoice.issued")).isEqualTo("Émise le");
  }

  @Test
  @DisplayName("every invoice status has a label in both languages")
  void everyInvoiceStatusHasALabelInBothLanguages() {
    for (com.insurancebilling.domain.InvoiceStatus status :
        com.insurancebilling.domain.InvoiceStatus.values()) {
      String key = "status." + status.name();
      assertThat(english.getProperty(key)).as("English label for %s", status).isNotBlank();
      assertThat(french.getProperty(key)).as("French label for %s", status).isNotBlank();
    }
  }

  @Test
  @DisplayName("every payment method has a label in both languages")
  void everyPaymentMethodHasALabelInBothLanguages() {
    for (com.insurancebilling.domain.PaymentMethod method :
        com.insurancebilling.domain.PaymentMethod.values()) {
      String key = "method." + method.name();
      assertThat(english.getProperty(key)).as("English label for %s", method).isNotBlank();
      assertThat(french.getProperty(key)).as("French label for %s", method).isNotBlank();
    }
  }

  @Test
  @DisplayName("a translation that is identical to the English is either deliberate or forgotten")
  void translationsThatMatchTheEnglishAreAccountedFor() {
    // Proper nouns and words French and English spell the same way. Anything else appearing here is a
    // key somebody copied across and never translated.
    Set<String> allowed =
        Set.of(
            "app.name",
            "app.tagline",
            "common.total",
            "common.none",
            "nav.language.english",
            "nav.language.french",
            "invoices.column.total",
            "invoice.total");

    Set<String> untranslated =
        english.stringPropertyNames().stream()
            .filter(key -> english.getProperty(key).equals(french.getProperty(key)))
            .filter(key -> !allowed.contains(key))
            .collect(Collectors.toCollection(TreeSet::new));

    assertThat(untranslated)
        .as("these read the same in both bundles; translate them or add them to the allowed set")
        .isEmpty();
  }

  private static Set<String> blankKeysIn(Properties bundle) {
    return bundle.stringPropertyNames().stream()
        .filter(key -> bundle.getProperty(key).isBlank())
        .collect(Collectors.toCollection(TreeSet::new));
  }
}
