package com.insurancebilling.qa.ui.support;

import com.insurancebilling.qa.ui.config.UiConfig;
import com.insurancebilling.qa.ui.driver.DriverFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Captures a screenshot when a UI test fails.
 *
 * <p>The point is being able to diagnose a CI failure without reproducing it locally. A browser
 * assertion message on its own rarely explains why an element was missing; the rendered page usually
 * does, and by the time the pipeline has finished the browser is long gone.
 *
 * <p>Files are named after the test and a timestamp so a re-run does not overwrite the evidence from
 * the previous failure. They are written under the build directory, which is git-ignored and uploaded
 * as a CI artifact.
 *
 * <p>Failures inside this listener are swallowed deliberately: a screenshot that cannot be taken must
 * not replace the real test failure with an I/O error, which would hide the actual problem.
 */
public class ScreenshotListener implements ITestListener {

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

  @Override
  public void onTestFailure(ITestResult result) {
    if (!DriverFactory.hasDriver()) {
      return;
    }
    String name =
        result.getTestClass().getRealClass().getSimpleName() + "." + result.getMethod().getMethodName();
    try {
      Path directory = Path.of(UiConfig.screenshotDirectory());
      Files.createDirectories(directory);
      Path target = directory.resolve(name + "-" + LocalDateTime.now().format(TIMESTAMP) + ".png");

      Path temporary =
          ((TakesScreenshot) DriverFactory.getDriver()).getScreenshotAs(OutputType.FILE).toPath();
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);

      System.out.println("Screenshot of failure written to " + target.toAbsolutePath());
    } catch (IOException | RuntimeException screenshotFailure) {
      System.out.println(
          "Could not capture a screenshot for " + name + ": " + screenshotFailure.getMessage());
    }
  }
}
