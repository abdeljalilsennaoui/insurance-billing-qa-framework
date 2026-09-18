package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.client.McpApiClient;
import com.insurancebilling.qa.api.model.PolicyTermDto;
import io.restassured.response.Response;
import java.util.List;
import org.testng.annotations.Test;

/**
 * The MCP surface, from outside the application.
 *
 * <p>The assertion that matters here is that <b>an MCP tool and the REST endpoint behind it publish the
 * same bytes</b>. Both are supposed to be the same response record serialised by the same mapper; if
 * they ever diverge, a machine consumer and a human reader are being told different things about the
 * same account, and the divergence would show up as a discrepancy nobody could explain from either
 * side alone.
 *
 * <p>These tests read only. Nothing here creates data, because the MCP surface cannot create data —
 * which is the other thing worth proving from outside.
 */
public class McpSurfaceApiIT extends BaseApiTest {

  private static final String SEEDED_ACCOUNT = "ACCT-100001";
  private static final String SEEDED_TERM = "SEED-TERM-001";

  private final McpApiClient mcp = new McpApiClient();

  @Test(groups = {"smoke", "regression"})
  public void theServerListsExactlyTheFourBillingTools() {
    assertThat(mcp.toolNames())
        .containsExactly("find_account", "find_term", "get_installment_schedule", "get_ledger");
  }

  @Test(groups = "regression")
  public void everyToolCarriesADescriptionAndASchemaThatNamesItsArgument() {
    Response tools = mcp.listTools();

    assertThat(tools.jsonPath().getList("result.tools.description", String.class))
        .as("a tool a model is asked to choose between with nothing to choose on")
        .allSatisfy(description -> assertThat(description).isNotBlank());
    assertThat(tools.jsonPath().getList("result.tools.inputSchema.required", List.class))
        .allSatisfy(required -> assertThat(required).hasSize(1));
    assertThat(
            tools.jsonPath().getList("result.tools.inputSchema.additionalProperties", Boolean.class))
        .as("a schema that permits unknown properties cannot be enforced")
        .containsOnly(false);
  }

  @Test(groups = {"smoke", "regression"})
  public void findAccountPublishesWhatTheAccountEndpointPublishes() {
    Response call = mcp.callTool("find_account", "accountReference", SEEDED_ACCOUNT);

    assertThat(mcp.isError(call)).isFalse();
    assertThat(mcp.textOf(call)).isEqualTo(billing.accountRaw(SEEDED_ACCOUNT).asString());
  }

  @Test(groups = "regression")
  public void findTermPublishesWhatTheTermEndpointPublishes() {
    Response call = mcp.callTool("find_term", "termReference", SEEDED_TERM);

    assertThat(mcp.textOf(call)).isEqualTo(billing.termRaw(SEEDED_TERM).asString());
  }

  @Test(groups = "regression")
  public void theScheduleToolPublishesWhatTheScheduleEndpointPublishes() {
    Response call = mcp.callTool("get_installment_schedule", "termReference", SEEDED_TERM);

    assertThat(mcp.textOf(call)).isEqualTo(billing.scheduleRaw(SEEDED_TERM).asString());
  }

  @Test(groups = "regression")
  public void theLedgerToolPublishesWhatTheLedgerEndpointPublishes() {
    Response call = mcp.callTool("get_ledger", "termReference", SEEDED_TERM);

    assertThat(mcp.textOf(call)).isEqualTo(billing.ledgerRaw(SEEDED_TERM).asString());
  }

  /**
   * A reference nobody can find is a tool result, not a protocol failure.
   *
   * <p>Over REST the same question is a 404. Over MCP it has to come back as a successful call whose
   * result is marked as an error, because the client asked a well-formed question and deserves to be
   * told the answer is "no such account" rather than "the server failed".
   */
  @Test(groups = {"regression", "negative"})
  public void anAccountThatDoesNotExistIsAnErrorResultRatherThanAProtocolError() {
    Response call = mcp.callTool("find_account", "accountReference", "ACCT-NOT-A-REAL-ACCOUNT");

    assertThat(call.statusCode()).isEqualTo(200);
    assertThat(call.jsonPath().getMap("error"))
        .as("a JSON-RPC error for a question that has an answer")
        .isNull();
    assertThat(mcp.isError(call)).isTrue();
    assertThat(mcp.textOf(call)).contains("was not found");
  }

  @Test(groups = {"regression", "negative"})
  public void aToolThatWouldMoveMoneyDoesNotExist() {
    Response call = mcp.callTool("pay_term", "termReference", SEEDED_TERM);

    assertThat(call.jsonPath().getString("error.message"))
        .as("the MCP surface offers something that moves money")
        .isNotNull();
    assertThat(mcp.toolNames()).doesNotContain("pay_term", "return_payment", "open_account");
  }

  /**
   * The payment endpoints are unchanged and still do the moving.
   *
   * <p>Worth stating rather than implying: the MCP surface is read-only because money goes through the
   * billing API, which applies the term's rules. A test that only proved the absence of a write tool
   * would leave open the possibility that nothing can write at all.
   */
  @Test(groups = "regression")
  public void moneyStillMovesThroughTheBillingApi() {
    PolicyTermDto term = testData.boundTerm();

    billing.pay(term.termReference(), "130.80");

    assertThat(billing.term(term.termReference()).balance()).isEqualByComparingTo("1460.80");
  }

  @Test(groups = {"regression", "negative"})
  public void aClientThatWillNotAcceptAnEventStreamIsRefused() {
    Response refused = mcp.rpcAcceptingJsonOnly("tools/list");

    assertThat(refused.statusCode())
        .as("the specification requires both content types; the server holds callers to it")
        .isEqualTo(400);
  }
}
