package com.insurancebilling.qa.ui.docs;

import com.insurancebilling.qa.api.client.InvoiceApiClient;
import com.insurancebilling.qa.api.config.ApiSpecs;
import com.insurancebilling.qa.api.data.BillingTestData;
import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.ui.config.UiConfig;
import com.insurancebilling.qa.ui.driver.DriverFactory;
import com.insurancebilling.qa.ui.pages.AgentConsolePage;
import com.insurancebilling.qa.ui.pages.InvoiceDetailsPage;
import com.insurancebilling.qa.ui.pages.AccountSummaryPage;
import com.insurancebilling.qa.ui.pages.InvoiceListPage;
import com.insurancebilling.qa.ui.pages.TermsPage;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Captures the screenshots used as evidence in {@code docs/test-report.md}.
 *
 * <p>This is a documentation tool, not a test. It asserts nothing and it gates nothing, so it lives in
 * {@code src/main/java} and is invoked explicitly rather than being picked up by Failsafe. Making it a
 * test class would mean a screenshot the report happens to want could turn a pipeline red, which is
 * exactly the wrong trade: the report is a description of the run, not a condition on it.
 *
 * <p>It reuses the suite's own {@link DriverFactory}, page objects and API test data instead of
 * scripting a browser separately. Two consequences follow, and both are the point. The images show the
 * application in the same viewport, the same browser mode and through the same locators the UI suite
 * drives, so the evidence corresponds to what was actually tested. And if a page object stops
 * compiling or a locator disappears, this tool breaks with the suite rather than quietly producing
 * screenshots of a console the tests no longer describe.
 *
 * <p>Two capture modes, because the two sets of images become available at different points in a run:
 *
 * <ul>
 *   <li>{@code console} — the running application's pages. Needs the application up.
 *   <li>{@code reports} — the HTML reports the suites and tools produce on disk. Needs those runs to
 *       have finished, and needs no application at all.
 * </ul>
 *
 * <pre>
 *   mvn -q -pl qa-ui-tests exec:java -Dexec.args=console
 *   mvn -q -pl qa-ui-tests exec:java -Dexec.args=reports
 * </pre>
 *
 * <p>{@code scripts/capture-screenshots.sh} runs both in the right order.
 */
public final class DocumentationScreenshots {

  /**
   * Where the images are written.
   *
   * <p>Defaulted by the Maven execution to the repository's {@code docs/screenshots}, because a
   * relative path here would resolve against the module directory and quietly produce
   * {@code qa-ui-tests/docs/screenshots} instead.
   */
  private static final Path OUTPUT_DIRECTORY =
      Path.of(System.getProperty("docs.screenshot.dir", "docs/screenshots"));

  /**
   * The repository root, used to locate the HTML reports in {@code reports} mode.
   *
   * <p>Also supplied by the Maven execution. {@code exec:java} runs inside the Maven JVM, so the
   * working directory is wherever Maven was invoked from rather than the module directory — a path
   * that is correct when the script runs from the root and silently wrong when someone runs the goal
   * from inside {@code qa-ui-tests}.
   */
  private static final Path REPO_ROOT = Path.of(System.getProperty("docs.repo.root", "."));

  private final WebDriver driver;
  private final BillingTestData testData = new BillingTestData();
  private final InvoiceApiClient invoices = new InvoiceApiClient();
  private final List<String> captured = new ArrayList<>();

  private DocumentationScreenshots(WebDriver driver) {
    this.driver = driver;
  }

  public static void main(String[] arguments) {
    String mode = arguments.length > 0 ? arguments[0] : "console";

    WebDriver driver = DriverFactory.startDriver();
    DocumentationScreenshots screenshots = new DocumentationScreenshots(driver);
    try {
      switch (mode) {
        case "console" -> screenshots.captureConsole();
        case "reports" -> screenshots.captureReports();
        default -> throw new IllegalArgumentException(
            "Unknown mode '" + mode + "'. Expected 'console' or 'reports'.");
      }
    } finally {
      DriverFactory.quitDriver();
    }

    System.out.println();
    System.out.println(screenshots.captured.size() + " screenshot(s) written to "
        + OUTPUT_DIRECTORY.toAbsolutePath());
    screenshots.captured.forEach(name -> System.out.println("  " + name));
  }

