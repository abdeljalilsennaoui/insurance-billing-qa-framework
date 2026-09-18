# Design rationale

A walkthrough of how this framework is built and why, by file path. Every claim below points at real
code — open the files while reading, because the point is that the design is legible in the source
rather than only in a document.

Where a decision has a cost, the cost is stated. Where a first attempt was wrong, the wrong version and
the reason it failed are kept, because that is usually the more useful half.

---

## 1. The browser automation

**Flow:** `BaseUiTest` → `DriverFactory` → page object → `BasePage` → the browser.

**The files, in the order the flow touches them:**

1. `qa-ui-tests/src/test/java/com/insurancebilling/qa/ui/BaseUiTest.java`
   `@BeforeMethod` calls `DriverFactory.startDriver()`; `@AfterMethod(alwaysRun = true)` quits it. It
   also holds `BillingTestData` and `InvoiceApiClient`, so a test builds its fixtures over HTTP rather
   than by driving forms.

2. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/driver/DriverFactory.java`
   Holds the driver in a `ThreadLocal`. `startDriver()` builds `ChromeOptions` (headless by default,
   `--no-sandbox`, `--disable-dev-shm-usage`), fixes the window size, sets a page-load timeout.
   `quitDriver()` calls `quit()` **and** `DRIVER.remove()`.

3. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/config/UiConfig.java`
   Reads `app.base.url`, `ui.headless`, `ui.wait.seconds`, the screenshot directory and the window size
   from system properties, with working defaults.

4. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/pages/BasePage.java`
   Builds locators with `testId(String)` → `By.cssSelector("[data-testid='...']")`. Exposes `visible`,
   `click`, `type`, `selectOption`, `isPresent`, all routed through `WebDriverWait`.

5. `InvoiceListPage.java` and `InvoiceDetailsPage.java`
   Page methods return values or the next page object, and contain no assertions — so the same page
   object serves a test expecting success and a test expecting refusal.

6. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/support/ScreenshotListener.java`
   A TestNG `ITestListener` that writes a PNG on failure, registered with `@Listeners` on `BaseUiTest`.

**Four decisions worth stating:**

- **`ThreadLocal`, and `remove()` as well as `quit()`.** TestNG reuses threads across tests; a stale
  entry would hand a dead session to the next test on that thread.
- **No implicit wait anywhere.** Mixing implicit and explicit waits produces timeouts that belong to
  neither and cannot be reasoned about.
- **No `Thread.sleep` in the module.** `grep -rn "Thread.sleep" qa-ui-tests/` returns exactly one hit,
  and it is the Javadoc in `BasePage` saying that it appears nowhere.
- **Selenium Manager resolves chromedriver**, so no driver binary is committed and no pinned version
  drifts away from the installed Chrome.

### Waiting for a navigation, and two wrong answers before the right one

This is the most instructive part of the module, because the first two implementations were wrong in
different ways.

**Attempt one — wait for a condition on the destination.** "A banner is present, or the outstanding
balance is displayed." Both are true of the page *already on screen*, because the balance is rendered
there too. The wait returned immediately, the test read pre-submit values, and the next interaction hit
a document mid-replacement. Written up as DEF-006.

**Attempt two — `ExpectedConditions.stalenessOf`.** Hold an element from the submitted page and wait for
it to go stale, since an element goes stale only once the browser has discarded the document containing
it. Correct in principle, and it failed on a CI runner: when a document is discarded mid-navigation,
ChromeDriver may raise a CDP-level `WebDriverException: Node with given id does not belong to the
document` instead of the `StaleElementReferenceException` that `stalenessOf` catches. It passed
consistently on a developer machine and failed on the runner — a timing and Chrome-version dependent
race. Written up as DEF-011.

**What is there now — ask the document about itself.** `BasePage.markCurrentDocument()` sets a marker on
`window` immediately before the action that navigates; `waitForNewDocument()` waits until that marker is
gone *and* `document.readyState === 'complete'`. A full page load creates a fresh `window`, so the
marker's absence proves the document was replaced rather than re-rendered, and nothing ever touches an
element reference that may already be dead. It holds for both outcomes, since a successful payment
redirects and a refused one re-renders.

What was deliberately not done at any point: add a sleep, or a retry. Both hide a race instead of
removing it.

---

## 2. The API framework

**The files:**

- `qa-api-tests/src/main/java/.../config/ApiSpecs.java` — the single `RequestSpecification`: base URI,
  content type, Jackson configuration, and `enableLoggingOfRequestAndResponseIfValidationFails`.
