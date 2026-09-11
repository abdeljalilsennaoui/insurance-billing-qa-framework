package com.insurancebilling.qa.ui.config;

import java.time.Duration;

/**
 * UI suite configuration, resolved from system properties with working defaults.
 *
 * <p>Headless is the default rather than an opt-in. A suite that only passes with a visible browser
 * cannot run in CI, and discovering that at pipeline time rather than at development time is the
 * expensive order to find out. {@code -Dui.headless=false} is there for watching a failure locally.
 */
public final class UiConfig {

  private UiConfig() {}

  public static String baseUrl() {
    return System.getProperty("app.base.url", "http://localhost:8080").replaceAll("/+$", "");
  }

  public static boolean headless() {
    return Boolean.parseBoolean(System.getProperty("ui.headless", "true"));
  }

  /**
   * How long an explicit wait will wait for a condition.
   *
   * <p>Generous enough to absorb a slow CI runner, because the cost of a high ceiling is paid only
   * when something is actually wrong: a condition that is already true returns immediately.
   */
  public static Duration explicitWait() {
    return Duration.ofSeconds(Long.getLong("ui.wait.seconds", 15L));
  }

  public static String screenshotDirectory() {
    return System.getProperty("ui.screenshot.dir", "target/screenshots");
  }

  /**
   * Fixed viewport for every session.
   *
   * <p>Pinned so that layout-dependent behaviour is identical locally and on a runner. A default
   * window size that differs between environments is a classic source of a suite that passes on one
   * machine and fails on another because an element sits outside the viewport.
   */
  public static int windowWidth() {
    return Integer.getInteger("ui.window.width", 1440);
  }

  public static int windowHeight() {
    return Integer.getInteger("ui.window.height", 900);
  }
}
