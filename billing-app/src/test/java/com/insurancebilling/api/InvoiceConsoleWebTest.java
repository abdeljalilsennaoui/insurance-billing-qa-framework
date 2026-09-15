package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Server-side coverage for the invoice console.
 *
 * <p>These tests assert the controller contract — which view is rendered, what lands in the model,
 * whether a payment redirects — and the presence of the {@code data-testid} hooks the browser suites
 * depend on. They are not a substitute for the Selenium suite: they never exercise a real browser. Their
 * value is speed, and catching a broken template or a missing test hook in seconds rather than in a
 * browser run.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvoiceConsoleWebTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private ApiTestClient api;

  @BeforeEach
  void setUp() {
    api = new ApiTestClient(mockMvc, objectMapper);
  }

  @Test
  @DisplayName("the root path redirects to the invoice console")
  void rootRedirectsToInvoices() throws Exception {
    mockMvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/invoices"));
  }

  @Test
  @DisplayName("the invoice list renders the seeded invoices")
  void invoiceListRendersSeededInvoices() throws Exception {
    mockMvc
        .perform(get("/invoices"))
        .andExpect(status().isOk())
        .andExpect(view().name("invoices/list"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("SEED-INV-001")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-testid=\"invoice-row\"")));
  }

  @Test
  @DisplayName("the invoice list can be filtered by status")
  void invoiceListCanBeFiltered() throws Exception {
    mockMvc
        .perform(get("/invoices").param("status", "PAID"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("selectedStatus", com.insurancebilling.domain.InvoiceStatus.PAID));
  }

  @Test
  @DisplayName("the detail page shows the balance and the test hooks the UI suite uses")
  void detailPageShowsBalanceAndTestHooks() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(get("/invoices/{id}", invoiceId))
        .andExpect(status().isOk())
        .andExpect(view().name("invoices/detail"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.containsString("data-testid=\"invoice-outstanding-balance\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-testid=\"payment-form\"")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("data-testid=\"submit-payment-button\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("450.00")));
  }

  @Test
  @DisplayName("a valid payment redirects back to the invoice rather than re-rendering")
  void validPaymentRedirects() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(post("/invoices/{id}/payments", invoiceId).param("amount", "150.00").param("method", "CARD"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/invoices/" + invoiceId));

    mockMvc
        .perform(get("/invoices/{id}", invoiceId))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("PARTIALLY_PAID")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("300.00")));
  }

  @Test
  @DisplayName("an overpayment re-renders the form with the rejection message visible")
  void overpaymentShowsError() throws Exception {
    long invoiceId = api.createInvoice("100.00");

    mockMvc
        .perform(post("/invoices/{id}/payments", invoiceId).param("amount", "500.00").param("method", "CARD"))
        .andExpect(status().isOk())
        .andExpect(view().name("invoices/detail"))
        .andExpect(model().attributeExists("paymentError"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-testid=\"payment-error\"")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("exceeds the outstanding balance")));
  }

  @Test
  @DisplayName("a non-numeric amount gets its own message, not a generic binding failure")
  void nonNumericAmountShowsSpecificMessage() throws Exception {
    long invoiceId = api.createInvoice("100.00");

    mockMvc
        .perform(post("/invoices/{id}/payments", invoiceId).param("amount", "abc").param("method", "CARD"))
        .andExpect(status().isOk())
        .andExpect(
            model().attribute("paymentError", "Enter a valid amount, for example 125.00."));
  }

  @Test
  @DisplayName("a blank amount asks for an amount")
  void blankAmountShowsPrompt() throws Exception {
    long invoiceId = api.createInvoice("100.00");

    mockMvc
        .perform(post("/invoices/{id}/payments", invoiceId).param("amount", "").param("method", "CARD"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("paymentError", "Enter a payment amount."));
  }

  @Test
  @DisplayName("a zero amount is refused by the billing rule and explained to the reader")
  void zeroAmountShowsRuleMessage() throws Exception {
    long invoiceId = api.createInvoice("100.00");

    // The console explains the refusal in the reader's words, not the domain's. The domain message
    // ("Payment amount must be greater than zero but was 0.00") still reaches the API, where the
    // audience is an engineer reading a response body rather than a policyholder reading a page.
    mockMvc
        .perform(post("/invoices/{id}/payments", invoiceId).param("amount", "0.00").param("method", "CARD"))
        .andExpect(status().isOk())
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("Enter an amount greater than zero.")));
  }

  @Test
  @DisplayName("an unknown invoice renders the console not-found page, not a JSON error")
  void unknownInvoiceRendersNotFoundPage() throws Exception {
    mockMvc
        .perform(get("/invoices/999999"))
        .andExpect(status().isNotFound())
        .andExpect(view().name("invoices/not-found"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-testid=\"not-found-message\"")));
  }

  @Test
  @DisplayName("a settled invoice says so on the page")
  void settledInvoiceShowsFullyPaidMessage() throws Exception {
    long invoiceId = api.createInvoice("100.00");
    mockMvc
        .perform(post("/invoices/{id}/payments", invoiceId).param("amount", "100.00").param("method", "CARD"))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(get("/invoices/{id}", invoiceId))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("data-testid=\"fully-paid-message\"")));
  }
}
