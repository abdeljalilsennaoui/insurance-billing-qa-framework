package com.insurancebilling.api;

import com.insurancebilling.api.dto.InvoiceRequest;
import com.insurancebilling.api.dto.InvoiceResponse;
import com.insurancebilling.api.dto.PaymentRequest;
import com.insurancebilling.api.dto.PaymentResponse;
import com.insurancebilling.domain.InvoiceStatus;
import com.insurancebilling.service.InvoiceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

  private final InvoiceService invoices;

  public InvoiceController(InvoiceService invoices) {
    this.invoices = invoices;
  }

  @GetMapping
  public List<InvoiceResponse> list(@RequestParam(required = false) InvoiceStatus status) {
    LocalDate today = LocalDate.now();
    return invoices.findAll(status).stream()
        .map(invoice -> InvoiceResponse.from(invoice, today))
        .toList();
  }

  @GetMapping("/{id}")
  public InvoiceResponse get(@PathVariable Long id) {
    return InvoiceResponse.from(invoices.findById(id), LocalDate.now());
  }

  @PostMapping
  public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody InvoiceRequest request) {
    InvoiceResponse created = InvoiceResponse.from(invoices.create(request), LocalDate.now());
    return ResponseEntity.created(URI.create("/api/invoices/" + created.id())).body(created);
  }

  /**
   * Applies a payment to an invoice.
   *
   * <p>Returns 201 with the recorded payment on success. A malformed body is 400; a well-formed request
   * that a billing rule refuses is 422 carrying the rejection reason.
   */
  @PostMapping("/{id}/payments")
  @ResponseStatus(HttpStatus.CREATED)
  public PaymentResponse pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request) {
    return PaymentResponse.from(invoices.pay(id, request));
  }

  @GetMapping("/{id}/payments")
  public List<PaymentResponse> payments(@PathVariable Long id) {
    return invoices.findPayments(id).stream().map(PaymentResponse::from).toList();
  }

  /** Cancels an invoice, so suites can verify that a cancelled invoice refuses payment. */
  @PostMapping("/{id}/cancellation")
  public InvoiceResponse cancel(@PathVariable Long id) {
    return InvoiceResponse.from(invoices.cancel(id), LocalDate.now());
  }
}
