package com.insurancebilling.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurancebilling.assistant.BillingReadTools;
import com.insurancebilling.assistant.BillingToolDefinition;
import com.insurancebilling.service.ResourceNotFoundException;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The MCP tools, built from the same registry the assistant reads through.
 *
 * <p>There is no tool definition in this class. Every name, description and argument schema comes
 * from {@link BillingReadTools}, so adding a tool to the registry surfaces it over MCP without anyone
 * editing this file — and, more to the point, nobody can widen what MCP exposes without widening what
 * the assistant exposes at the same time, in front of the deny-list test that guards it.
 *
 * <p><b>Results are text, not structured content.</b> MCP's {@code structuredContent} has to be a JSON
 * object, and two of the four tools answer with an array — a schedule and a ledger. Wrapping those in
 * an object to satisfy the field would invent a shape the REST API does not publish, and the whole
 * point of reading through the registry is that a machine consumer sees exactly what the API returns.
 * The text is the response DTO serialised by the application's own {@link ObjectMapper}, which is the
 * one the REST layer uses, so the bytes match rather than merely resembling each other.
 */
@Component
public class BillingMcpTools {

  private final BillingReadTools tools;
  private final ObjectMapper json;

  public BillingMcpTools(BillingReadTools tools, ObjectMapper json) {
    this.tools = tools;
    this.json = json;
  }

  /** One specification per registered tool, in the registry's own order. */
  public List<SyncToolSpecification> specifications() {
    return tools.definitions().stream().map(this::specificationFor).toList();
  }

  private SyncToolSpecification specificationFor(BillingToolDefinition definition) {
    // builder(name, inputSchema) rather than the no-argument builder: the latter is deprecated in
    // 2.0.1, because a tool without a schema is only discovered to be one at call time.
    Tool tool =
        Tool.builder(definition.name(), definition.inputSchema())
            .description(definition.description())
            .build();

    return SyncToolSpecification.builder()
        .tool(tool)
        .callHandler((context, request) -> call(definition, request))
        .build();
  }

  /**
   * Runs one tool call.
   *
   * <p>A reference nobody can find, and an argument that is missing or blank, come back as a tool
   * result marked {@code isError} rather than as a protocol error. That is the distinction MCP draws:
   * the protocol worked, the call was understood, and the answer is that there is no such account.
   * A client that got a JSON-RPC error instead would have no way to tell the two apart, and a model
   * reading it could not report the difference to the person who asked.
   */
  private CallToolResult call(BillingToolDefinition definition, CallToolRequest request) {
    Object argument = request.arguments().get(definition.argument());
    try {
      Object result = tools.invoke(definition.name(), argument == null ? null : argument.toString());
      return CallToolResult.builder().addTextContent(serialise(result)).build();
    } catch (ResourceNotFoundException | IllegalArgumentException refused) {
      return CallToolResult.builder().isError(true).addTextContent(refused.getMessage()).build();
    }
  }

  private String serialise(Object result) {
    try {
      return json.writeValueAsString(result);
    } catch (JsonProcessingException unserialisable) {
      // The registry returns the same response records the REST API serialises on every request, so
      // reaching here means the application could not have answered over HTTP either.
      throw new IllegalStateException(
          "A billing response could not be serialised for MCP: " + unserialisable.getOriginalMessage(),
          unserialisable);
    }
  }
}
