package com.insurancebilling.api;

import com.insurancebilling.api.dto.BillingTransactionResponse;
import com.insurancebilling.api.dto.InstallmentResponse;
import com.insurancebilling.api.dto.PolicyTermResponse;
import com.insurancebilling.api.dto.PortfolioRow;
import com.insurancebilling.domain.BillingAccount;
import com.insurancebilling.domain.Money;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.service.BillingService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The agent's view: every term on the books at once, and the detail of whichever one is selected.
 *
 * <p>The policyholder console starts from an account because a policyholder has one. An agent does not
 * - they arrive knowing a policy number or nothing at all - so this screen starts from the portfolio
 * and drills in. Below the grid are the same three panels the policyholder sees, rendered from the same
 * fragment, because an agent quoting different figures to the customer than the customer can read for
 * themselves is the failure this console exists to avoid.
 *
 * <p>Read-only by design. Money moves through the API's payment endpoints, which apply the term's rules;
 * a screen that could post entries of its own would be a second way into the same arithmetic.
 *
 * <p><b>Known limitation: the grid is not paginated.</b> It renders every term on the books, and the
 * account load hydrates every schedule and ledger along with them. On a seeded database that is two
 * rows; on a real book it would be the slowest page in the application, and the fix is a paged query
 * projecting only the columns the grid shows rather than whole aggregates. It is left as it is because
 * {@code GET /api/invoices} has the same gap and the two belong in one change, not because the cost is
 * acceptable.
 */
@Controller
@RequestMapping("/agent")
public class AgentConsoleWebController {

  /** The panels below the grid. Kept here so an unknown tab falls back rather than rendering blank. */
  static final List<String> TERM_TABS = List.of("summary", "transactions", "schedule");

  /**
   * Policy number, then term number. The grid is read down the page looking for a policy, so the order
   * has to be the one a reader would sort it into themselves - not insertion order, which is an
   * accident of how the seed data happened to be written.
   */
  private static final Comparator<PolicyTerm> GRID_ORDER =
      Comparator.comparing((PolicyTerm term) -> term.getPolicy().getPolicyNumber())
          .thenComparingInt(PolicyTerm::getTermNumber);

  private final BillingService billing;
  private final AssistantPanel assistantPanel;

  public AgentConsoleWebController(BillingService billing, AssistantPanel assistantPanel) {
    this.billing = billing;
    this.assistantPanel = assistantPanel;
  }

  /**
   * Puts a question to the assistant and re-renders the console with the answer on it.
   *
   * <p>The account comes from the selected term rather than from the request. An agent looking at a
   * term is asking about the account that term is billed to, and a parameter naming a different one
   * would let the screen and the answer disagree about whose money is being discussed.
   */
  @PostMapping("/assistant")
  public String ask(
      @RequestParam(required = false) String term,
      @RequestParam(required = false, defaultValue = "summary") String tab,
      @RequestParam(required = false) String question,
      Model model) {

    String view = console(term, tab, model);
    PolicyTermResponse selected = (PolicyTermResponse) model.getAttribute("selectedTerm");
    if (selected != null) {
      model.addAttribute(
          "assistantAnswer", assistantPanel.answer(selected.accountReference(), question));
      model.addAttribute("assistantQuestion", question);
    }
    return view;
  }

  @GetMapping
  public String console(
      @RequestParam(required = false) String term,
      @RequestParam(required = false, defaultValue = "summary") String tab,
      Model model) {

    List<PolicyTerm> terms =
        billing.findAllAccounts().stream()
            .map(BillingAccount::getTerms)
            .flatMap(List::stream)
            .sorted(GRID_ORDER)
            .toList();

    List<PortfolioRow> rows = terms.stream().map(PortfolioRow::from).toList();
    model.addAttribute("rows", rows);
    model.addAttribute("portfolioTotal", total(rows));
    model.addAttribute("tabBasePath", "/agent");

    if (rows.isEmpty()) {
      model.addAttribute("selectedTerm", null);
      return "agent/console";
    }

    // Defaulting to the first row rather than refusing means /agent is a URL an agent can bookmark
    // without naming a term, and the grid is the part of the screen they came for anyway.
    PolicyTerm selected =
        terms.stream()
            .filter(candidate -> candidate.getTermReference().equals(term))
            .findFirst()
            .orElse(terms.get(0));

    PolicyTerm loaded = billing.findTerm(selected.getTermReference());
    List<InstallmentResponse> schedule =
        loaded.getInstallments().stream().map(InstallmentResponse::from).toList();
    List<BillingTransactionResponse> ledger =
        loaded.getTransactions().stream()
            .map(transaction -> BillingTransactionResponse.from(transaction, loaded))
            .toList();

    model.addAttribute("selectedTerm", PolicyTermResponse.from(loaded));
    model.addAttribute("schedule", schedule);
    // Newest first, matching the policyholder's ledger. The running balance on each line is still
    // derived from the lines below it, so the arithmetic reads downward even though the list does not.
    model.addAttribute("ledger", ledger.reversed());
    model.addAttribute("activeTab", TERM_TABS.contains(tab) ? tab : "summary");
    return "agent/console";
  }

  /**
   * What the whole book owes.
   *
   * <p>Summed from the rows on screen rather than queried separately, so the total can never disagree
   * with the column above it - which is the one thing a reader will check by hand.
   */
  private static BigDecimal total(List<PortfolioRow> rows) {
    return Money.normalise(
        rows.stream().map(PortfolioRow::balance).reduce(Money.ZERO, BigDecimal::add));
  }
}