- `.../config/TestEnvironment.java` — the target, from `app.base.url`, defaulted.
- `.../client/` — `InvoiceApiClient`, `CustomerApiClient`, `PolicyApiClient`: typed operations plus raw
  `Response` variants.
- `.../model/` — `InvoiceDto`, `PaymentDto`, `ApiError` and friends, as records.
- `.../data/BillingTestData.java` — per-scenario fixtures.
- `qa-api-tests/src/test/java/.../BaseApiTest.java` — one health probe before the suite.

**Three decisions:**

**Logging only on failure.** Always-on logging makes a green run unreadable, and a failure that prints
nothing is undiagnosable from a CI log. REST Assured is configured to dump request and response exactly
when an assertion fails.

**Two method shapes per operation.** `pay()` asserts 201 and returns a `PaymentDto`; `payRaw()` returns
the `Response`. A negative test needs to inspect an error body; a happy-path test should not repeat the
extraction.

**The module does not depend on `billing-app`.** `InvoiceDto.status()` is a `String`, not the
application's `InvoiceStatus` enum. If both sides shared that enum, renaming a constant would change the
test and the application together, and the suite would stay green while the published contract broke.
The cost is no compile-time safety on status strings — a typo fails at runtime instead of at compile
time. Worth it.

---

## 3. Test isolation

**File:** `qa-api-tests/src/main/java/.../data/BillingTestData.java`

Every fixture method — `unpaidInvoice`, `partiallyPaidInvoice`, `settledInvoice`, `cancelledInvoice`,
`overdueInvoice`, `invoiceOnInactivePolicy` — creates a **new** customer, policy and invoice through the
public API. Emails are UUID-suffixed, because the application enforces a unique-email constraint and a
fixed address would make the second run fail with a 409.

Nothing in the mutating suites reads or changes the seeded `SEED-` records. Those exist for read-only
scenarios and the Cypress smoke suite.

**The check that proves it.** Running every suite twice against one running application, without
restarting it in between. A suite that only passes against a freshly started application is hiding
shared-state coupling — and the failure surfaces later, in a different test, as apparent flakiness.

Measured on 2026-09-14, against one instance started once:

| Suite | Pass 1 | Pass 2 |
|---|---|---|
| API (REST Assured) | 52 / 52 | 52 / 52 |
| UI (Selenium) | 18 / 18 | 18 / 18 |
| BDD (Cucumber) | 20 / 20 | 20 / 20 |
| Smoke (Cypress) | 7 / 7 | 7 / 7 |

97 black-box tests twice over, no restart and no reset between the passes, nothing skipped. That is also
what makes Failsafe's `parallel=methods` with four threads safe.

**Why resets are per-run, not per-test.** `TestSupportController.reset()` exists, but per-test resets
would serialise the whole suite and make parallel execution impossible. The rule is the other one: a
test that mutates data owns that data.

---

## 4. Why a zero payment is 422 and not 400

**Files:** `api/GlobalExceptionHandler.java`, `api/dto/PaymentRequest.java`, `domain/Invoice.java`

`PaymentRequest` validates only **presence** — `@NotNull` on amount and method. It has no `@Positive`.
Whether the amount is positive, correctly scaled, or within the outstanding balance is a **billing
rule**, enforced in `Invoice.applyPayment` and surfaced as `PaymentRejectedException` → 422 with the
reason in `code`.

**Why it matters:** if a zero amount returned 400 the way a missing field does, a test could not tell
"the client sent nonsense" from "the platform applied a billing rule", and a regression that turned one
into the other would pass unnoticed. The split is what makes
`PaymentValidationApiIT.malformedBodiesAreRejectedAsBadRequestsNotRuleViolations` meaningful: it pins
that unparseable input is 400 and **never** 422.

**The full scheme:** 400 not understood · 404 absent · 409 conflicts with existing state · 422
understood and refused by a rule.

---

## 5. Why the billing rules live on the entity

**File:** `domain/Invoice.java`, method `applyPayment`

1. **They cannot be bypassed.** A guard in a service is bypassed by any other caller that reaches the
   entity. A guard inside `applyPayment` is not.
2. **They are testable without Spring.** `InvoicePaymentRulesTest` runs as plain JUnit — no context, no
   database, milliseconds.

`InvoiceService.pay()` does lookup and transaction management, then delegates.

**The trade-off:** the entity is larger than an anaemic model. Accepted.

The rules are checked **cheapest-first** — amount, then invoice state, then policy state, then the
overpayment comparison — so the caller hears the thing it can fix without needing further information.
`InvoicePaymentRulesTest.amountIsValidatedBeforeInvoiceState` pins that order: a negative amount against
a cancelled invoice reports the amount.

