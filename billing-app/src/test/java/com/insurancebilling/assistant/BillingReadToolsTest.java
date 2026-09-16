package com.insurancebilling.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.insurancebilling.api.dto.BillingAccountResponse;
import com.insurancebilling.api.dto.BillingTransactionResponse;
import com.insurancebilling.api.dto.InstallmentResponse;
import com.insurancebilling.api.dto.PolicyTermResponse;
import com.insurancebilling.service.BillingService;
import com.insurancebilling.service.ResourceNotFoundException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The read-only tool surface, and the assertions that keep it read-only.
 *
 * <p>Two of these tests are the point of the class. {@code theRegistryExposesExactlyFourTools} fails
 * the moment a fifth tool is added, so widening the surface is a decision somebody has to take
 * deliberately rather than a diff that slips through. {@code noToolReachesAMethodThatMovesMoney}
 * calls every tool and then asserts that none of {@link BillingService}'s four mutating methods was
 * invoked — a deny-list by name, which is the only version of this claim that keeps working when the
 * implementation is refactored.
 *
 * <p>A Mockito spy is used for that one assertion and nowhere else. The rest of the class runs
 * against the real service and the seeded baseline, because a registry proved correct only against
 * stubs would be proved correct against the wrong thing.
 */
@SpringBootTest
class BillingReadToolsTest {

  @Autowired private BillingReadTools tools;

  @MockitoSpyBean private BillingService billing;

  // ---------------------------------------------------------------------------------------------
  // The surface itself
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("the registry exposes exactly four tools, in a stable order")
  void theRegistryExposesExactlyFourTools() {
    assertThat(tools.toolNames())
        .containsExactly(
            BillingReadTools.FIND_ACCOUNT,
            BillingReadTools.FIND_TERM,
            BillingReadTools.GET_INSTALLMENT_SCHEDULE,
            BillingReadTools.GET_LEDGER);

    assertThat(tools.definitions())
        .extracting(BillingToolDefinition::name)
        .containsExactlyElementsOf(tools.toolNames());
  }

  @Test
  @DisplayName("calling every tool reaches no method that moves money")
  void noToolReachesAMethodThatMovesMoney() {
    invokeEveryTool();

    verify(billing, never()).openAccount(any());
    verify(billing, never()).bindTerm(anyString(), any());
    verify(billing, never()).payTerm(anyString(), any(BigDecimal.class), anyString());
    verify(billing, never()).returnPayment(anyString(), any());
  }

  @Test
  @DisplayName("the registry holds no service other than the billing service")
  void theRegistryHoldsNoServiceOtherThanTheBillingService() {
    List<Class<?>> services =
        java.util.Arrays.stream(BillingReadTools.class.getDeclaredFields())
            .map(Field::getType)
            .filter(type -> type.getPackageName().equals("com.insurancebilling.service"))
            .toList();

    assertThat(services).containsExactly(BillingService.class);
  }

  @Test
  @DisplayName("reading changes no balance and posts nothing to a ledger")
  void readingChangesNoBalanceAndPostsNothingToALedger() {
    BigDecimal balanceBefore = accountBalance();
    int ledgerLinesBefore = ledger().size();

    invokeEveryTool();
    invokeEveryTool();

    assertThat(accountBalance()).isEqualByComparingTo(balanceBefore);
    assertThat(ledger()).hasSize(ledgerLinesBefore);
  }

  // ---------------------------------------------------------------------------------------------
  // What the tools return
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("find_account reports the seeded account's derived balance")
  void findAccountReportsTheSeededBalance() {
    BillingAccountResponse account =
        (BillingAccountResponse) tools.invoke(BillingReadTools.FIND_ACCOUNT, "ACCT-100001");

    assertThat(account.accountReference()).isEqualTo("ACCT-100001");
    assertThat(account.customerName()).isEqualTo("Dominique Fortin");
    assertThat(account.totalBalance()).isEqualByComparingTo("1328.00");
    assertThat(account.terms()).isNotEmpty();
  }

  @Test
  @DisplayName("find_term reports the term's scheduled total and remaining installments")
  void findTermReportsTheScheduledTotal() {
    PolicyTermResponse term = firstTerm();

    PolicyTermResponse found =
        (PolicyTermResponse) tools.invoke(BillingReadTools.FIND_TERM, term.termReference());

    assertThat(found.scheduledTotal()).isEqualByComparingTo("1591.60");
    assertThat(found.installmentsRemaining()).isEqualTo(10);
  }

