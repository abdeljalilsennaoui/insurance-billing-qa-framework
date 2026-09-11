package com.insurancebilling.qa.api.config;

/**
 * Where the suite points and how long it waits.
 *
 * <p>Resolved from system properties with working defaults, so the identical suite runs against a
 * locally started application, against the instance CI starts, or against a deployed environment with
 * nothing more than {@code -Dapp.base.url=...}. No environment detail is ever compiled into a test.
 */
public final class TestEnvironment {

  private static final String DEFAULT_BASE_URL = "http://localhost:8080";

  private TestEnvironment() {}

  /** Base URL of the application under test. */
  public static String baseUrl() {
    return System.getProperty("app.base.url", DEFAULT_BASE_URL).replaceAll("/+$", "");
  }

  /** True when the QA test-support endpoints are expected to be enabled on the target. */
  public static boolean testSupportEnabled() {
    return Boolean.parseBoolean(System.getProperty("qa.test-support.enabled", "true"));
  }
}
