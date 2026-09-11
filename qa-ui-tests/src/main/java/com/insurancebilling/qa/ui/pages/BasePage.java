package com.insurancebilling.qa.ui.pages;

import com.insurancebilling.qa.ui.config.UiConfig;
import com.insurancebilling.qa.ui.driver.DriverFactory;
import java.util.List;
import org.openqa.selenium.By;
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

  public String currentUrl() {
    return driver.getCurrentUrl();
  }

  public String pageTitle() {
    return textOf("page-title");
  }
}
