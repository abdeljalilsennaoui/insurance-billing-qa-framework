package com.insurancebilling.api;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import com.insurancebilling.config.LocalisationConfiguration;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Renders money and dates the way the reader's language writes them.
 *
 * <p>Thymeleaf's {@code #numbers.formatDecimal(x, 1, 2)} produces {@code 1591.60} for everyone, which is
 * wrong for half of this application's readers: a French-Canadian statement writes
 * {@code 1 591,60 $}. Formatting is a locale decision, so it is made from the request's locale and
 * nowhere else.
 *
 * <p>A bean rather than a Thymeleaf utility so it can be unit tested directly. Templates call it as
 * {@code ${@billingFormats.money(amount, #locale)}}.
 */
@Component("billingFormats")
public class BillingFormats {

  private final java.time.ZoneId businessZone;

  /**
   * Takes the business clock's zone, not the host's.
   *
   * <p>A timestamp rendered in the JVM's default zone would show a policyholder a payment landing on a
   * different day than the one the platform decided it landed on, for part of every day. That is the
   * same fault DEF-012 recorded in the overdue rule, arriving through a different door.
   */
  public BillingFormats(java.time.Clock businessClock) {
    this.businessZone = businessClock.getZone();
  }

  /**
   * Formats an amount as currency for the given locale.
   *
   * <p>Java's French-Canadian currency format groups thousands with a narrow no-break space. A browser
   * renders that identically to an ordinary space, so a test asserting {@code "1 591,60 $"} with the
   * space anyone would type fails on a difference nobody can see. Normalising the separator here makes
   * the rendered text say what it looks like it says.
   */
  public String money(BigDecimal amount, Locale locale) {
    if (amount == null) {
      return "";
    }
    return NumberFormat.getCurrencyInstance(resolve(locale))
        .format(amount)
        .replace(' ', ' ')
        .replace(' ', ' ');
  }

  /**
   * Formats an instant as a date and time in the reader's language and the platform's business zone.
   *
   * <p>A raw {@code Instant} renders as {@code 2026-09-15T03:29:51.812146Z}, which is precise, wide, and
   * of no use to anyone reading a statement.
   */
  public String dateTime(java.time.Instant instant, Locale locale) {
    if (instant == null) {
      return "";
    }
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
        .withLocale(resolve(locale))
        .withZone(businessZone)
        .format(instant);
  }

  /** Formats a date in the reader's language: {@code Sep 14, 2026} against {@code 14 sept. 2026}. */
  public String date(LocalDate date, Locale locale) {
    if (date == null) {
      return "";
    }
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(resolve(locale))
        .format(date);
  }

  /**
   * Falls back to the default locale for anything the console is not published in.
   *
   * <p>Without this a reader whose browser asks for German would be shown German number formatting
   * beside English words, which is worse than either on its own.
   */
  private Locale resolve(Locale locale) {
    if (locale == null) {
      return LocalisationConfiguration.DEFAULT_LOCALE;
    }
    return LocalisationConfiguration.SUPPORTED_LOCALES.stream()
        .filter(supported -> supported.getLanguage().equals(locale.getLanguage()))
        .findFirst()
        .orElse(LocalisationConfiguration.DEFAULT_LOCALE);
  }
}
