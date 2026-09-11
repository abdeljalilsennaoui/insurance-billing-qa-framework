package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration coverage for the invoice and payment endpoints.
 *
 * <p>The 400 against 422 split is asserted explicitly throughout: a malformed request must not be
 * reported the same way as a payment the platform refused, because that distinction is the only thing
 * proving the billing rule ran at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvoicePaymentApiIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private ApiTestClient api;

  @BeforeEach
  void setUp() {
    api = new ApiTestClient(mockMvc, objectMapper);
  }

  private static String payment(String amount) {
    return """
        {"amount":%s,"method":"CARD","reference":"IT-REF"}
        """
        .formatted(amount);
  }

  @Test
  @DisplayName("creating an invoice returns 201 with the full amount outstanding")
  void createInvoiceReturns201() throws Exception {
    long policyId = api.createPolicy(api.createCustomer());

    mockMvc
        .perform(
            post("/api/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"policyId":%d,"totalAmount":450.00,"issueDate":"%s","dueDate":"%s"}
                    """
                        .formatted(policyId, LocalDate.now(), LocalDate.now().plusDays(30))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.invoiceNumber").value(org.hamcrest.Matchers.startsWith("INV-")))
        .andExpect(jsonPath("$.status").value("UNPAID"))
        .andExpect(jsonPath("$.amountPaid").value(0.00))
        .andExpect(jsonPath("$.outstandingBalance").value(450.00))
        .andExpect(jsonPath("$.overdue").value(false));
  }

  @Test
  @DisplayName("a partial payment returns 201 and leaves the invoice partially paid")
  void partialPaymentIsAccepted() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("150.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(150.00))
        .andExpect(jsonPath("$.method").value("CARD"));

    mockMvc
        .perform(get("/api/invoices/{id}", invoiceId))
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.amountPaid").value(150.00))
        .andExpect(jsonPath("$.outstandingBalance").value(300.00));
  }

  @Test
  @DisplayName("paying the outstanding balance settles the invoice")
  void fullPaymentSettlesInvoice() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("450.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/invoices/{id}", invoiceId))
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.outstandingBalance").value(0.00));
  }

  @Test
  @DisplayName("an overpayment is refused with 422 and the overpayment reason")
  void overpaymentIsRefusedWith422() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("500.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("EXCEEDS_OUTSTANDING_BALANCE"));

    mockMvc
        .perform(get("/api/invoices/{id}", invoiceId))
        .andExpect(jsonPath("$.outstandingBalance").value(450.00))
        .andExpect(jsonPath("$.payments.length()").value(0));
  }

  @Test
  @DisplayName("a zero amount is refused as a billing rule, not as malformed input")
  void zeroAmountIsRefusedWith422() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("0.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("AMOUNT_NOT_POSITIVE"));
  }

  @Test
  @DisplayName("an over-precise amount is refused with the scale reason")
  void overPreciseAmountIsRefusedWith422() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("10.001")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("AMOUNT_SCALE_INVALID"));
  }

  @Test
  @DisplayName("a missing amount is a 400, not a 422")
  void missingAmountIsA400() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"method":"CARD"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.amount").value("amount is required"));
  }

  @Test
  @DisplayName("an unparseable body is a 400 with the malformed-request code")
  void unparseableBodyIsA400() throws Exception {
    long invoiceId = api.createInvoice("450.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": }"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  @DisplayName("paying an unknown invoice returns 404")
  void payingUnknownInvoiceReturns404() throws Exception {
    mockMvc
        .perform(
            post("/api/invoices/999999/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("10.00")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("paying a settled invoice is refused with the already-paid reason")
  void payingSettledInvoiceIsRefused() throws Exception {
    long invoiceId = api.createInvoice("100.00");
    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("100.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("1.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVOICE_ALREADY_PAID"));
  }

  @Test
  @DisplayName("paying a cancelled invoice is refused with the cancelled reason")
  void payingCancelledInvoiceIsRefused() throws Exception {
    long invoiceId = api.createInvoice("100.00");
    mockMvc.perform(post("/api/invoices/{id}/cancellation", invoiceId)).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("10.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INVOICE_CANCELLED"));
  }

  @Test
  @DisplayName("paying against a lapsed policy is refused with the policy reason")
  void payingOnLapsedPolicyIsRefused() throws Exception {
    long policyId = api.createPolicy(api.createCustomer());
    long invoiceId = api.createInvoice(policyId, "100.00", LocalDate.now().plusDays(30));

    mockMvc
        .perform(
            patch("/api/policies/{id}/status", policyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"status":"LAPSED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LAPSED"));

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("10.00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("POLICY_NOT_ACTIVE"));
  }

  @Test
  @DisplayName("payments are listed against the invoice in the order received")
  void paymentsAreListedInOrder() throws Exception {
    long invoiceId = api.createInvoice("300.00");

    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount":100.00,"method":"CARD","reference":"FIRST"}
                    """))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount":50.00,"method":"BANK_TRANSFER","reference":"SECOND"}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/invoices/{id}/payments", invoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].reference").value("FIRST"))
        .andExpect(jsonPath("$[1].reference").value("SECOND"));
  }

  @Test
  @DisplayName("an invoice past its due date with nothing paid is reported overdue")
  void pastDueInvoiceIsReportedOverdue() throws Exception {
    long policyId = api.createPolicy(api.createCustomer());
    long invoiceId = api.createInvoice(policyId, "200.00", LocalDate.now().minusDays(10));

    mockMvc
        .perform(get("/api/invoices/{id}", invoiceId))
        .andExpect(jsonPath("$.overdue").value(true))
        .andExpect(jsonPath("$.status").value("OVERDUE"));
  }

  @Test
  @DisplayName("the invoice list can be filtered by status")
  void invoiceListCanBeFilteredByStatus() throws Exception {
    long invoiceId = api.createInvoice("500.00");
    mockMvc
        .perform(
            post("/api/invoices/{id}/payments", invoiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payment("250.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/invoices").param("status", "PARTIALLY_PAID"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + invoiceId + ")]").exists())
        .andExpect(jsonPath("$[?(@.status != 'PARTIALLY_PAID')]").doesNotExist());
  }

  @Test
  @DisplayName("an unknown status filter value returns 400")
  void unknownStatusFilterReturns400() throws Exception {
    mockMvc
        .perform(get("/api/invoices").param("status", "NOT_A_STATUS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }
}
