package com.insurancebilling.soap;

import com.insurancebilling.domain.Invoice;
import com.insurancebilling.repository.InvoiceRepository;
import com.insurancebilling.soap.generated.GetInvoiceStatusRequest;
import com.insurancebilling.soap.generated.GetInvoiceStatusResponse;
import com.insurancebilling.soap.generated.InvoiceStatus;
import java.time.LocalDate;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * SOAP endpoint answering invoice status queries for partner systems.
 *
 * <p>Read-only by design. Partners can ask what an invoice owes; they cannot pay it here. Payment stays
 * on the REST endpoint where the billing rules and their specific rejection reasons live, rather than
 * being reimplemented behind a second protocol that could diverge from the first.
 *
 * <p>Reuses the same repository and the same derived figures as the REST API, so the two protocols can
 * never report different balances for the same invoice.
 */
@Endpoint
public class InvoiceStatusEndpoint {

  private static final String NAMESPACE = "http://insurancebilling.com/billing/invoice-status";

  private final InvoiceRepository invoices;

  public InvoiceStatusEndpoint(InvoiceRepository invoices) {
    this.invoices = invoices;
  }

  @PayloadRoot(namespace = NAMESPACE, localPart = "GetInvoiceStatusRequest")
  @ResponsePayload
  @Transactional(readOnly = true)
  public GetInvoiceStatusResponse getInvoiceStatus(@RequestPayload GetInvoiceStatusRequest request) {
    Invoice invoice =
        invoices
            .findByInvoiceNumber(request.getInvoiceNumber())
            .orElseThrow(() -> new InvoiceNotFoundFault(request.getInvoiceNumber()));

    GetInvoiceStatusResponse response = new GetInvoiceStatusResponse();
    response.setInvoiceNumber(invoice.getInvoiceNumber());
    response.setStatus(InvoiceStatus.fromValue(invoice.getStatus().name()));
    response.setTotalAmount(invoice.getTotalAmount());
    response.setAmountPaid(invoice.getAmountPaid());
    response.setOutstandingBalance(invoice.getOutstandingBalance());
    response.setOverdue(invoice.isOverdue(LocalDate.now()));
    response.setDueDate(toXmlDate(invoice.getDueDate()));
    return response;
  }

  /**
   * Converts a {@link LocalDate} to the {@link XMLGregorianCalendar} the generated class expects.
   *
   * <p>xjc maps {@code xs:date} to {@code XMLGregorianCalendar}. That could be changed with a JAXB
   * binding customisation mapping it to {@code java.time.LocalDate}, but that means another
   * configuration file and an adapter class to maintain. Converting at the single boundary where the
   * domain meets the generated contract is less machinery for the same result.
   *
   * <p>The time fields are left undefined deliberately: {@code xs:date} carries no time, and filling in
   * zeros would publish a precision the contract does not have.
   */
  private static XMLGregorianCalendar toXmlDate(LocalDate date) {
    try {
      return DatatypeFactory.newInstance()
          .newXMLGregorianCalendarDate(
              date.getYear(), date.getMonthValue(), date.getDayOfMonth(), DatatypeConstants.FIELD_UNDEFINED);
    } catch (DatatypeConfigurationException unavailable) {
      throw new IllegalStateException("No XML datatype factory available", unavailable);
    }
  }
}
