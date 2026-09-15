package com.insurancebilling.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies what every rendered page needs and no controller should have to remember.
 *
 * <p>Currently one thing: the path the reader is on, so the language switch can send them back to the
 * same page in the other language rather than to the invoice list. A switch that loses your place is
 * worse than no switch.
 *
 * <p>Scoped to the controllers that render HTML. A {@code @ControllerAdvice} with no {@code
 * assignableTypes} would also run for every JSON endpoint, which would be harmless and pointless.
 */
@ControllerAdvice(assignableTypes = InvoiceWebController.class)
public class WebPageModelAdvice {

  @ModelAttribute("currentPath")
  public String currentPath(HttpServletRequest request) {
    return request.getRequestURI();
  }
}
