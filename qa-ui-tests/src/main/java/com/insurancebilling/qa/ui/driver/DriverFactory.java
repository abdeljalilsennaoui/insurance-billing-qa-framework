package com.insurancebilling.qa.ui.driver;

import com.insurancebilling.qa.ui.config.UiConfig;
import java.time.Duration;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chrome.ChromeDriver;

/**
 * Creates and disposes the browser session for a test.
 *
 * <p>The driver is held in a {@link ThreadLocal}, created per test and quit in teardown. That is what
 * makes parallel execution safe: with a shared static driver, two tests running concurrently would
 * drive the same browser window and interfere in ways that look like random flakiness.
 *
 * <p>No driver binary is downloaded or committed. Selenium Manager, built into Selenium since 4.6,
 * resolves a chromedriver matching the installed Chrome. That removes both a third-party dependency
 * and the recurring breakage of a pinned driver version drifting from the browser.
 *
 * <p>No implicit wait is configured anywhere. Mixing implicit and explicit waits produces
 * unpredictable timeouts, so all waiting goes through the explicit helpers on
 * {@link com.insurancebilling.qa.ui.pages.BasePage}.
 */
public final class DriverFactory {

  private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

  private DriverFactory() {}

  /** Starts a browser for the current thread. */
  public static WebDriver startDriver() {
    ChromeOptions options = new ChromeOptions();

    if (UiConfig.headless()) {
      // The 'new' headless mode behaves like real Chrome; the legacy mode it replaced differed
      // enough that a suite could pass headless and fail headed.
      options.addArguments("--headless=new");
    }
    // Required on CI containers: no sandbox namespace is available, and the default 64MB /dev/shm
    // makes Chrome crash on page loads that are fine locally.
    options.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu");
    options.addArguments("--remote-allow-origins=*");

    WebDriver driver = new ChromeDriver(options);
    driver.manage().window().setSize(new Dimension(UiConfig.windowWidth(), UiConfig.windowHeight()));
    driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
    DRIVER.set(driver);
    return driver;
  }

  /** The browser for the current thread. */
  public static WebDriver getDriver() {
    WebDriver driver = DRIVER.get();
    if (driver == null) {
      throw new IllegalStateException(
          "No WebDriver for this thread. Tests must extend BaseUiTest so a driver is started before use.");
    }
    return driver;
  }

  public static boolean hasDriver() {
    return DRIVER.get() != null;
  }

  /**
   * Quits the browser and clears the thread binding.
   *
   * <p>The {@code remove()} matters as much as the {@code quit()}: TestNG reuses threads across
   * tests, so a stale entry left behind would hand a dead session to the next test on that thread.
   */
  public static void quitDriver() {
    WebDriver driver = DRIVER.get();
    if (driver != null) {
      try {
        driver.quit();
      } finally {
        DRIVER.remove();
      }
    }
  }
}