---

## 6. Why `BigDecimal` rather than `double`

**File:** `domain/Money.java`

Three instalments of 33.33, 33.33 and 33.34 must settle a 100.00 invoice **exactly**, and binary
floating point cannot promise that. `Money.normalise` uses `RoundingMode.UNNECESSARY`, so a value that
would need rounding throws instead of quietly losing a cent.

**The test:** `InvoiceBalanceTest.sequentialPartialPaymentsSettleInvoiceExactly`.

Over-precise amounts such as `10.001` are **refused** (`AMOUNT_SCALE_INVALID`), not rounded. Silently
rounding sub-cent amounts is how a billing platform accumulates differences nobody can explain.

---

## 7. How the BDD layer avoids duplicating the other suites

**Files:** `qa-bdd-tests/src/test/java/.../steps/InvoicePaymentApiSteps.java` and
`InvoiceConsoleUiSteps.java`

The step definitions contain **no automation logic**. `InvoicePaymentApiSteps` uses `InvoiceApiClient`
and `BillingTestData` from `qa-api-tests`; `InvoiceConsoleUiSteps` uses `InvoiceDetailsPage` and
`InvoiceListPage` from `qa-ui-tests`. No locator and no `WebDriver` reference appears anywhere in the
BDD module.

That is possible because framework code lives in `src/main/java` of its module and the tests in
`src/test/java`, so the BDD module depends on ordinary library classes rather than consuming another
module's test-jar.

**Per-scenario state:** `support/ScenarioContext.java`, constructor-injected by picocontainer, fresh for
each scenario. Static fields are the usual route to an order-dependent suite.

**Runners split by tag:** `ApiScenariosIT` (`@api`) and `UiScenariosIT` (`@ui`), so a browser problem
and a contract problem do not arrive as the same red result. `BrowserHooks` is tagged `@Before("@ui")`,
so API scenarios do not pay for starting Chrome.

**What went wrong here — DEF-008.** All six UI scenarios failed with `No WebDriver for this thread`,
which reads like a driver bug. The cause was that `BrowserHooks` lives in the `support` package while
the runners declared `glue = "...bdd.steps"`, and **Cucumber glue scanning is package-exact, not
recursive**. The hooks were never registered, so `@Before("@ui")` never fired. Both runners now list
both packages.

---

## 8. The pipeline

**File:** `.github/workflows/ci.yml` — six jobs.

1. **build** — `mvn -B clean install -DskipITs`, runs the 72 application tests, uploads the jar and its
   coverage data.
2. **api-tests** — downloads that jar, `scripts/start-app.sh`, `mvn -B verify -pl qa-api-tests`.
3. **ui-tests** — the same, Selenium headless, uploads failure screenshots.
4. **bdd-tests** — the same, uploads the Cucumber HTML reports always.
5. **cypress-smoke** — Node 20, `npm ci --ignore-scripts`, an explicit `cypress install`, then the
   binary by path.
6. **coverage** — merges the execution data jobs 1 to 4 produced, renders the full-stack report, and
   submits it to Codecov and SonarQube Cloud.

**The decisions:**

- **The jar is passed between jobs, not rebuilt.** The suites run against the exact artifact the first
  job validated, rather than a second build of the same source.
- **CI calls the same `scripts/start-app.sh` a developer calls**, so "works on my machine" and "works in
  CI" cannot quietly diverge.
- **It polls `/actuator/health` and never sleeps.** A sleep long enough for a slow runner wastes time on
  every fast run; one tuned to a fast run fails intermittently on a slow one.
- **Coverage is merged, not re-measured.** Jobs 2 to 4 attach the JaCoCo agent to the *application*
  process; job 6 merges what they recorded with the in-process data. That is what turns 91% line
  coverage into 99% without running a single suite twice — see [`coverage.md`](coverage.md).
- **Artifacts are chosen for diagnosis:** reports always, application log and browser screenshots on
  failure only.

**Two Maven traps that cost real time:**

- **`-DskipTests` does not suppress Failsafe 3.6.0.** The integration suites still execute and fail with
  connection refused. `-DskipITs` is the flag that actually excludes them.
- **Failsafe 3.6.0 dropped `suiteXmlFiles`** — DEF-005. It accepts the parameter, warns, ignores it, and
  runs every `*IT` class. A run selected as "smoke" reported BUILD SUCCESS having executed the entire
  suite. Group selection now uses the plugin's own `groups` parameter, and the suite XMLs were deleted
  rather than left in the repository looking functional.

---

## 9. The defects worth reading

