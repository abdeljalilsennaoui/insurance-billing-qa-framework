package com.insurancebilling.api;

import com.insurancebilling.api.dto.BillingTransactionResponse;
import com.insurancebilling.api.dto.InstallmentResponse;
import com.insurancebilling.api.dto.PolicyTermResponse;
import com.insurancebilling.api.dto.ReturnPaymentRequest;
import com.insurancebilling.api.dto.TermPaymentRequest;
import com.insurancebilling.domain.BillingTransaction;
import com.insurancebilling.domain.PolicyTerm;
import com.insurancebilling.service.BillingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A policy term, its payment schedule and its ledger.
 *
 * <p>Payment lives here rather than on the installment, because money is received against a term and the
 * term decides what it settles. An endpoint that took an installment reference would invite a caller to
 * pay installment five while one to four are open, and the domain would then have to either obey — which
 * is wrong — or refuse a request the API had implied was reasonable.
 */
@RestController
@RequestMapping("/api/terms")
public class PolicyTermController {

  private final BillingService billing;

  public PolicyTermController(BillingService billing) {
    this.billing = billing;
  }

  @GetMapping("/{termReference}")
  public PolicyTermResponse get(@PathVariable String termReference) {
    return PolicyTermResponse.from(billing.findTerm(termReference));
  }

  @GetMapping("/{termReference}/schedule")
  public List<InstallmentResponse> schedule(@PathVariable String termReference) {
    return billing.findTerm(termReference).getInstallments().stream()
        .map(InstallmentResponse::from)
        .toList();
  }

  /**
   * The ledger, newest line first.
   *
   * <p>Ordered for reading rather than for arithmetic: each line carries the balance as it stood after
   * it, which the term derives from the lines below it in this view.
   */
  @GetMapping("/{termReference}/transactions")
  public List<BillingTransactionResponse> transactions(@PathVariable String termReference) {
    PolicyTerm term = billing.findTerm(termReference);
    List<BillingTransactionResponse> ledger =
        new java.util.ArrayList<>(
            term.getTransactions().stream()
                .map(transaction -> BillingTransactionResponse.from(transaction, term))
                .toList());
    java.util.Collections.reverse(ledger);
    return ledger;
  }

  @PostMapping("/{termReference}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public BillingTransactionResponse pay(
      @PathVariable String termReference, @Valid @RequestBody TermPaymentRequest request) {
    BillingTransaction posted =
        billing.payTerm(termReference, request.amount(), request.description());
    return BillingTransactionResponse.from(posted, posted.getPolicyTerm());
  }

  /**
   * Records that a bank refused a payment.
   *
   * <p>Addressed by the ledger line the payment posted, not by the term, because that line is what is
   * being reversed and naming it is what makes a second attempt refusable.
   */
  @PostMapping("/transactions/{transactionReference}/return")
  @ResponseStatus(HttpStatus.CREATED)
  public BillingTransactionResponse returnPayment(
      @PathVariable String transactionReference, @Valid @RequestBody ReturnPaymentRequest request) {
    BillingTransaction reversal = billing.returnPayment(transactionReference, request.reason());
    return BillingTransactionResponse.from(reversal, reversal.getPolicyTerm());
  }
}
