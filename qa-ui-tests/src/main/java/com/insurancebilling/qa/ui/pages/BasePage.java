package com.insurancebilling.qa.ui.pages;

import com.insurancebilling.qa.ui.config.UiConfig;
import com.insurancebilling.qa.ui.driver.DriverFactory;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Shared interaction and waiting behaviour for every page object.
 *
 * <p>All waiting is explicit and condition-based. {@code Thread.sleep} appears nowhere in this module:
 * a sleep either waits longer than necessary on every run or not long enough on a slow one, and tuning
 * it trades one failure mode for the other. Waiting for the condition you actually depend on is both
 * faster and deterministic.
 *
 * <p>Elements are addressed by {@code data-testid}, never by CSS class or visible text, so restyling
 * or rewording the console cannot break a locator.
 *
 * <p>Page methods return values or the next page object. They contain no assertions: assertions belong
 * to tests, so the same page object can serve a test expecting success and a test expecting failure.
 */
public abstract class BasePage {

  protected final WebDriver driver;
  protected final WebDriverWait wait;

  protected BasePage() {
    this.driver = DriverFactory.getDriver();
    this.wait = new WebDriverWait(driver, UiConfig.explicitWait());
  }

  /** Locator for a stable test hook. */
  protected static By testId(String id) {
    return By.cssSelector("[data-testid='" + id + "']");
  }

  protected void navigateTo(String path) {
    driver.get(UiConfig.baseUrl() + path);
  }

  protected WebElement visible(String id) {
    return wait.until(ExpectedConditions.visibilityOfElementLocated(testId(id)));
  }

  protected List<WebElement> allVisible(String id) {
    wait.until(ExpectedConditions.presenceOfElementLocated(testId(id)));
    return driver.findElements(testId(id));
  }

  /**
   * Reads a semantic attribute rather than the words on screen.
   *
   * <p>The console is published in two languages, so its visible text is display copy and changes with
   * the reader. State that automation asserts on - a status, an amount - rides on a data attribute that
   * is the same in both. A suite reading the words would pass in English and fail in French while the
   * application behaved identically.
   */
  protected String attributeOf(String id, String attribute) {
    return visible(id).getDomAttribute(attribute);
  }

  protected String textOf(String id) {
    return visible(id).getText().trim();
  }

  protected void click(String id) {
    wait.until(ExpectedConditions.elementToBeClickable(testId(id))).click();
  }

  /**
   * Replaces the contents of a field.
   *
   * <p>Clears before typing because the console re-renders the form with the rejected value still in
   * it after a failed payment; appending to that would submit something neither the test nor the user
   * intended.
   */
  protected void type(String id, String value) {
    WebElement field = visible(id);
    field.clear();
    field.sendKeys(value);
  }

  protected void selectOption(String id, String value) {
    new Select(visible(id)).selectByValue(value);
  }

  /**
   * Whether an element is present, without waiting for it.
   *
   * <p>Used for asserting absence. Routing that through the waiting helpers would pay the full
   * timeout on every negative check.
   */
  protected boolean isPresent(String id) {
    try {
      return !driver.findElements(testId(id)).isEmpty();
    } catch (NoSuchElementException absent) {
      return false;
    }
  }

  /**
   * Name of the marker set on {@code window} to detect a page replacement.
   *
   * <p>Prefixed to make a collision with application script impossible, though the console runs no
   * JavaScript of its own.
   */
  private static final String NAVIGATION_MARKER = "__ibqfNavigationMarker";

  /**
   * Marks the current document immediately before an action that navigates.
   *
   * <p>Pair with {@link #waitForNewDocument()}. A full page load creates a fresh {@code window}, so the
   * marker's disappearance is proof the browser replaced the document rather than merely re-rendered part
   * of it.
   */
  protected void markCurrentDocument() {
    ((JavascriptExecutor) driver).executeScript("window." + NAVIGATION_MARKER + " = true;");
  }

  /**
   * Waits until the marked document has been replaced and the new one has finished loading.
   *
   * <p>This replaced an earlier implementation built on {@code ExpectedConditions.stalenessOf}, which was
   * correct in principle and unreliable in practice. {@code stalenessOf} decides an element is stale by
   * touching it and catching {@code StaleElementReferenceException} — but when a document has been
   * discarded mid-navigation, ChromeDriver may instead raise a CDP-level
   * {@code WebDriverException: Node with given id does not belong to the document}, which
   * {@code stalenessOf} does not catch and which therefore escapes as a test error. It passed consistently
   * on a developer machine and failed on a CI runner: a timing and Chrome-version dependent race.
   *
   * <p>Asking the document about itself avoids the problem entirely, because it never touches a reference
   * that may already be dead. Checking {@code readyState} as well means the caller does not then race the
   * new page's own rendering.
   */
  protected void waitForNewDocument() {
    wait.until(
        driver ->
            Boolean.TRUE.equals(
                ((JavascriptExecutor) driver)
                    .executeScript(
                        "return window."
                            + NAVIGATION_MARKER
                            + " === undefined && document.readyState === 'complete';")));
  }

  public String currentUrl() {
    return driver.getCurrentUrl();
  }

  public String pageTitle() {
    return textOf("page-title");
  }
}
