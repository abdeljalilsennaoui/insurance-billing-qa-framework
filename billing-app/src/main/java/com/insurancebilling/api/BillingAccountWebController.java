package com.insurancebilling.api;

import com.insurancebilling.api.dto.BillingAccountResponse;
import com.insurancebilling.api.dto.BillingTransactionResponse;
import com.insurancebilling.api.dto.InstallmentResponse;
import com.insurancebilling.api.dto.PolicyTermResponse;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.service.BillingService;
import com.insurancebilling.service.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The policyholder's view of their billing account.
 *
 * <p>Two screens. The summary answers "what do I owe and when does it come out"; the terms screen
 * answers "show me the arithmetic". They are separate pages rather than one long one because those are
 * two different visits: the first is the common one and should be short.
 *
 * <p>The terms screen's three panels are tabs on one page rather than three pages. The tab is a query
 * parameter, so every panel has a URL of its own that a policyholder can bookmark and a test can open
 * directly - which a tab switched by JavaScript alone would not have.
 */
@Controller
@RequestMapping("/accounts")
public class BillingAccountWebController {

  /** The panels on the terms screen. Kept here so an unknown tab falls back rather than rendering blank. */
  static final List<String> TERM_TABS = List.of("summary", "transactions", "schedule");

  private final BillingService billing;

  public BillingAccountWebController(BillingService billing) {
    this.billing = billing;
  }

  @GetMapping("/{accountReference}")
  public String summary(@PathVariable String accountReference, Model model) {
    BillingAccountResponse account =
        BillingAccountResponse.from(billing.findAccount(accountReference));
    model.addAttribute("account", account);
    model.addAttribute("activeNav", "summary");
    return "accounts/summary";
  }

  @GetMapping("/{accountReference}/terms")
  public String terms(
      @PathVariable String accountReference,
      @RequestParam(required = false) String term,
      @RequestParam(required = false, defaultValue = "summary") String tab,
      Model model) {

    BillingAccountResponse account =
        BillingAccountResponse.from(billing.findAccount(accountReference));
    model.addAttribute("account", account);
    model.addAttribute("activeNav", "terms");

    if (account.terms().isEmpty()) {
      model.addAttribute("selectedTerm", null);
      return "accounts/terms";
    }

    // Defaulting to the first term rather than refusing means the left nav can link here without
    // knowing which terms exist.
    PolicyTermResponse selected =
        account.terms().stream()
            .filter(candidate -> candidate.termReference().equals(term))
            .findFirst()
            .orElse(account.terms().get(0));

    PolicyTerm loaded = billing.findTerm(selected.termReference());
    List<InstallmentResponse> schedule =
        loaded.getInstallments().stream().map(InstallmentResponse::from).toList();
    List<BillingTransactionResponse> ledger =
        loaded.getTransactions().stream()
            .map(transaction -> BillingTransactionResponse.from(transaction, loaded))
            .toList();

    model.addAttribute("selectedTerm", selected);
    model.addAttribute("schedule", schedule);
    // Newest first, which is the order a ledger is read in. The running balance on each line is still
    // derived from the lines below it, so the arithmetic reads downward even though the list does not.
    model.addAttribute("ledger", ledger.reversed());
    model.addAttribute("activeTab", TERM_TABS.contains(tab) ? tab : "summary");
    return "accounts/terms";
  }

  /**
   * Renders the console's own not-found page.
   *
   * <p>The global advice answers JSON, which is right for the API and wrong for somebody who mistyped an
   * account number in a browser.
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String accountNotFound(Model model) {
    model.addAttribute("activeNav", "summary");
    return "accounts/not-found";
  }
}
