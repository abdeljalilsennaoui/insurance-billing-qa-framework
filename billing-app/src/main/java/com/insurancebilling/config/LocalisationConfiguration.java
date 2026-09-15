package com.insurancebilling.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

/**
 * Serves the console in English or French.
 *
 * <p>A Canadian insurer's billing console is bilingual, so this application's is too. The point is not
 * the translation itself — it is that a second locale turns a whole class of defect into something a
 * test can catch: a hard-coded string nobody externalised, a key present in one bundle and missing from
 * the other, an amount formatted for the wrong reader.
 *
 * <p>The locale is chosen in this order: an explicit {@code ?lang=} on the request, then the cookie a
 * previous {@code ?lang=} set, then the browser's {@code Accept-Language}, then English. A cookie rather
 * than a session so the choice survives a restart of the application, which matters when a suite
 * restarts it between runs.
 */
@Configuration
public class LocalisationConfiguration implements WebMvcConfigurer {

  /** The locales the console is published in. */
  public static final List<Locale> SUPPORTED_LOCALES =
      List.of(Locale.of("en", "CA"), Locale.of("fr", "CA"));

  public static final Locale DEFAULT_LOCALE = Locale.of("en", "CA");

  /** The request parameter that switches language. */
  public static final String LANGUAGE_PARAMETER = "lang";

  @Bean
  public LocaleResolver localeResolver() {
    SupportedLocalesOnly resolver = new SupportedLocalesOnly();
    resolver.setDefaultLocale(DEFAULT_LOCALE);
    return resolver;
  }

  /**
   * A cookie resolver that only ever answers with a locale the console is published in.
   *
   * <p>{@code CookieLocaleResolver} has no supported-locales list of its own, and
   * {@code LocaleChangeInterceptor}'s {@code ignoreInvalidLocale} only ignores strings that are not
   * locales at all — {@code ?lang=de} is a perfectly valid locale this console has no words for.
   * Without the clamp the page would declare {@code lang="de"} and then render English, which is worse
   * than either: a screen reader would announce English text in a German voice.
   */
  static class SupportedLocalesOnly extends CookieLocaleResolver {

    SupportedLocalesOnly() {
      super("billing-locale");
    }

    @Override
    public Locale resolveLocale(jakarta.servlet.http.HttpServletRequest request) {
      return clamp(super.resolveLocale(request));
    }

    /** Matches on language alone, so {@code fr} and {@code fr-FR} both reach the French bundle. */
    private static Locale clamp(Locale requested) {
      if (requested == null) {
        return DEFAULT_LOCALE;
      }
      return SUPPORTED_LOCALES.stream()
          .filter(supported -> supported.getLanguage().equals(requested.getLanguage()))
          .findFirst()
          .orElse(DEFAULT_LOCALE);
    }
  }

  @Bean
  public LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName(LANGUAGE_PARAMETER);
    // An unrecognised language is ignored rather than raising: a mistyped link should show the console
    // in the previous language, not an error page.
    interceptor.setIgnoreInvalidLocale(true);
    return interceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(localeChangeInterceptor());
  }
}
