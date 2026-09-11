package com.insurancebilling.soap;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Exposes the SOAP endpoint and publishes its WSDL.
 *
 * <p>The WSDL is generated at runtime from the same XSD the request and response classes are generated
 * from, so there is exactly one contract and no committed WSDL file that could fall out of step with it.
 * A consumer fetches it from {@code /ws/invoiceStatus.wsdl}.
 *
 * <p>SOAP is mounted under {@code /ws/*} so it cannot collide with the REST API or the console. The
 * servlet needs {@code setTransformWsdlLocations(true)} for the published WSDL to advertise the host it
 * was actually fetched from; without it the WSDL hands consumers a hard-coded localhost address, which
 * works in development and breaks everywhere else.
 */
@EnableWs
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

  @Bean
  public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
      ApplicationContext applicationContext) {
    MessageDispatcherServlet servlet = new MessageDispatcherServlet();
    servlet.setApplicationContext(applicationContext);
    servlet.setTransformWsdlLocations(true);
    return new ServletRegistrationBean<>(servlet, "/ws/*");
  }

  @Bean(name = "invoiceStatus")
  public DefaultWsdl11Definition invoiceStatusWsdl(XsdSchema invoiceStatusSchema) {
    DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
    definition.setPortTypeName("InvoiceStatusPort");
    definition.setLocationUri("/ws");
    definition.setTargetNamespace("http://insurancebilling.com/billing/invoice-status");
    definition.setSchema(invoiceStatusSchema);
    return definition;
  }

  @Bean
  public XsdSchema invoiceStatusSchema() {
    return new SimpleXsdSchema(new ClassPathResource("xsd/invoice-status.xsd"));
  }
}