  @Test
  @DisplayName("the schedule comes back in sequence order and reconciles to the term")
  void theScheduleComesBackInSequenceOrder() {
    PolicyTermResponse term = firstTerm();

    @SuppressWarnings("unchecked")
    List<InstallmentResponse> schedule =
        (List<InstallmentResponse>)
            tools.invoke(BillingReadTools.GET_INSTALLMENT_SCHEDULE, term.termReference());

    assertThat(schedule).isNotEmpty();
    assertThat(schedule)
        .extracting(InstallmentResponse::sequenceNumber)
        .isSorted()
        .startsWith(1);

    BigDecimal scheduled =
        schedule.stream()
            .map(InstallmentResponse::amountDue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(scheduled).isEqualByComparingTo(term.scheduledTotal());
  }

  @Test
  @DisplayName("the ledger comes back newest first, as the REST endpoint returns it")
  void theLedgerComesBackNewestFirst() {
    List<BillingTransactionResponse> ledger = ledger();

    assertThat(ledger).isNotEmpty();
    assertThat(ledger)
        .extracting(BillingTransactionResponse::processedAt)
        .isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }

  @Test
  @DisplayName("bank details come back masked, and the result holds nothing to unmask them with")
  void bankDetailsComeBackMasked() {
    BillingAccountResponse account =
        (BillingAccountResponse) tools.invoke(BillingReadTools.FIND_ACCOUNT, "ACCT-100001");

    assertThat(account.paymentInformation()).isNotNull();
    assertThat(account.toString()).doesNotContain("bank_account_number");
    assertThat(account.toString()).matches(candidate -> !candidate.matches(".*\\d{7,}.*"));
  }

  // ---------------------------------------------------------------------------------------------
  // Schemas and failure
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("every schema requires its single argument and forbids any other")
  void everySchemaRequiresItsSingleArgumentAndForbidsAnyOther() {
    assertThat(tools.definitions())
        .allSatisfy(
            definition -> {
              Map<String, Object> schema = definition.inputSchema();
              assertThat(schema).containsEntry("type", "object");
              assertThat(schema).containsEntry("additionalProperties", false);
              assertThat(schema).containsEntry("required", List.of(definition.argument()));

              @SuppressWarnings("unchecked")
              Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
              assertThat(properties).containsOnlyKeys(definition.argument());
            });
  }

  @Test
  @DisplayName("an unknown tool is refused by name, and the message lists what is available")
  void anUnknownToolIsRefusedByName() {
    assertThatThrownBy(() -> tools.invoke("pay_term", "SEED-TERM-001"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pay_term")
        .hasMessageContaining(BillingReadTools.FIND_TERM);
  }

  @Test
  @DisplayName("a blank argument is refused rather than searched for")
  void aBlankArgumentIsRefused() {
    assertThatThrownBy(() -> tools.invoke(BillingReadTools.FIND_ACCOUNT, "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountReference");

    assertThatThrownBy(() -> tools.invoke(BillingReadTools.FIND_ACCOUNT, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("an account that does not exist is not found, not empty")
  void anAccountThatDoesNotExistIsNotFound() {
    assertThatThrownBy(() -> tools.invoke(BillingReadTools.FIND_ACCOUNT, "ACCT-NOPE"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ---------------------------------------------------------------------------------------------

  private void invokeEveryTool() {
    PolicyTermResponse term = firstTerm();
    tools.invoke(BillingReadTools.FIND_ACCOUNT, "ACCT-100001");
    tools.invoke(BillingReadTools.FIND_TERM, term.termReference());
    tools.invoke(BillingReadTools.GET_INSTALLMENT_SCHEDULE, term.termReference());
    tools.invoke(BillingReadTools.GET_LEDGER, term.termReference());
  }

  private PolicyTermResponse firstTerm() {
    BillingAccountResponse account =
        (BillingAccountResponse) tools.invoke(BillingReadTools.FIND_ACCOUNT, "ACCT-100001");
    return account.terms().get(0);
  }

  private BigDecimal accountBalance() {
    BillingAccountResponse account =
        (BillingAccountResponse) tools.invoke(BillingReadTools.FIND_ACCOUNT, "ACCT-100001");
    return account.totalBalance();
  }

  @SuppressWarnings("unchecked")
  private List<BillingTransactionResponse> ledger() {
    return (List<BillingTransactionResponse>)
        tools.invoke(BillingReadTools.GET_LEDGER, firstTerm().termReference());
  }
}
