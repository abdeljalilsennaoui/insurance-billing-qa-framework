package com.insurancebilling.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration coverage for the billing account, schedule and ledger endpoints.
 *
 * <p>Every assertion is against the seeded baseline, which is read-only for this class: nothing here
 * moves money, so the figures are the ones {@code SeedDataLoader} documents and a change to them is
 * meant to fail here.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BillingAccountApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("an account summary reports the balance derived from its terms")
  void accountSummaryReportsTheDerivedBalance() throws Exception {
    mockMvc
        .perform(get("/api/accounts/ACCT-100001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountReference").value("ACCT-100001"))
        .andExpect(jsonPath("$.customerName").value("Dominique Fortin"))
        .andExpect(jsonPath("$.totalBalance").value(1328.00))
        .andExpect(jsonPath("$.terms[0].scheduledTotal").value(1591.60))
        .andExpect(jsonPath("$.terms[0].installmentsRemaining").value(10));
  }

  @Test
  @DisplayName("bank details are returned masked, and the response holds nothing to unmask")
  void bankDetailsAreReturnedMasked() throws Exception {
    mockMvc
        .perform(get("/api/accounts/ACCT-100001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentInformation.paymentMethod").value("PRE_AUTHORIZED_DEBIT"))
        .andExpect(jsonPath("$.paymentInformation.accountNumber").value("****204"))
        .andExpect(jsonPath("$.paymentInformation.institutionNumber").value("***"))
        .andExpect(jsonPath("$.paymentInformation.branchNumber").value("*****"));
  }

  @Test
  @DisplayName("no account response carries an account number longer than the digits held")
  void noAccountResponseCarriesAWholeAccountNumber() throws Exception {
    String body =
        mockMvc
            .perform(get("/api/accounts"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    java.util.regex.Matcher masked =
        java.util.regex.Pattern.compile("\"accountNumber\":\"([^\"]*)\"").matcher(body);
    int found = 0;
    while (masked.find()) {
      found++;
      org.assertj.core.api.Assertions.assertThat(masked.group(1))
          .as("an account number reaching a caller must be masked to its last three digits")
          .matches("\\*{4}\\d{3}");
    }
    org.assertj.core.api.Assertions.assertThat(found).isPositive();
  }

  @Test
  @DisplayName("an accented insured name survives the round trip to JSON")
  void anAccentedNameSurvivesTheRoundTrip() throws Exception {
    mockMvc
        .perform(get("/api/accounts/ACCT-100002").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.customerName").value("Élise Marchand"));
  }

  @Test
  @DisplayName("an unknown account reference is a 404, not an empty summary")
  void anUnknownAccountIsNotFound() throws Exception {
    mockMvc
        .perform(get("/api/accounts/ACCT-NOPE"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("a schedule collects exactly what its term was posted for")
  void aScheduleCollectsExactlyWhatTheTermWasPostedFor() throws Exception {
    mockMvc
        .perform(get("/api/terms/SEED-TERM-002/schedule"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(12)))
        .andExpect(jsonPath("$[0].amountDue").value(90.87))
        .andExpect(jsonPath("$[0].premiumAmount").value(83.37))
        .andExpect(jsonPath("$[1].amountDue").value(92.83))
        .andExpect(jsonPath("$[11].amountDue").value(92.83));
  }

  @Test
  @DisplayName("an installment falls due after the date it is drawn, not on it")
  void anInstallmentFallsDueAfterItIsDrawn() throws Exception {
    mockMvc
        .perform(get("/api/terms/SEED-TERM-001/schedule"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].scheduledDate").exists())
        .andExpect(jsonPath("$[0].dueDate").exists())
        .andExpect(jsonPath("$[0].feeAmount").value(0.00))
        .andExpect(jsonPath("$[1].feeAmount").value(2.00));
  }

  @Test
  @DisplayName("the ledger is returned newest first with a running balance on every line")
  void theLedgerIsReturnedNewestFirstWithARunningBalance() throws Exception {
    mockMvc
        .perform(get("/api/terms/SEED-TERM-002/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", Matchers.hasSize(4)))
        .andExpect(jsonPath("$[0].type").value("NSF_FEE"))
        .andExpect(jsonPath("$[0].balanceAfter").value(1137.00))
        .andExpect(jsonPath("$[3].type").value("NEW_BUSINESS"))
        .andExpect(jsonPath("$[3].balanceAfter").value(1112.00));
  }

  @Test
  @DisplayName("a returned payment reverses its original line column for column")
  void aReturnedPaymentReversesColumnForColumn() throws Exception {
    mockMvc
        .perform(get("/api/terms/SEED-TERM-002/transactions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].type").value("PAYMENT_RETURNED"))
        .andExpect(jsonPath("$[1].premiumAmount").value(83.37))
        .andExpect(jsonPath("$[2].type").value("PAYMENT"))
        .andExpect(jsonPath("$[2].premiumAmount").value(-83.37));
  }

  @Test
  @DisplayName("an account that has had a payment returned reports both tallies")
  void anAccountWithAReturnedPaymentReportsBothTallies() throws Exception {
    mockMvc
        .perform(get("/api/accounts/ACCT-100002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nsfCount").value(1))
        .andExpect(jsonPath("$.returnedPaymentCount").value(1))
        .andExpect(jsonPath("$.totalBalance").value(1137.00));
  }

  @Test
  @DisplayName("an unknown term reference is a 404 on every one of its sub-resources")
  void anUnknownTermIsNotFoundOnEverySubResource() throws Exception {
    for (String path :
        new String[] {"/api/terms/NOPE", "/api/terms/NOPE/schedule", "/api/terms/NOPE/transactions"}) {
      mockMvc
          .perform(get(path))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
  }

  @Test
  @DisplayName("a payment with no amount is a malformed request, not a refused one")
  void aPaymentWithNoAmountIsMalformed() throws Exception {
    mockMvc
        .perform(
            post("/api/terms/SEED-TERM-001/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors.amount").exists());
  }

  @Test
  @DisplayName("a return naming a reason this platform has no meaning for is malformed")
  void aReturnWithAnUnknownReasonIsMalformed() throws Exception {
    mockMvc
        .perform(
            post("/api/terms/transactions/SEED-TXN-002/return")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"BECAUSE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  @DisplayName("returning a ledger line that does not exist is a 404")
  void returningAnUnknownLedgerLineIsNotFound() throws Exception {
    mockMvc
        .perform(
            post("/api/terms/transactions/TXN-NOPE/return")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"INSUFFICIENT_FUNDS\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
