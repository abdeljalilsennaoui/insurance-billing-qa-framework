package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The join between the two halves of the domain.
 *
 * <p>An installment is what raises a billing document; the document is what the invoice console lists;
 * paying it is what moves the ledger. If that chain ever comes apart, an invoice will read settled while
 * the term it belongs to still shows the balance — which is the failure this class exists to catch.
 *
 * <p>Runs against the seeded schedule and <em>does</em> move money, so it resets the baseline afterwards
 * rather than leaving the figures other tests assert on changed.
 */
@SpringBootTest(properties = "qa.test-support.enabled=true")
@AutoConfigureMockMvc
class InstallmentInvoiceLedgerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private JsonNode getJson(String path) throws Exception {
    return objectMapper.readTree(
        mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString());
  }

  private long seededInstallmentInvoiceId(String invoiceNumber) throws Exception {
    for (JsonNode invoice : getJson("/api/invoices")) {
      if (invoiceNumber.equals(invoice.get("invoiceNumber").asText())) {
        return invoice.get("id").asLong();
      }
    }
    throw new AssertionError("The seeded baseline no longer contains " + invoiceNumber);
  }

  private void reset() throws Exception {
    mockMvc.perform(post("/api/test-support/reset")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("a billed installment has raised an invoice for exactly what it asks for")
  void aBilledInstallmentHasRaisedAnInvoiceForWhatItAsksFor() throws Exception {
    JsonNode schedule = getJson("/api/terms/SEED-TERM-001/schedule");
    BigDecimal firstInstallment = schedule.get(0).get("amountDue").decimalValue();

    JsonNode invoices = getJson("/api/invoices");
    boolean matched = false;
    for (JsonNode invoice : invoices) {
      if ("SEED-INS-INV-001".equals(invoice.get("invoiceNumber").asText())) {
        org.assertj.core.api.Assertions.assertThat(invoice.get("totalAmount").decimalValue())
            .as("the document has to bill what the schedule says, or the two disagree in front of the customer")
            .isEqualByComparingTo(firstInstallment);
        matched = true;
      }
    }
    org.assertj.core.api.Assertions.assertThat(matched).isTrue();
  }

  @Test
  @DisplayName("paying an installment's invoice moves the ledger behind it")
  void payingAnInstallmentsInvoiceMovesTheLedger() throws Exception {
    try {
      BigDecimal balanceBefore = getJson("/api/terms/SEED-TERM-001").get("balance").decimalValue();
      int remainingBefore =
          getJson("/api/terms/SEED-TERM-001").get("installmentsRemaining").asInt();
      long invoiceId = seededInstallmentInvoiceId("SEED-INS-INV-003");

      mockMvc
          .perform(
              post("/api/invoices/" + invoiceId + "/payments")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"amount\":132.80,\"method\":\"PRE_AUTHORIZED_DEBIT\",\"reference\":\"IT-CASCADE\"}"))
          .andExpect(status().isCreated());

      JsonNode term = getJson("/api/terms/SEED-TERM-001");
      org.assertj.core.api.Assertions.assertThat(term.get("balance").decimalValue())
          .as("an invoice cannot settle without the term it bills settling too")
          .isEqualByComparingTo(balanceBefore.subtract(new BigDecimal("132.80")));
      org.assertj.core.api.Assertions.assertThat(term.get("installmentsRemaining").asInt())
          .isEqualTo(remainingBefore - 1);
    } finally {
      reset();
    }
  }

  @Test
  @DisplayName("a payment refused by the invoice never reaches the ledger")
  void aPaymentRefusedByTheInvoiceNeverReachesTheLedger() throws Exception {
    BigDecimal balanceBefore = getJson("/api/terms/SEED-TERM-001").get("balance").decimalValue();
    long invoiceId = seededInstallmentInvoiceId("SEED-INS-INV-003");

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":9999.00,\"method\":\"CARD\",\"reference\":\"IT-REFUSED\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("EXCEEDS_OUTSTANDING_BALANCE"));

    org.assertj.core.api.Assertions.assertThat(
            getJson("/api/terms/SEED-TERM-001").get("balance").decimalValue())
        .as("the invoice's rules run first, so a refusal must leave the ledger untouched")
        .isEqualByComparingTo(balanceBefore);
  }

  @Test
  @DisplayName("an invoice raised directly against a policy has no ledger to move")
  void anInvoiceWithNoInstallmentHasNoLedgerToMove() throws Exception {
    ApiTestClient api = new ApiTestClient(mockMvc, objectMapper);
    long invoiceId = api.createInvoice("200.00");

    mockMvc
        .perform(
            post("/api/invoices/" + invoiceId + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":200.00,\"method\":\"CARD\",\"reference\":\"IT-PLAIN\"}"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/invoices/" + invoiceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
  }

  @Test
  @DisplayName("resetting restores the seeded schedules and ledgers, not just the invoices")
  void resettingRestoresSchedulesAndLedgers() throws Exception {
    mockMvc
        .perform(
            post("/api/terms/SEED-TERM-001/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":132.80}"))
        .andExpect(status().isCreated());

    reset();

    mockMvc
        .perform(get("/api/accounts/ACCT-100001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalBalance").value(1328.00));
    mockMvc
        .perform(get("/api/accounts/ACCT-100002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nsfCount").value(1))
        .andExpect(jsonPath("$.totalBalance").value(1137.00));
  }
}
