package com.insurancebilling.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancebilling.api.dto.InvoiceResponse;
import com.insurancebilling.domain.Invoice;
import com.insurancebilling.repository.InvoiceRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The overdue rule is evaluated in the configured business time zone, not in the host's.
 *
 * <p>This is the regression test for DEF-012. Every other suite in this repository evaluates the
 * overdue rule in the same zone it was written in, so the test and the code make the identical
 * assumption and agree with each other — which is why 165 tests and 99% line coverage did not catch
 * the defect. Coverage could say those lines ran; nothing said they ran with the right date.
 *
 * <p>The fixture makes the zone load-bearing. At {@code 2026-01-15T04:30Z} the calendar date is the
 * <b>15th</b> in UTC and still the <b>14th</b> in {@code America/Toronto}, where it is 23:30 the
 * previous evening. An invoice due on the 14th is therefore not yet overdue for the business, and is
 * already overdue to a naive UTC reading. Every "not overdue" assertion below fails if the date is
 * read from anywhere other than the configured zone.
 *
 * <p>The clock is frozen rather than merely zoned, so the assertions are absolute dates instead of
 * offsets from "today". The zone it is frozen in is read back from {@code billing.time-zone} — the
 * same property the application builds its own clock from — so this test asks the application the
 * question its own configuration answers, rather than restating a literal.
 */
@SpringBootTest(properties = "billing.time-zone=America/Toronto")
@AutoConfigureMockMvc
class InvoiceOverdueBusinessZoneTest {

  /** 23:30 on 14 January in America/Toronto. 04:30 on 15 January in UTC. */
  private static final Instant BOUNDARY = Instant.parse("2026-01-15T04:30:00Z");

  private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Toronto");
  private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 1, 14);
  private static final LocalDate UTC_DATE = LocalDate.of(2026, 1, 15);

  /**
   * Replaces the business clock with one frozen at the boundary instant.
   *
   * <p>The zone is deliberately not hard-coded here. Freezing the instant while leaving the zone to
   * configuration is what keeps this a test of the business zone rather than a test of a literal.
   */
  @TestConfiguration
  static class FrozenBusinessClock {

    @Bean
    @Primary
    Clock frozenBusinessClock(@Value("${billing.time-zone}") String zone) {
      return Clock.fixed(BOUNDARY, ZoneId.of(zone));
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private InvoiceRepository invoices;

  private ApiTestClient api;

  @BeforeEach
  void setUp() {
    api = new ApiTestClient(mockMvc, objectMapper);
  }

  @Test
  @DisplayName("the fixture instant really does fall on different dates in the two zones")
  void fixtureStraddlesMidnight() {
    assertThat(BOUNDARY.atZone(BUSINESS_ZONE).toLocalDate())
        .as("the business zone's date at the boundary instant")
        .isEqualTo(BUSINESS_DATE);
    assertThat(BOUNDARY.atZone(ZoneOffset.UTC).toLocalDate())
        .as("UTC's date at the same instant")
        .isEqualTo(UTC_DATE);
  }

  @Test
  @DisplayName("an invoice due on the business date is not yet overdue, though UTC has moved on")
  void invoiceDueOnTheBusinessDateIsNotYetOverdue() throws Exception {
    JsonNode invoice = createInvoice("300.00", BUSINESS_DATE);

    mockMvc
        .perform(get("/api/invoices/{id}", invoice.get("id").asLong()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dueDate").value(BUSINESS_DATE.toString()))
        .andExpect(jsonPath("$.overdue").value(false))
        .andExpect(jsonPath("$.status").value("UNPAID"));
  }

  @Test
  @DisplayName("an invoice due the day before the business date is overdue")
  void invoiceDueTheDayBeforeTheBusinessDateIsOverdue() throws Exception {
    JsonNode invoice = createInvoice("300.00", BUSINESS_DATE.minusDays(1));

    mockMvc
        .perform(get("/api/invoices/{id}", invoice.get("id").asLong()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.overdue").value(true))
        .andExpect(jsonPath("$.status").value("OVERDUE"));

    mockMvc
        .perform(get("/api/invoices").param("status", "OVERDUE"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].invoiceNumber").value(hasItem(invoice.get("invoiceNumber").asText())));
  }

  @Test
  @DisplayName("the console judges the same invoice the same way as the API")
  void consoleAgreesWithTheApi() throws Exception {
    JsonNode invoice = createInvoice("300.00", BUSINESS_DATE);

    InvoiceResponse rendered =
        (InvoiceResponse)
            mockMvc
                .perform(get("/invoices/{id}", invoice.get("id").asLong()))
                .andExpect(status().isOk())
                .andReturn()
                .getModelAndView()
                .getModel()
                .get("invoice");

    assertThat(rendered.overdue())
        .as("the console must not contradict the API about the same invoice")
        .isFalse();
  }

  @Test
  @DisplayName("the seeded baseline is built relative to the business date, not the host's")
  void seededBaselineIsBuiltFromTheBusinessDate() {
    Invoice seeded = invoices.findByInvoiceNumber("SEED-INV-001").orElseThrow();

    assertThat(seeded.getIssueDate()).isEqualTo(BUSINESS_DATE.minusDays(5));
    assertThat(seeded.getDueDate()).isEqualTo(BUSINESS_DATE.plusDays(25));
  }

  @Test
  @DisplayName("a payment is stamped from the business clock, not from wall-clock time")
  void paymentIsStampedFromTheBusinessClock() throws Exception {
    JsonNode invoice = createInvoice("300.00", BUSINESS_DATE);

    String response =
        mockMvc
            .perform(
                post("/api/invoices/{id}/payments", invoice.get("id").asLong())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"amount":100.00,"method":"CARD","reference":"TZ-REF"}
                        """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(Instant.parse(objectMapper.readTree(response).get("receivedAt").asText()))
        .isEqualTo(BOUNDARY);
  }

  /** Raises an invoice with an explicit due date, on a fresh customer and an active policy. */
  private JsonNode createInvoice(String totalAmount, LocalDate dueDate) throws Exception {
    long policyId = api.createPolicy(api.createCustomer());
    String body =
        """
        {"policyId":%d,"totalAmount":%s,"issueDate":"%s","dueDate":"%s"}
        """
            .formatted(policyId, totalAmount, dueDate.minusDays(30), dueDate);
    return objectMapper.readTree(
        mockMvc
            .perform(post("/api/invoices").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
