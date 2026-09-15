package com.insurancebilling.qa.bdd.support;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import com.insurancebilling.qa.api.model.BillingTransactionDto;
import com.insurancebilling.qa.api.model.InvoiceDto;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import java.util.List;

/**
 * State shared between the steps of one scenario.
 *
 * <p>Injected by Cucumber's picocontainer, which creates a fresh instance per scenario. Static fields
 * would be the obvious alternative and are the classic way a BDD suite becomes order-dependent: state
 * from one scenario leaks into the next, and running scenarios in parallel stops working entirely.
 */
public class ScenarioContext {

  private InvoiceDto invoice;
  private BillingAccountDto account;
  private PolicyTermDto term;
  private BillingTransactionDto lastPayment;
  private List<String> amountsOnFirstScreen;
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

  public BillingAccountDto account() {
    return require(account, "billing account");
  }

  public void setAccount(BillingAccountDto account) {
    this.account = account;
  }

  public PolicyTermDto term() {
    return require(term, "policy term");
  }

  public void setTerm(PolicyTermDto term) {
    this.term = term;
  }

  /** The payment a return step will reverse. */
  public BillingTransactionDto lastPayment() {
    return require(lastPayment, "payment");
  }

  public void setLastPayment(BillingTransactionDto lastPayment) {
    this.lastPayment = lastPayment;
  }

  /**
   * Figures read off whichever console was opened first.
   *
   * <p>Held so a later step can open the other console and compare the two, which is the only way a
   * scenario can assert that both personas are shown the same thing.
   */
  public List<String> amountsOnFirstScreen() {
    return require(amountsOnFirstScreen, "figures from the first screen");
  }

  public void setAmountsOnFirstScreen(List<String> amounts) {
    this.amountsOnFirstScreen = amounts;
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

  private static <T> T require(T value, String what) {
    if (value == null) {
      throw new IllegalStateException(
          "No " + what + " in scenario context. A Given step must create one before it is used.");
    }
    return value;
  }
}
