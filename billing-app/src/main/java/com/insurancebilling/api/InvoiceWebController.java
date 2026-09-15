package com.insurancebilling.api;

import com.insurancebilling.api.dto.InvoiceResponse;
import com.insurancebilling.api.dto.PaymentForm;
import com.insurancebilling.api.dto.PaymentRequest;
import com.insurancebilling.domain.InvoiceStatus;
import com.insurancebilling.domain.PaymentMethod;
import com.insurancebilling.domain.PaymentRejectedException;
import com.insurancebilling.service.InvoiceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import com.insurancebilling.service.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The invoice console: the server-rendered UI that the Selenium, Cucumber UI and Cypress suites drive.
 *
 * <p>Rendered with Thymeleaf rather than a client-side framework. That keeps the stack Java-first, gives
 * the UI suites a deterministic DOM with no hydration race to wait on, and removes a Node build from
 * CI. The accepted trade-off is that this project does not demonstrate SPA testing.
 *
 * <p>A successful payment redirects rather than rendering directly, so a browser refresh after paying
 * cannot resubmit the payment.
 *
 * <p>The console reads the business date from the same {@link Clock} as the REST API, so the two can
 * never disagree about whether an invoice is overdue.
 */
@Controller
@RequestMapping("/invoices")
public class InvoiceWebController {

  private final InvoiceService invoices;
  private final Clock clock;
  private final org.springframework.context.MessageSource messages;
  private final BillingFormats formats;

  public InvoiceWebController(
      InvoiceService invoices,
      Clock clock,
      org.springframework.context.MessageSource messages,
      BillingFormats formats) {
    this.invoices = invoices;
    this.clock = clock;
    this.messages = messages;
    this.formats = formats;
  }

  @GetMapping
  public String list(@RequestParam(required = false) InvoiceStatus status, Model model) {
    LocalDate today = LocalDate.now(clock);
    model.addAttribute(
        "invoices",
        invoices.findAll(status).stream().map(invoice -> InvoiceResponse.from(invoice, today)).toList());
    model.addAttribute("statuses", InvoiceStatus.values());
    model.addAttribute("selectedStatus", status);
    return "invoices/list";
  }

  @GetMapping("/{id}")
  public String detail(@PathVariable Long id, Model model) {
    return renderDetail(id, new PaymentForm(), model);
  }

  @PostMapping("/{id}/payments")
  public String pay(
      @PathVariable Long id,
      @ModelAttribute PaymentForm form,
      Model model,
      RedirectAttributes redirectAttributes) {

    java.util.Locale locale = org.springframework.context.i18n.LocaleContextHolder.getLocale();

    BigDecimal amount;
    try {
      amount = parseAmount(form.getAmount());
    } catch (AmountNotUnderstoodException invalidInput) {
      model.addAttribute("paymentError", messages.getMessage(invalidInput.key(), null, locale));
      return renderDetail(id, form, model);
    }

    try {
      PaymentMethod method = form.getMethod() == null ? PaymentMethod.CARD : form.getMethod();
      invoices.pay(id, new PaymentRequest(amount, method, form.getReference()));
    } catch (PaymentRejectedException rejected) {
      model.addAttribute("paymentError", refusalMessage(id, rejected, amount, locale));
      return renderDetail(id, form, model);
    }

    redirectAttributes.addFlashAttribute(
        "paymentSuccess",
        messages.getMessage(
            "payment.success", new Object[] {formats.money(amount, locale)}, locale));
    return "redirect:/invoices/" + id;
  }

  /**
   * Explains a refusal in the reader's language.
   *
   * <p>The domain's own message stays as it is and keeps reaching the API, where it is read by an
   * engineer debugging a call. The console needs a different sentence for a different audience, so it
   * resolves one from the rejection's reason rather than showing the developer's.
   *
   * <p>Falling back to the domain's message for a reason with no key means a new rejection reason shows
   * something useful rather than {@code ??rejection.X??} - and {@code MessageBundleParityTest} is what
   * makes sure that fallback is never actually needed.
   */
  private String refusalMessage(
      Long invoiceId, PaymentRejectedException rejected, BigDecimal amount, java.util.Locale locale) {
    Object[] args = {
      formats.money(amount, locale),
      formats.money(invoices.findById(invoiceId).getOutstandingBalance(), locale)
    };
    return messages.getMessage(
        "rejection." + rejected.getReason().name(), args, rejected.getMessage(), locale);
  }

  /** Thrown when the amount box holds something this console cannot read as money. */
  private static final class AmountNotUnderstoodException extends IllegalArgumentException {

    private final transient String key;

    private AmountNotUnderstoodException(String key) {
      super(key);
      this.key = key;
    }

    String key() {
      return key;
    }
  }

  /**
   * Parses the amount the user typed.
   *
   * <p>Each failure gets its own message key so the console tells the user what to correct, and so the
   * UI suite can assert on a specific message rather than on the presence of any error at all. A key
   * rather than a sentence, because the sentence depends on who is reading.
   */
  private BigDecimal parseAmount(String rawAmount) {
    if (rawAmount == null || rawAmount.isBlank()) {
      throw new AmountNotUnderstoodException("payment.error.blank");
    }
    try {
      return new BigDecimal(rawAmount.trim());
    } catch (NumberFormatException notANumber) {
      throw new AmountNotUnderstoodException("payment.error.notANumber");
    }
  }

  /**
   * Renders a not-found page for the console.
   *
   * <p>Declared locally because the shared {@code @RestControllerAdvice} answers with JSON, which is the
   * right response for the API and the wrong one for a browser.
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String invoiceNotFound(ResourceNotFoundException exception, Model model) {
    model.addAttribute("message", exception.getMessage());
    return "invoices/not-found";
  }

  private String renderDetail(Long id, PaymentForm form, Model model) {
    model.addAttribute("invoice", InvoiceResponse.from(invoices.findById(id), LocalDate.now(clock)));
    model.addAttribute("paymentForm", form);
    model.addAttribute("methods", PaymentMethod.values());
    return "invoices/detail";
  }
}
