package com.insurancebilling.assistant;

import com.insurancebilling.api.dto.BillingAccountResponse;
import com.insurancebilling.api.dto.BillingTransactionResponse;
import com.insurancebilling.api.dto.InstallmentResponse;
import com.insurancebilling.api.dto.PolicyTermResponse;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.service.BillingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * The read-only surface a machine consumer is allowed to see.
 *
 * <p>Two consumers read through this class — the billing assistant and the MCP server — and neither
 * has a tool definition of its own. One registry means the question "what can a machine reach?" has
 * one answer, in one file, with one set of tests, rather than an answer per integration that drifts
 * the first time somebody adds a tool to only one of them.
 *
 * <p><b>Read-only, and narrowly so.</b> The registry delegates to four read methods on
 * {@link BillingService}. Nothing that opens an account, binds a term, posts a payment, returns a
 * payment, creates, cancels or changes a status is reachable from here, and
 * {@code BillingReadToolsTest} asserts that against an explicit deny-list so widening the surface
 * later fails a test rather than passing unnoticed. The reasoning is the one the agent console
 * already gives for being read-only: money moves through the payment endpoints, which apply the
 * term's rules, and a second way in would be a second place for those rules to be missed.
 *
 * <p><b>"Read-only" does not mean "changes nothing".</b> Reading a term ages its schedule — an
 * installment whose due date has passed becomes overdue the moment somebody asks, which is
 * {@link BillingService}'s documented behaviour and not something this class should suppress. What
 * the tools cannot do is post a transaction, move a balance, or bring an aggregate into or out of
 * existence. That is the distinction the tests assert, because the looser claim would be false and
 * the stricter one would require lying about what a read does.
 *
 * <p>Results are the same response records the REST API returns, never entities. Besides keeping one
 * JSON shape across both surfaces, it is what makes a full bank account number unreachable rather
 * than merely filtered: {@code PaymentInformationResponse} is built from
 * {@code BankAccountReference}, which has no field capable of holding one.
 */
@Component
public class BillingReadTools {

  public static final String FIND_ACCOUNT = "find_account";
  public static final String FIND_TERM = "find_term";
  public static final String GET_INSTALLMENT_SCHEDULE = "get_installment_schedule";
  public static final String GET_LEDGER = "get_ledger";

  private static final String ACCOUNT_REFERENCE = "accountReference";
  private static final String TERM_REFERENCE = "termReference";

  private final BillingService billing;

  /**
   * Insertion-ordered so {@link #definitions()} is stable across runs.
   *
   * <p>That matters more than it looks: the tool list is the first thing in a model request and sits
   * inside the cached prefix, so a set that iterated in a different order on each start would
   * invalidate the prompt cache on every call and the cost would drift with no code change to blame.
   */
  private final Map<String, Function<String, Object>> handlers = new LinkedHashMap<>();

  private final Map<String, BillingToolDefinition> definitions = new LinkedHashMap<>();

  public BillingReadTools(BillingService billing) {
    this.billing = billing;

    register(
        new BillingToolDefinition(
            FIND_ACCOUNT,
            "Look up a billing account: what is owed in total, what falls due next, how it is "
                + "collected, and a header line for every policy term on it.",
            ACCOUNT_REFERENCE,
            "The account reference as the policyholder would quote it, for example ACCT-100001."),
        this::findAccount);

    register(
        new BillingToolDefinition(
            FIND_TERM,
            "Look up one policy term: its premium, tax and fee, the scheduled total, the current "
                + "balance and how many installments remain.",
            TERM_REFERENCE,
            "The term reference, for example SEED-TERM-001."),
        this::findTerm);

    register(
        new BillingToolDefinition(
            GET_INSTALLMENT_SCHEDULE,
            "The payment schedule for a policy term: every installment in order, with its due date, "
                + "its premium, tax and fee split, the amount due and whether it is paid.",
            TERM_REFERENCE,
            "The term reference whose schedule is wanted."),
        this::getInstallmentSchedule);

    register(
        new BillingToolDefinition(
            GET_LEDGER,
            "The billing ledger for a policy term: every transaction, newest first, with the "
                + "balance as it stood after each one.",
            TERM_REFERENCE,
            "The term reference whose ledger is wanted."),
        this::getLedger);
  }

  private void register(BillingToolDefinition definition, Function<String, Object> handler) {
    definitions.put(definition.name(), definition);
    handlers.put(definition.name(), handler);
  }

  /** Every tool a consumer may call, in a stable order. */
  public List<BillingToolDefinition> definitions() {
    return List.copyOf(definitions.values());
  }

  /** The names of every tool a consumer may call. */
  public Set<String> toolNames() {
    return definitions.keySet();
  }

  /**
   * Calls one tool by name.
   *
   * <p>An unknown name is an {@link IllegalArgumentException} rather than a null result, because the
   * caller is a model and a silently empty answer is exactly the kind of thing it would go on to
   * summarise as though it had meant something.
   */
  public Object invoke(String toolName, String argument) {
    Function<String, Object> handler = handlers.get(toolName);
    if (handler == null) {
      throw new IllegalArgumentException(
          "Unknown tool '" + toolName + "'. Available tools: " + String.join(", ", toolNames()));
    }
    if (argument == null || argument.isBlank()) {
      BillingToolDefinition definition = definitions.get(toolName);
      throw new IllegalArgumentException(
          "Tool '" + toolName + "' requires a non-blank " + definition.argument() + ".");
    }
    return handler.apply(argument.strip());
  }

  private BillingAccountResponse findAccount(String accountReference) {
    return BillingAccountResponse.from(billing.findAccount(accountReference));
  }

  private PolicyTermResponse findTerm(String termReference) {
    return PolicyTermResponse.from(billing.findTerm(termReference));
  }

  private List<InstallmentResponse> getInstallmentSchedule(String termReference) {
    return billing.findTerm(termReference).getInstallments().stream()
        .map(InstallmentResponse::from)
        .toList();
  }

  /**
   * Newest first, matching {@code GET /api/terms/{reference}/transactions}.
   *
   * <p>The term holds its transactions oldest first so the running balance can be derived down the
   * list; both this tool and the REST endpoint reverse the finished rows rather than the source, so
   * a reader comparing the two sees the same order and the derived {@code balanceAfter} on each line
   * still refers to the position the transaction actually occupies in the ledger.
   */
  private List<BillingTransactionResponse> getLedger(String termReference) {
    PolicyTerm term = billing.findTerm(termReference);
    List<BillingTransactionResponse> ledger =
        new ArrayList<>(
            term.getTransactions().stream()
                .map(transaction -> BillingTransactionResponse.from(transaction, term))
                .toList());
    Collections.reverse(ledger);
    return ledger;
  }
}
