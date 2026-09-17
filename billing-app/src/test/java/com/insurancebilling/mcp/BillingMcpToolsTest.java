package com.insurancebilling.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancebilling.assistant.BillingReadTools;
import com.insurancebilling.assistant.BillingToolDefinition;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * What MCP publishes, and where it comes from.
 *
 * <p>The assertion this class exists for is {@code theToolsArePreciselyTheRegistrysTools}: it compares
 * names, descriptions and schemas against {@link BillingReadTools} rather than against a list written
 * here. A copy of the expected four tools in this file would pass just as happily on the day the MCP
 * surface and the assistant's surface stopped being the same thing, which is the failure worth
 * catching.
 */
@SpringBootTest
class BillingMcpToolsTest {

  private static final String SEEDED_ACCOUNT = "ACCT-100001";

  @Autowired private BillingMcpTools mcpTools;

  @Autowired private BillingReadTools registry;

  @Autowired private ObjectMapper json;

  @Test
  @DisplayName("the published tools are precisely the registry's tools, in its order")
  void theToolsArePreciselyTheRegistrysTools() {
    List<Tool> published = mcpTools.specifications().stream().map(SyncToolSpecification::tool).toList();

    assertThat(published)
        .as("the MCP surface and the assistant's surface have come apart")
        .hasSameSizeAs(registry.definitions());

    for (int index = 0; index < published.size(); index++) {
      BillingToolDefinition definition = registry.definitions().get(index);
      Tool tool = published.get(index);

      assertThat(tool.name()).isEqualTo(definition.name());
      assertThat(tool.description()).isEqualTo(definition.description());
      assertThat(tool.inputSchema()).isEqualTo(definition.inputSchema());
    }
  }

  @Test
  @DisplayName("a tool answers with the same JSON the registry produces for the same argument")
  void aToolAnswersWithTheRegistrysOwnJson() throws Exception {
    CallToolResult result = call("find_account", Map.of("accountReference", SEEDED_ACCOUNT));

    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
    assertThat(textOf(result))
        .isEqualTo(json.writeValueAsString(registry.invoke("find_account", SEEDED_ACCOUNT)));
  }

  @Test
  @DisplayName("every tool can be called, and none of them answers with an error")
  void everyToolCanBeCalled() {
    for (BillingToolDefinition definition : registry.definitions()) {
      String reference = "accountReference".equals(definition.argument()) ? SEEDED_ACCOUNT : "SEED-TERM-001";

      CallToolResult result = call(definition.name(), Map.of(definition.argument(), reference));

      assertThat(result.isError())
          .as("%s answered with an error for %s", definition.name(), reference)
          .isNotEqualTo(Boolean.TRUE);
      assertThat(textOf(result)).as("%s answered with nothing", definition.name()).isNotBlank();
    }
  }

  /**
   * A reference nobody can find is an answer, not a failure.
   *
   * <p>The distinction matters to the caller: a tool result marked {@code isError} says the platform
   * understood the question and there is no such account, where a thrown exception would reach the
   * client as a protocol failure indistinguishable from the server being broken.
   */
  @Test
  void anUnknownReferenceIsAnErrorResultRatherThanAnException() {
    CallToolResult result = call("find_account", Map.of("accountReference", "ACCT-NOT-A-REAL-ONE"));

    assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    assertThat(textOf(result)).contains("ACCT-NOT-A-REAL-ONE").contains("not found");
  }

  @Test
  void aBlankArgumentIsRefusedWithoutReachingTheDatabase() {
    CallToolResult result = call("find_term", Map.of("termReference", "   "));

    assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    assertThat(textOf(result)).contains("non-blank termReference");
  }

  private CallToolResult call(String tool, Map<String, Object> arguments) {
    SyncToolSpecification specification =
        mcpTools.specifications().stream()
            .filter(candidate -> candidate.tool().name().equals(tool))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No MCP tool named " + tool));

    return specification
        .callHandler()
        .apply(null, CallToolRequest.builder(tool).arguments(arguments).build());
  }

  private String textOf(CallToolResult result) {
    return result.content().stream()
        .filter(TextContent.class::isInstance)
        .map(TextContent.class::cast)
        .map(TextContent::text)
        .findFirst()
        .orElseThrow(() -> new AssertionError("The tool answered with no text content"));
  }
}
