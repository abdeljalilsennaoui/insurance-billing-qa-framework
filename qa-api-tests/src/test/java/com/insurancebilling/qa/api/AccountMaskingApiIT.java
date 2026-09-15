package com.insurancebilling.qa.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.insurancebilling.qa.api.model.BillingAccountDto;
import io.restassured.response.Response;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

/**
 * What the API will and will not say about a policyholder's bank account.
 *
 * <p>This suite is black box on purpose. The unit tests prove the domain has nowhere to put a whole
 * account number; these prove that nothing between the domain and the wire put one back — a serializer,
 * a new field on a response, a debug endpoint. Neither claim is worth much without the other.
 */
public class AccountMaskingApiIT extends BaseApiTest {

  private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\"accountNumber\":\"([^\"]*)\"");

  @Test(groups = {"smoke", "regression"})
  public void anAccountReportsItsBankDetailsMasked() {
    BillingAccountDto account = testData.account();

    BillingAccountDto fetched = billing.account(account.accountReference());

    assertThat(fetched.paymentInformation().accountNumber()).isEqualTo("****742");
    assertThat(fetched.paymentInformation().institutionNumber()).isEqualTo("***");
    assertThat(fetched.paymentInformation().branchNumber()).isEqualTo("*****");
    assertThat(fetched.paymentInformation().paymentMethod()).isEqualTo("PRE_AUTHORIZED_DEBIT");
  }

  @Test(groups = "regression")
  public void noAccountNumberOnTheWireIsAnythingButFourStarsAndThreeDigits() {
    testData.account();

    String body =
        io.restassured.RestAssured.given()
            .spec(com.insurancebilling.qa.api.config.ApiSpecs.request())
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(200)
            .extract()
            .asString();

    Matcher matcher = ACCOUNT_NUMBER.matcher(body);
    int checked = 0;
    while (matcher.find()) {
      checked++;
      assertThat(matcher.group(1))
          .as("an account number reaching a caller must be masked to its last three digits")
          .matches("\\*{4}\\d{3}");
    }
    assertThat(checked).as("the listing returned no account numbers to check").isPositive();
  }

  @Test(groups = {"negative", "regression"})
  public void theApiRefusesToBeGivenAWholeAccountNumber() {
    Response response =
        billing.openAccountRaw(
            Map.of(
                "customerId", testData.customer().id(),
                "paymentPlan", "MONTHLY",
                "paymentMethod", "PRE_AUTHORIZED_DEBIT",
                "accountHolder", "QA Tester",
                "accountLastDigits", "4829471203742"));

    assertThat(response.statusCode())
        .as("accepting and truncating would hide the caller's mistake instead of surfacing it")
        .isEqualTo(400);
    assertThat(response.jsonPath().getString("code")).isEqualTo("VALIDATION_FAILED");
    assertThat(response.jsonPath().getString("fieldErrors.accountLastDigits"))
        .contains("never a whole account number");
  }

  @Test(groups = {"negative", "regression"})
  public void openingAnAccountForAnUnknownCustomerIsNotFound() {
    Response response =
        billing.openAccountRaw(
            Map.of(
                "customerId", 999_999_999L,
                "paymentPlan", "MONTHLY",
                "paymentMethod", "CARD"));

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.jsonPath().getString("code")).isEqualTo("NOT_FOUND");
  }

  @Test(groups = {"negative", "regression"})
  public void anUnknownAccountIsNotFoundRatherThanAnEmptySummary() {
    Response response = billing.accountRaw("ACCT-DOES-NOT-EXIST");

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.jsonPath().getString("code")).isEqualTo("NOT_FOUND");
  }
}
