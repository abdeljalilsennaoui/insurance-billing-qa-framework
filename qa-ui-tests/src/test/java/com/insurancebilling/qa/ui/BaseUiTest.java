package com.insurancebilling.qa.ui;

import com.insurancebilling.qa.api.client.BillingApiClient;
import com.insurancebilling.qa.api.client.InvoiceApiClient;
import com.insurancebilling.qa.api.data.BillingTestData;
import com.insurancebilling.qa.ui.driver.DriverFactory;
import com.insurancebilling.qa.ui.support.ScreenshotListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;

/**
 * Browser lifecycle for the UI suite.
 *
 * <p>A fresh browser per test method, quit afterwards in an {@code alwaysRun} teardown so a failing
 * assertion cannot leak a Chrome process. Reusing one session across tests would let cookies, scroll
 * position and leftover form state from one test influence the next.
 *
 * <p>Test data comes from the API clients rather than from the browser. Building a three-invoice
 * fixture by driving forms would be slow and would make every test depend on the correctness of the UI
 * it is meant to be testing; if invoice creation broke, a payment test should fail for the payment, not
 * during setup.
 */
@Listeners(ScreenshotListener.class)
public abstract class BaseUiTest {

  protected final BillingTestData testData = new BillingTestData();
  protected final InvoiceApiClient invoices = new InvoiceApiClient();
  protected final BillingApiClient billing = new BillingApiClient();

  @BeforeMethod(alwaysRun = true)
  public void startBrowser() {
    DriverFactory.startDriver();
  }

  @AfterMethod(alwaysRun = true)
  public void quitBrowser() {
    DriverFactory.quitDriver();
  }
}
