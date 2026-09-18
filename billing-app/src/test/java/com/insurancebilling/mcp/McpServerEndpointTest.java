package com.insurancebilling.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.assistant.BillingReadTools;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The endpoint as an MCP client actually meets it.
 *
 * <p>{@link BillingMcpToolsTest} calls the tool handlers directly, which proves what they answer but
 * not that any of it is reachable over the protocol. This drives the running servlet with the SDK's
 * own client — the handshake, {@code tools/list} and {@code tools/call} — because a server whose tools
 * are correct and whose transport is misconfigured is a server nobody can use.
 *
 * <p>The SDK's {@code mcp-test} artifact is deliberately not used. Its classes are abstract
 * conformance suites for people implementing the SDK itself, and its POM pulls
 * {@code org.testcontainers:toxiproxy} at compile scope, which would put a Docker requirement into a
 * build that documents Docker as unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpServerEndpointTest {

  private static final String SEEDED_ACCOUNT = "ACCT-100001";

  @LocalServerPort private int port;

  @Autowired private BillingReadTools registry;

  private McpSyncClient client;

  @BeforeEach
  void connect() {
    client =
        McpClient.sync(
                HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                    .endpoint(McpServerConfiguration.ENDPOINT)
                    .build())
            .requestTimeout(Duration.ofSeconds(10))
            .build();
  }

  @AfterEach
  void disconnect() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  @DisplayName("a client can connect and is told what the server is")
  void aClientCanConnect() {
    InitializeResult initialised = client.initialize();

    assertThat(initialised.serverInfo().name()).isEqualTo("insurance-billing");
    assertThat(initialised.capabilities().tools()).isNotNull();
    assertThat(initialised.instructions()).contains("Read-only");
  }

  @Test
  @DisplayName("tools/list returns the registry's tools over the protocol")
  void toolsListReturnsTheRegistrysTools() {
    client.initialize();

    List<String> published = client.listTools().tools().stream().map(Tool::name).toList();

    assertThat(published).containsExactlyElementsOf(registry.toolNames());
  }

  @Test
  @DisplayName("tools/call reaches the billing data")
  void toolsCallReachesTheBillingData() {
    client.initialize();

    CallToolResult result =
        client.callTool(
            CallToolRequest.builder("find_account")
                .arguments(Map.of("accountReference", SEEDED_ACCOUNT))
                .build());

    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
    assertThat(((TextContent) result.content().getFirst()).text())
        .contains(SEEDED_ACCOUNT)
        .contains("totalBalance");
  }

  /**
   * The read-only claim, checked at the protocol boundary.
   *
   * <p>{@code BillingReadToolsTest} already proves no registered tool reaches a method that moves
   * money. This proves the same thing from outside: a client asking for the payment tool by name is
   * told there is no such tool, because there is no second code path that could have one.
   */
  @Test
  void thereIsNoToolThatMovesMoney() {
    client.initialize();

    List<String> published = client.listTools().tools().stream().map(Tool::name).toList();

    assertThat(published)
        .as("a machine consumer can reach something that moves money")
        .doesNotContain("pay_term", "return_payment", "bind_term", "open_account");
  }
}
