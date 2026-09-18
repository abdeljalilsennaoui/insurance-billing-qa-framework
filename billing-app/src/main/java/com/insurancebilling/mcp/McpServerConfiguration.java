package com.insurancebilling.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mounts the billing platform's read-only surface at {@code /mcp}.
 *
 * <p>The transport is a plain {@code HttpServlet} from {@code mcp-core}, registered on the servlet
 * container the application already runs. No Spring AI: {@code spring-ai-starter-mcp-server-webmvc}
 * 2.0.1 declares {@code spring-boot-starter-web} 4.1.1, so adopting it would drag this application
 * from Spring Boot 3.5 to 4.1 — recorded in {@code docs/design-rationale.md} along with the rest of
 * that decision.
 *
 * <p><b>Stateless rather than session-based.</b> Of the three servlet transports the SDK ships, this
 * one answers a single JSON-RPC POST without a session to open first. Every tool here is a read
 * against data the caller names in the call, so there is no per-client state for a session to hold,
 * and the surface stays reachable with one {@code curl} — which is the difference between a reviewer
 * being able to check the claim in this README and having to take it on trust.
 *
 * <p><b>No authentication, deliberately, and it is not a gap left unnoticed.</b> This is a QA
 * portfolio application with seeded data, whose REST API is equally open and whose reset endpoint is
 * switched off by default for the same conversation. A real deployment would put this endpoint behind
 * the same authentication as the API and scope the tools to the caller's own accounts;
 * {@code docs/design-rationale.md} says so rather than leaving a reader to assume it was forgotten.
 */
@Configuration
public class McpServerConfiguration {

  /** Where the server is mounted. The transport and the servlet mapping must agree on it. */
  public static final String ENDPOINT = "/mcp";

  private static final String SERVER_NAME = "insurance-billing";

  /**
   * The transport, sharing the application's own {@link ObjectMapper}.
   *
   * <p>That is what makes the JSON a machine consumer reads over MCP identical to the JSON it would
   * read over REST — same date format, same money scale, same property names — rather than a second
   * rendering of the same objects that agrees until one of the two is configured differently.
   */
  @Bean
  HttpServletStatelessServerTransport mcpTransport(ObjectMapper objectMapper) {
    return HttpServletStatelessServerTransport.builder()
        .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
        .messageEndpoint(ENDPOINT)
        .build();
  }

  @Bean
  ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(
      HttpServletStatelessServerTransport transport) {
    ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
        new ServletRegistrationBean<>(transport, ENDPOINT);
    registration.setName("mcp");
    return registration;
  }

  /**
   * The server itself, exposing exactly what {@link BillingMcpTools} builds from the registry.
   *
   * <p>{@code tools(false)} is the {@code listChanged} capability: this tool list is fixed at startup,
   * so a client that subscribed to changes would be waiting for a notification that cannot come.
   */
  @Bean
  McpStatelessSyncServer mcpServer(
      HttpServletStatelessServerTransport transport, BillingMcpTools tools) {
    return McpServer.sync(transport)
        .serverInfo(SERVER_NAME, version())
        .capabilities(ServerCapabilities.builder().tools(false).build())
        .instructions(
            "Read-only access to the billing platform: accounts, policy terms, installment "
                + "schedules and ledgers. Quote the figures these tools return rather than "
                + "recalculating them. Payments are not made here; they go through the billing API, "
                + "which applies the term's rules.")
        .tools(tools.specifications())
        .build();
  }

  /**
   * The version reported to clients, read from the jar manifest.
   *
   * <p>Answering "dev" when running from compiled classes rather than a jar is the honest answer:
   * there is no release version to report, and a hard-coded number here would be a second copy of the
   * project's version that nothing keeps current.
   */
  private static String version() {
    String implementationVersion = McpServerConfiguration.class.getPackage().getImplementationVersion();
    return implementationVersion != null ? implementationVersion : "dev";
  }
}
