package com.insurancebilling.qa.bdd.support;

import com.insurancebilling.qa.api.model.InvoiceDto;

/**
 * State shared between the steps of one scenario.
 *
 * <p>Injected by Cucumber's picocontainer, which creates a fresh instance per scenario. Static fields
 * would be the obvious alternative and are the classic way a BDD suite becomes order-dependent: state
 * from one scenario leaks into the next, and running scenarios in parallel stops working entirely.
 */
public class ScenarioContext {

  private InvoiceDto invoice;
  private String lastErrorCode;
  private int lastStatusCode;

  public InvoiceDto invoice() {
    if (invoice == null) {
      throw new IllegalStateException(
          "No invoice in scenario context. A Given step must create one before it is used.");
    }
    return invoice;
  }

  public void setInvoice(InvoiceDto invoice) {
    this.invoice = invoice;
  }

  public String lastErrorCode() {
    return lastErrorCode;
  }

  public void setLastErrorCode(String lastErrorCode) {
    this.lastErrorCode = lastErrorCode;
  }

  public int lastStatusCode() {
    return lastStatusCode;
  }

  public void setLastStatusCode(int lastStatusCode) {
    this.lastStatusCode = lastStatusCode;
  }
}