  /** The application's own pages, driven through the page objects. */
  private void captureConsole() {
    restoreSeededBaseline();

    InvoiceListPage list = new InvoiceListPage().open();
    capture("01-invoice-console-all", "Invoice console, seeded baseline: six invoices, every status");

    list.filterByStatus("OVERDUE");
    capture("02-invoice-console-filtered-overdue", "Status filter applied: only overdue invoices");

    // A seeded invoice, read only: two instalments of a four-instalment premium already received.
    openSeededInvoice("SEED-INV-002");
    capture("03-invoice-detail-partially-paid", "Invoice detail: summary, payment history, payment form");

    // From here on the tool creates its own data, for the same reason the suites do: a screenshot that
    // mutates a seeded record would change what the next run of the list screenshot shows.
    InvoiceDto payable = testData.unpaidInvoice("480.00");
    InvoiceDetailsPage detail = new InvoiceDetailsPage().openById(payable.id());

    detail.payWith("120.00", "CARD", "DOC-CAPTURE");
    capture("04-payment-accepted", "Payment accepted: confirmation banner, balance and history updated");

    // 10000.00 against a 360.00 balance. Refused by a billing rule rather than by input validation,
    // which is the distinction the API answers as 422 EXCEEDS_OUTSTANDING_BALANCE.
    detail.payWith("10000.00", "CARD", "DOC-CAPTURE");
    capture("05-payment-rejected-exceeds-balance", "Payment refused: amount exceeds the outstanding balance");

    InvoiceDto cancelled = testData.cancelledInvoice("600.00");
    new InvoiceDetailsPage().openById(cancelled.id()).payWith("50.00", "CARD", "DOC-CAPTURE");
    capture("06-payment-rejected-cancelled-invoice", "Payment refused: the invoice is cancelled");

    InvoiceDto settled = testData.settledInvoice("250.00");
    new InvoiceDetailsPage().openById(settled.id());
    capture("07-invoice-settled", "Settled invoice: status PAID, nothing left to pay");

    driver.get(UiConfig.baseUrl() + "/invoices/999999");
    capture("08-invoice-not-found", "Unknown invoice: the console's own 404 page, not a JSON error body");

    captureBillingAccounts();
  }

  /**
   * The billing account screens, against the seeded accounts.
   *
   * <p>Read-only and taken from seeded data on purpose: these are the screens whose whole point is a
   * particular set of figures - an evenly divisible schedule, an uneven one, and a returned payment -
   * and a screenshot of numbers a test invented would show none of that.
   *
   * <p>The baseline is restored first because the captures above create and pay invoices.
   */
  private void captureBillingAccounts() {
    restoreSeededBaseline();

    // The uneven account: 1000.00 over twelve does not divide, and a payment on it was returned.
    new AccountSummaryPage().open("ACCT-100002");
    capture(
        "09-account-summary",
        "Account summary: balance, next payment, and the two failed-payment tallies");

    TermsPage terms = new TermsPage().open("ACCT-100002", "schedule");
    capture(
        "10-term-schedule",
        "Payment schedule: the down payment carries the rounding remainder, and one installment was reversed");

    terms.openTab("transactions");
    capture(
        "11-term-transactions",
        "Transaction history: new business, a payment, its reversal and the fee, with a running balance");

    new AccountSummaryPage().open("ACCT-100001").switchLanguageTo("fr");
    capture(
        "12-account-summary-french",
        "The same screen in French: the figures are identical, the words and the number format are not");

    captureAgentConsole();
  }

  /**
   * The agent's side of the same data.
   *
   * <p>Captured from the same seeded accounts as the policyholder screens above, and that is the point
   * of including it: the ledger in 14 is the ledger in 11, rendered by the same fragment for a
   * different reader. A screenshot pair is the cheapest way to show that.
   */
  private void captureAgentConsole() {
    AgentConsolePage agent = new AgentConsolePage().open();
    capture(
        "13-agent-console-portfolio",
        "Agent console: every term on the books, ordered by policy number and totalled");

    agent.open("SEED-TERM-002", "transactions");
    capture(
        "14-agent-console-ledger",
        "The same ledger the policyholder sees in 11, read from the agent's console");
  }

  /** The HTML reports produced by the suites and by the coverage and performance tooling. */
  private void captureReports() {
    captureLocalReport(
        "billing-app/target/site/jacoco-full/index.html",
        "15-coverage-jacoco",
        "JaCoCo full-stack coverage report");
    captureLocalReport(
        "qa-bdd-tests/target/cucumber-reports/api.html",
        "16-cucumber-api-scenarios",
        "Cucumber report for the API scenarios");
    captureLocalReport(
        "qa-bdd-tests/target/cucumber-reports/ui.html",
        "17-cucumber-ui-scenarios",
        "Cucumber report for the browser scenarios");
    captureLatestJmeterDashboard();
  }