All <!--count:defects-->14<!--/count--> are in [`defect-reports.md`](defect-reports.md), each with steps, root cause and the commit
that fixed it. Three are worth more than the others.

**DEF-007 — the start script reporting healthy for a server it did not start.** A stale instance held
port 8080; the new jar failed to bind and died; the health probe was answered by the old process; the
script printed "Application healthy". The SOAP suite then ran against a jar that predated the SOAP
endpoint and failed with 404s, which sent the investigation towards the servlet mapping instead of the
port. The lesson is the general one: **a green run can certify code that was never deployed.** The fix
is two changes — refuse to start when something else already serves the port, and check process liveness
*before* health in the wait loop, since checking health first lets one early success end the loop before
the process has been examined at all.

**DEF-004 — the seeding race.** Seeding ran from an `ApplicationRunner`, which executes *after* the
context refresh that starts Tomcat. The application therefore accepted requests and reported healthy
with an empty database — precisely the window every suite lands in: wait for health, read an invoice,
intermittent 404. Moved to `@PostConstruct` on a separate bean; separate, because a `@PostConstruct` on
the writer itself would self-invoke and bypass the transactional proxy.

**DEF-012 — the overdue rule read the host's time zone.** Found by static analysis, not by a test.
`LocalDate.now()` resolves against `ZoneId.systemDefault()`, so whether an invoice was `OVERDUE` was a
property of whichever machine the process started on. No test could catch it: every test evaluated the
rule in the same zone the code did, so the test and the defect agreed with each other. It sat inside
code that reported as fully covered — the clearest demonstration in this repository of what coverage
does not tell you. The fix makes the billing zone configuration (`billing.time-zone`), and the
regression test freezes a clock at an instant where UTC and the business zone disagree about the date.

---

## 10. What is missing, in priority order

1. **No SPA testing.** The console is server-rendered by choice — it keeps the stack Java-first and
   gives the UI suites a deterministic DOM with no hydration race. The cost is that nothing here
   demonstrates waiting on asynchronous client-side state, which is most of modern UI automation.
2. **No optimistic locking on `Invoice`** (no `@Version`), and concurrent payments against the *same*
   invoice are untested — the load plan gives every thread its own invoice. Whether the last 10.00 on a
   10.00 balance can be accepted twice under load is the interesting question, and the honest expectation
   is that it can.
3. **A real database.** In-memory H2 removes exactly the costs that dominate a real billing platform:
   query latency, connection pooling, lock contention. Testcontainers with PostgreSQL would make the
   performance numbers mean something. Skipped because Docker is not available on the machine this was
   built on.
4. **Pagination on `GET /api/invoices`.** It returns every invoice. Fine for six; linear and eventually
   unacceptable for fifty thousand.
5. **Cypress overlaps Selenium.** The triangulation argument — two independent toolchains against the
   same console — is real but thin, and now has a measurement behind it: comparing the coverage reports
   class by class, the Cypress suite adds **zero** lines that Selenium does not already cover.
6. **Accessibility, security and cross-browser testing.** None. There is no authentication to test for
   the second of those, which is a reason the gap is smaller than it looks, not a reason it is closed.

---

## 11. The limits of what this repository claims

Stated here rather than left to be discovered.

**The Jenkins pipeline has never been executed.** It is a real, complete declarative pipeline written
against this project's actual layout and commands, and no controller was available to run it. That is
stated at the top of the `Jenkinsfile`, in the README and in the test strategy. GitHub Actions is the
pipeline that gates merges.

**The performance numbers are not a capacity measurement**, and [`perf/README.md`](../perf/README.md)
says so before anyone else can. The load generator shares a machine with the application, the database
is in-memory, the dataset is tiny, the run is five seconds, and there is no think time. They are useful
as a trend and as evidence that concurrent payment writes produce no errors.

**Nobody reviewed this code.** It is a single-contributor repository. The pull request checklists
describe a structured self-review of the diff and never imply a second reviewer; GitHub does not count
an author's own approval, and that limitation was respected rather than worked around.

**<!--count:total-->600<!--/count--> tests is a lot for this much application**, deliberately — the application exists in order to be
tested, and the ratio would be wrong in a product repository. What is worth defending is the
*distribution*: the coverage is concentrated on the money-handling rules, and sixteen of the <!--count:requirements-->33<!--/count-->
requirements in the [traceability matrix](requirements-traceability-matrix.md) are covered at four or
more levels.

**99% line coverage does not mean 99% of the behaviour is verified.** A line counts as covered if a test
executed it, whether or not anything asserted on what it did. DEF-012 is the proof, and
[`coverage.md`](coverage.md) names every remaining gap individually.

