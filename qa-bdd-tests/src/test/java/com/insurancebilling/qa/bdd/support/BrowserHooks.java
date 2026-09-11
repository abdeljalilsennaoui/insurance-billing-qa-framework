package com.insurancebilling.qa.bdd.support;

import com.insurancebilling.qa.ui.config.UiConfig;
import com.insurancebilling.qa.ui.driver.DriverFactory;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

/**
 * Browser lifecycle for scenarios tagged {@code @ui}.
 *
 * <p>Tagged hooks matter here: the API scenarios must not pay for starting Chrome. An untagged
 * {@code @Before} would launch a browser for every scenario in the suite, roughly tripling the API
 * feature's runtime for no benefit.
 *
 * <p>On failure the screenshot is both attached to the Cucumber report and written to disk: the report
 * is what a person reads, and the file is what CI uploads as an artifact.
 */
public class BrowserHooks {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  @Before("@ui")
  public void startBrowser() {
    DriverFactory.startDriver();
  }

  @After("@ui")
  public void captureFailureAndQuit(Scenario scenario) {
    try {
      if (scenario.isFailed() && DriverFactory.hasDriver()) {
        byte[] png = ((TakesScreenshot) DriverFactory.getDriver()).getScreenshotAs(OutputType.BYTES);
        scenario.attach(png, "image/png", scenario.getName());
        writeToDisk(scenario, png);
      }
    } catch (RuntimeException screenshotFailure) {
      // Never let a diagnostic replace the real failure with an unrelated one.
      System.out.println("Could not capture a screenshot: " + screenshotFailure.getMessage());
    } finally {
      DriverFactory.quitDriver();
    }
  }

  private void writeToDisk(Scenario scenario, byte[] png) {
    try {
      Path directory = Path.of(UiConfig.screenshotDirectory());
      Files.createDirectories(directory);
      String safeName = scenario.getName().replaceAll("[^A-Za-z0-9.-]", "_");
      Path target =
          directory.resolve(safeName + "-" + LocalDateTime.now().format(TIMESTAMP) + ".png");
      Files.copy(new java.io.ByteArrayInputStream(png), target, StandardCopyOption.REPLACE_EXISTING);
      System.out.println("Screenshot of failed scenario written to " + target.toAbsolutePath());
    } catch (Exception writeFailure) {
      System.out.println("Could not write screenshot to disk: " + writeFailure.getMessage());
    }
  }
}
