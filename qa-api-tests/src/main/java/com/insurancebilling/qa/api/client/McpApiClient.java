package com.insurancebilling.qa.api.client;

import static io.restassured.RestAssured.given;

import com.insurancebilling.qa.api.config.ApiSpecs;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The platform's MCP surface, driven as JSON-RPC over HTTP.
 *
 * <p>Deliberately not the MCP SDK's client. This suite is the black-box one: it checks what the
 * platform puts on the wire, and a test written against the same library the server is built from
 * would agree with it about a malformed message for the same reason twice. Plain requests here mean
 * the assertions hold for any client, and that the {@code curl} in the README is the request the suite
 * makes.
 *
 * <p>The {@code Accept} header carries {@code text/event-stream} beside {@code application/json}
 * because the MCP specification requires a client to accept both, and the server refuses a request
 * that does not with 400 — which is itself worth a test rather than a workaround.
 */
public class McpApiClient {

  public static final String PATH = "/mcp";

  private static final String ACCEPT = "application/json, text/event-stream";

  /** Request ids only have to be unique per client, and a test reading a reply checks it matches. */
  private final AtomicInteger nextId = new AtomicInteger(1);

  /** A JSON-RPC call, exactly as sent. The response is returned raw, errors included. */
  public Response rpc(String method, Map<String, Object> params) {
    Map<String, Object> body =
        params == null
            ? Map.of("jsonrpc", "2.0", "id", nextId.getAndIncrement(), "method", method)
            : Map.of(
                "jsonrpc",
                "2.0",
                "id",
                nextId.getAndIncrement(),
                "method",
                method,
                "params",
                params);

    return given().spec(ApiSpecs.request()).accept(ACCEPT).body(body).when().post(PATH);
  }

  /** The same call without the event-stream part of the Accept header. */
  public Response rpcAcceptingJsonOnly(String method) {
    return given()
        .spec(ApiSpecs.request())
        .body(Map.of("jsonrpc", "2.0", "id", nextId.getAndIncrement(), "method", method))
        .when()
        .post(PATH);
  }

  public Response listTools() {
    return rpc("tools/list", null);
  }

  /** Every published tool name, in the order the server lists them. */
  public List<String> toolNames() {
    return listTools().jsonPath().getList("result.tools.name", String.class);
  }

  public Response callTool(String tool, String argumentName, String argumentValue) {
    return rpc("tools/call", Map.of("name", tool, "arguments", Map.of(argumentName, argumentValue)));
  }

  /** The text a tool answered with: for these tools, the same JSON the REST API publishes. */
  public String textOf(Response response) {
    return response.jsonPath().getString("result.content[0].text");
  }

  public boolean isError(Response response) {
    return Boolean.TRUE.equals(response.jsonPath().getBoolean("result.isError"));
  }
}
