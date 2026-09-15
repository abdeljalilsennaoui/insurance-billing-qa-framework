package com.insurancebilling.api;

import com.insurancebilling.api.dto.BillingAccountRequest;
import com.insurancebilling.api.dto.BillingAccountResponse;
import com.insurancebilling.api.dto.PolicyTermRequest;
import com.insurancebilling.api.dto.PolicyTermResponse;
import com.insurancebilling.domain.BillingAccount;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.service.BillingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read access to billing accounts.
 *
 * <p>Accounts are addressed by their business reference rather than by database id. A policyholder
 * quoting their account number to an agent is quoting the reference, and an API that needed the agent to
 * translate it first would be modelling the database instead of the business.
 */
@RestController
@RequestMapping("/api/accounts")
public class BillingAccountController {

  private final BillingService billing;

  public BillingAccountController(BillingService billing) {
    this.billing = billing;
  }

  @GetMapping
  public List<BillingAccountResponse> list() {
    return billing.findAllAccounts().stream().map(BillingAccountResponse::from).toList();
  }

  @GetMapping("/{accountReference}")
  public BillingAccountResponse get(@PathVariable String accountReference) {
    return BillingAccountResponse.from(billing.findAccount(accountReference));
  }

  @GetMapping("/{accountReference}/terms")
  public List<PolicyTermResponse> terms(@PathVariable String accountReference) {
    return billing.findTermsForAccount(accountReference).stream().map(PolicyTermResponse::from).toList();
  }

  @PostMapping
  public ResponseEntity<BillingAccountResponse> open(@Valid @RequestBody BillingAccountRequest request) {
    BillingAccount account = billing.openAccount(request);
    return ResponseEntity.created(URI.create("/api/accounts/" + account.getAccountReference()))
        .body(BillingAccountResponse.from(account));
  }

  /** Binds a term to this account: generates its schedule and posts it to the ledger. */
  @PostMapping("/{accountReference}/terms")
  public ResponseEntity<PolicyTermResponse> bindTerm(
      @PathVariable String accountReference, @Valid @RequestBody PolicyTermRequest request) {
    PolicyTerm term = billing.bindTerm(accountReference, request);
    return ResponseEntity.created(URI.create("/api/terms/" + term.getTermReference()))
        .body(PolicyTermResponse.from(term));
  }
}