  /**
   * Restores the seeded baseline before capturing the list.
   *
   * <p>Without this the list screenshot shows whatever the last suite left behind — by the end of a
   * full run that is a hundred-odd generated invoices, which documents nothing. The reset endpoint is
   * the same one the {@code test-support} group uses and is only present when the application was
   * started with {@code qa.test-support.enabled=true}.
   *
   * <p>It deletes every row, so this tool must never run beside a suite. {@code scripts/coverage.sh}
   * and {@code scripts/capture-screenshots.sh} both invoke it after the suites have finished.
   */
  private void restoreSeededBaseline() {
    Response response =
        RestAssured.given().spec(ApiSpecs.request()).when().post("/api/test-support/reset");

    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Could not reset to the seeded baseline: POST /api/test-support/reset returned "
              + response.statusCode()
              + ". Start the application with --qa.test-support.enabled=true "
              + "(scripts/start-app.sh already does).");
    }
    System.out.println("Database reset to the seeded baseline: " + response.asString());
  }

  private void openSeededInvoice(String invoiceNumber) {
    InvoiceDto invoice =
        invoices.list().stream()
            .filter(candidate -> invoiceNumber.equals(candidate.invoiceNumber()))
            .findFirst()
            .orElseThrow(
                () -> new IllegalStateException("No seeded invoice " + invoiceNumber + " to capture"));
    new InvoiceDetailsPage().openById(invoice.id());
  }

  /**
   * Renders a report already on disk and captures it.
   *
   * <p>A missing report is reported and skipped rather than thrown, because the two capture modes are
   * run at different times and for different reasons: someone regenerating the console screenshots
   * after a UI change should not have to run JMeter first.
   */
  private void captureLocalReport(String relativePath, String name, String description) {
    Path report = REPO_ROOT.resolve(relativePath).toAbsolutePath().normalize();
    if (!Files.isRegularFile(report)) {
      System.out.println("Skipping " + name + ": no report at " + report);
      return;
    }
    driver.get(report.toUri().toString());
    capture(name, description);
  }

  /** The most recent JMeter dashboard, whose directory name carries the run's timestamp. */
  private void captureLatestJmeterDashboard() {
    Path results = REPO_ROOT.resolve("perf/results");
    if (!Files.isDirectory(results)) {
      System.out.println("Skipping the JMeter dashboard: no perf/results directory");
      return;
    }
    try (var entries = Files.list(results)) {
      entries
          .filter(path -> path.getFileName().toString().startsWith("report-"))
          .filter(path -> Files.isRegularFile(path.resolve("index.html")))
          .max(Comparator.comparing(path -> path.getFileName().toString()))
          .ifPresentOrElse(
              latest ->
                  captureLocalReport(
                      latest.resolve("index.html").toString(),
                      "18-jmeter-dashboard",
                      "JMeter dashboard for the invoice API load test"),
              () -> System.out.println("Skipping the JMeter dashboard: no report-* directory"));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  /**
   * Writes the current page to a PNG.
   *
   * <p>The window is grown to the document height first. ChromeDriver's screenshot is a picture of the
   * viewport, so a page taller than the window would be cut off exactly where the interesting part
   * usually is — the payment form sits below the fold on the detail page at 1440x900. The window is
   * restored afterwards so every capture starts from the same layout.
   *
   * <p>File names are fixed rather than timestamped, the opposite of {@code ScreenshotListener}'s
   * choice for failure evidence. These images are referenced by the report and committed, so a re-run
   * should replace them and produce a reviewable diff; a timestamp would accumulate near-duplicates
   * and leave the report pointing at the oldest one.
   */
  private void capture(String name, String description) {
    growWindowToContent();
    try {
      Files.createDirectories(OUTPUT_DIRECTORY);
      Path target = OUTPUT_DIRECTORY.resolve(name + ".png");
      Path temporary = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      captured.add(target.getFileName() + "  —  " + description);
      System.out.println("Captured " + target);
    } catch (IOException writeFailed) {
      throw new UncheckedIOException("Could not write the screenshot for " + name, writeFailed);
    } finally {
      resetWindow();
    }
  }

  private void growWindowToContent() {
    JavascriptExecutor javascript = (JavascriptExecutor) driver;
    long content =
        ((Number)
                javascript.executeScript(
                    "return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"))
            .longValue();
    long viewport = ((Number) javascript.executeScript("return window.innerHeight;")).longValue();

    if (content <= viewport) {
      return;
    }
    Dimension window = driver.manage().window().getSize();
    // Whatever the window spends on browser chrome, so the new viewport is the content height rather
    // than the content height minus the toolbar.
    long chrome = window.getHeight() - viewport;
    driver.manage().window().setSize(new Dimension(window.getWidth(), (int) (content + chrome)));
  }

  private void resetWindow() {
    driver
        .manage()
        .window()
        .setSize(new Dimension(UiConfig.windowWidth(), UiConfig.windowHeight()));
  }
}
