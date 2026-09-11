package com.insurancebilling.soap;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * Returned when a consumer asks about an invoice that does not exist.
 *
 * <p>A SOAP fault rather than a success response with empty fields. An empty response would force every
 * consumer to invent its own way of telling "this invoice is unpaid with a zero balance" apart from
 * "this invoice does not exist", and some of them would get it wrong.
 *
 * <p>{@code CLIENT} is the correct fault code: the request was understood and the caller asked about
 * something that is not there, which is not a server failure and should not prompt a retry.
 */
@SoapFault(faultCode = FaultCode.CLIENT)
public class InvoiceNotFoundFault extends RuntimeException {

  public InvoiceNotFoundFault(String invoiceNumber) {
    super("No invoice exists with number " + invoiceNumber);
  }
}