---

## 12. Publishing the platform over MCP

The MCP server at `/mcp` exposes the registry the assistant already reads through, and nothing else.
Three decisions in it are worth the paragraph.

**Spring AI was the framework-idiomatic choice and was rejected on evidence.**
`spring-ai-starter-mcp-server-webmvc` 2.0.1 declares `spring-boot-starter-web` **4.1.1**, read from its
published POM. Adopting it would drag this application from Spring Boot 3.5 to 4.1, which is a
different project with a different risk profile. `mcp-core` is a plain library with no opinion about
the framework around it, and its servlet transport mounts on the container the application already
runs.

**`mcp-core` plus `mcp-json-jackson2`, not the aggregate `mcp` artifact.** `mcp` pulls
`mcp-json-jackson3`, and this is a Jackson 2 application: two Jackson majors on one classpath to
serialise the same four DTOs is a cost with nothing bought. Binding to Jackson 2 also lets the MCP
surface use the application's own `ObjectMapper`, which is what makes a tool result **byte-identical**
to the REST response behind it rather than merely similar — and that identity is asserted, tool by
tool, in `McpSurfaceApiIT`.

**Stateless rather than session-based.** Of the three servlet transports the SDK ships, the stateless
one answers a single JSON-RPC POST without a session to open first. Every tool is a read against data
the caller names in the call, so there is no per-client state a session would hold, and the surface
stays checkable with one `curl` — the difference between a reviewer verifying the claim in the README
and taking it on trust.

**`mcp-test` is not used, and the acceptance criteria asked for it.** Its classes are abstract
conformance suites written for people implementing the SDK itself, not utilities for testing an
application's tools, and its POM pulls `org.testcontainers:toxiproxy` at compile scope — a Docker
requirement, in a repository that documents Docker as unavailable and uses in-memory H2 because of it.
The coverage the criterion wanted exists: `McpServerEndpointTest` drives the endpoint with the SDK's
own client, and `McpSurfaceApiIT` drives it as raw JSON-RPC from outside the application.

**There is no authentication on `/mcp`**, matching the REST API beside it. A real deployment would put
both behind the same authentication and scope the tools to the caller's own accounts. It is listed
here because an unauthenticated data endpoint is exactly the kind of thing a reader should see named
by the author rather than discover for themselves.

---

## 13. A second live provider, and what it is for

The assistant was built behind a port from the start, and a port with one implementation is an
assertion nobody has tested. `GeminiBillingAssistant` is the test of it: a new class and a config
value, with no edit to the console, the REST endpoint, the tool registry, or any suite above them.

**Why Gemini specifically.** Its free tier needs no card and no paid plan, which is the difference
between a live model that runs on a schedule and one that runs when somebody is willing to pay for it.
A weekly evaluation over a few dozen prompts sits well inside the published limits.

**The system prompt is shared, not copied.** `AssistantSystemPrompt` is given to both providers
verbatim. Two copies would drift the first time one was tuned, and an evaluation comparing two models'
answers would then be comparing two prompts.

**REST rather than a vendor SDK.** Google's Java client is a large dependency for a request that is
four fields and a nested map, and `RestClient` ships with the web starter already here. The key
travels in the `x-goog-api-key` header rather than the `?key=` query parameter the quickstart uses,
because URLs end up in access logs and error messages.

**What has and has not been run.** The adapter is exercised end to end against a stubbed API with
MockWebServer — the tool loop, the usage accounting, the finish reasons, the rate limit, and the
runaway-loop ceiling — with no key, no network and no cost, on every pull request. **No request has
been made to the real Gemini API from this repository**, exactly as with the Anthropic provider, and
that stays stated here until one has.

---

## Where to start reading

| File | Why |
|---|---|
| `billing-app/src/main/java/com/insurancebilling/domain/Invoice.java` | Every billing rule, the ordering decision, and the derived-balance design |
| `billing-app/src/main/java/com/insurancebilling/api/GlobalExceptionHandler.java` | The 400/404/409/422 scheme that makes every negative assertion meaningful |
| `qa-api-tests/src/main/java/com/insurancebilling/qa/api/data/BillingTestData.java` | Test isolation — the thing that makes parallel and repeat runs safe |
| `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/pages/BasePage.java` | The Page Object pattern, and the navigation wait that took three attempts |
| `.github/workflows/ci.yml` | The six-job pipeline, the artifact passing, and the coverage merge |

Then read [`defect-reports.md`](defect-reports.md). It is the most interesting document here, because it
is the only one describing things that went wrong.
