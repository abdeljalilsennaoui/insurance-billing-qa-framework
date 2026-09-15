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
 *
 * <p>The cost of that scoping is that a new screen has to be added to the list below, and nothing
 * fails loudly when it is not: the language switch simply renders with no destination on that one
 * page. The agent console was added here only after a test went looking for the link. Any further
 * page that renders {@code fragments/header} belongs in this list too.
 */
@ControllerAdvice(
    assignableTypes = {
      InvoiceWebController.class,
      BillingAccountWebController.class,
      AgentConsoleWebController.class
    })
public class WebPageModelAdvice {

  /**
   * The page the reader is on, query string and all, with any existing {@code lang} removed.
   *
   * <p>The query string is the part that is easy to forget and the part that matters. A switch that
   * kept only the path would drop the reader from the schedule tab back to the summary, and from a
   * filtered invoice list back to the unfiltered one - changing what they were looking at, not just the
   * language it was written in.
   *
   * <p>{@code lang} itself is stripped so the link builder can add the new one without the old value
   * surviving alongside it.
   */
  @ModelAttribute("currentPath")
  public String currentPath(HttpServletRequest request) {
    String query = request.getQueryString();
    if (query == null || query.isBlank()) {
      return request.getRequestURI();
    }
    String withoutLanguage =
        java.util.Arrays.stream(query.split("&"))
            .filter(parameter -> !parameter.startsWith("lang="))
            .collect(java.util.stream.Collectors.joining("&"));
    return withoutLanguage.isEmpty()
        ? request.getRequestURI()
        : request.getRequestURI() + "?" + withoutLanguage;
  }
}
