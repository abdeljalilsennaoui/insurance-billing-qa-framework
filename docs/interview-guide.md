# Interview preparation guide

Every answer below points at real files in this repository. Open them while reading — the goal is to be
able to walk someone through the code, not to recite a summary of it.

---

## 1. "Walk me through your Selenium architecture."

**Flow:** `BaseUiTest` → `DriverFactory` → page object → `BasePage` → the browser.

**Files, in the order the flow touches them:**

1. `qa-ui-tests/src/test/java/com/insurancebilling/qa/ui/BaseUiTest.java`
   `@BeforeMethod` calls `DriverFactory.startDriver()`; `@AfterMethod(alwaysRun = true)` quits it. It
   also holds `BillingTestData` and `InvoiceApiClient`, so a test builds fixtures over HTTP rather than
   through the browser.

2. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/driver/DriverFactory.java`
   Holds the driver in a `ThreadLocal`. `startDriver()` builds `ChromeOptions` (headless by default,
   `--no-sandbox`, `--disable-dev-shm-usage`), fixes the window size, and sets a page-load timeout.
   `quitDriver()` calls `quit()` **and** `DRIVER.remove()`.

3. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/config/UiConfig.java`
   Reads `app.base.url`, `ui.headless`, `ui.wait.seconds`, the screenshot directory and the window size
   from system properties with defaults.

4. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/pages/BasePage.java`
   Builds locators with `testId(String)` → `By.cssSelector("[data-testid='...']")`. Exposes
   `visible`, `click`, `type`, `selectOption`, `isPresent`, all going through `WebDriverWait`.

5. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/pages/InvoiceListPage.java` and
   `InvoiceDetailsPage.java`
   Page methods return values or the next page object. No assertions.

6. `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/support/ScreenshotListener.java`
   A TestNG `ITestListener` writing a PNG on failure, registered via `@Listeners` on `BaseUiTest`.

**The four points worth volunteering:**

- **`ThreadLocal`, and `remove()` as well as `quit()`.** TestNG reuses threads; a stale entry would hand
  a dead session to the next test on that thread.
- **No implicit wait anywhere.** Mixing implicit and explicit waits produces timeouts belonging to
  neither.
- **No `Thread.sleep` in the module.** `grep -rn "Thread.sleep" qa-ui-tests/` returns exactly one hit,
  and it is the Javadoc in `BasePage` stating that it appears nowhere. Worth running in front of someone.
- **Selenium Manager resolves chromedriver**, so no driver binary is committed and no version pinning
  drifts from the installed Chrome.

### Follow-up: "How do you handle waiting after a form submission?"

This is the best question to get, because the first implementation was wrong and the fix is in the
history — commit `63e8fc0`, written up as DEF-006.

The naive version waited for "a banner is present, or the outstanding balance is displayed". That is
satisfied by the page **already on screen**, because the balance is displayed there too. The wait
returned immediately, the test read pre-submit values, and the next interaction hit a document mid-replacement
— `StaleElementReferenceException`, in whichever test submitted twice.

The fix, in `InvoiceDetailsPage.waitForPageReplacement`: hold an element from the submitted page and
wait for `ExpectedConditions.stalenessOf` on it. An element goes stale only once the browser has
discarded the document containing it, which is a genuine navigation signal, and it holds for both
outcomes — success redirects, rejection re-renders. The same mistake existed in
`InvoiceListPage.filterByStatus`, waiting for a page title present on both pages, and was fixed the same
way.

What I deliberately did **not** do: add a sleep or a retry. Both hide a race instead of removing it.

---

## 2. "How is your API test framework structured?"

**Files:**

- `qa-api-tests/src/main/java/com/insurancebilling/qa/api/config/ApiSpecs.java` — the single
  `RequestSpecification`: base URI, content type, Jackson config, and
  `enableLoggingOfRequestAndResponseIfValidationFails`.
- `qa-api-tests/src/main/java/com/insurancebilling/qa/api/config/TestEnvironment.java` — target from `app.base.url`, defaulted.
- `qa-api-tests/src/main/java/com/insurancebilling/qa/api/client/` — `InvoiceApiClient.java`,
  `CustomerApiClient.java`, `PolicyApiClient.java` — typed
  operations plus raw `Response` variants.
- `qa-api-tests/src/main/java/com/insurancebilling/qa/api/model/` — `InvoiceDto`, `PaymentDto`, `ApiError`, etc. as records.
- `qa-api-tests/src/main/java/com/insurancebilling/qa/api/data/BillingTestData.java` — per-scenario fixtures.
- `qa-api-tests/src/test/java/com/insurancebilling/qa/api/BaseApiTest.java` — one health probe before the suite.

**Three decisions to explain:**

**Logging only on failure.** Always-on logging makes a green run unreadable; a failure printing nothing
is undiagnosable from a CI log. `ApiSpecs` configures REST Assured to dump request and response only
when an assertion fails.

**Two method shapes per operation.** `pay()` asserts 201 and returns a `PaymentDto`; `payRaw()` returns
the `Response`. A negative test needs to inspect an error body; a happy-path test should not repeat the
extraction.

**The module does not depend on `billing-app`.** `InvoiceDto.status()` is a `String`, not the
application's `InvoiceStatus` enum. If both sides shared the enum, renaming a constant would change test
and application together and the suite would still pass while the published contract had broken. The
cost is no compile-time safety on status strings — a typo fails at runtime. Worth it.

---

## 3. "How do you keep tests independent?"

**File:** `qa-api-tests/src/main/java/com/insurancebilling/qa/api/data/BillingTestData.java`

Every fixture method — `unpaidInvoice`, `partiallyPaidInvoice`, `settledInvoice`, `cancelledInvoice`,
`overdueInvoice`, `invoiceOnInactivePolicy` — creates a **new** customer, policy and invoice through the
public API. Emails are UUID-suffixed, because the application enforces a unique-email constraint and a
fixed address would make the second run fail with a 409.

Nothing in the mutating suites reads or changes the seeded `SEED-` records. Those exist for
`SeedDataLoader`-based reads and the Cypress smoke suite.

**The proof, and the phrasing to use:** "The check that matters is running the suite twice against one
running application without restarting it. A suite that only passes against a freshly started app is
hiding shared-state coupling. I ran the API suite twice in a row — 43 of 43 both times." That is also
what makes the Failsafe `parallel=methods` setting with 4 threads safe.

**Why resets are per-run, not per-test:** `TestSupportController.reset()` exists, but per-test resets
would serialise the suite and make parallel execution impossible.

---

## 4. "Why is a zero payment a 422 and not a 400?"

This is the decision I would most want to be asked about.

**Files:** `billing-app/src/main/java/com/insurancebilling/api/GlobalExceptionHandler.java`,
`billing-app/src/main/java/com/insurancebilling/api/dto/PaymentRequest.java`, `billing-app/src/main/java/com/insurancebilling/domain/Invoice.java`

`PaymentRequest` validates only **presence** — `@NotNull` on amount and method. It has no `@Positive`.
Whether the amount is positive, correctly scaled, or within the balance is a **billing rule**, enforced
in `Invoice.applyPayment` and surfaced as `PaymentRejectedException` → 422 with the reason in `code`.

**Why it matters:** if a zero amount returned 400 like a missing field does, a test could not tell "the
client sent nonsense" from "the platform applied a billing rule". A regression that turned one into the
other would pass unnoticed. The split is what makes
`PaymentValidationApiIT.malformedBodiesAreRejectedAsBadRequestsNotRuleViolations` meaningful: it pins
that unparseable input is 400 and **never** 422.

**Full scheme:** 400 not understood · 404 absent · 409 conflicts with existing state (duplicate email)
· 422 understood and refused by a rule.

---

## 5. "Why are the billing rules on the entity instead of in a service?"

**File:** `billing-app/src/main/java/com/insurancebilling/domain/Invoice.java`, method `applyPayment`

Two reasons:

1. **They cannot be bypassed.** A guard in a service is bypassed by any other caller that reaches the
   entity. A guard inside `applyPayment` is not.
2. **They are testable without Spring.** `InvoicePaymentRulesTest` runs as plain JUnit — no context, no
   database, milliseconds.

`InvoiceService.pay()` does only lookup and transaction management, then delegates.

**Trade-off to state:** the entity is larger than an anaemic model. Accepted.

**Also worth mentioning:** the rules are checked **cheapest-first** — amount, then invoice state, then
policy state, then the overpayment comparison — so the caller hears the thing it can fix without further
information. `InvoicePaymentRulesTest.amountIsValidatedBeforeInvoiceState` pins that order: a negative
amount against a cancelled invoice reports the amount.

---

## 6. "Why `BigDecimal` rather than `double`?"

**File:** `billing-app/src/main/java/com/insurancebilling/domain/Money.java`

Three instalments of 33.33, 33.33 and 33.34 must settle a 100.00 invoice **exactly**. Binary floating
point cannot promise that. `Money.normalise` uses `RoundingMode.UNNECESSARY`, so a value that would need
rounding throws instead of silently losing a cent.

**The test to point at:** `InvoiceBalanceTest.sequentialPartialPaymentsSettleInvoiceExactly`.

**Related:** over-precise amounts such as `10.001` are **refused** (`AMOUNT_SCALE_INVALID`), not rounded.
Silently rounding sub-cent amounts is how a platform accumulates unexplained differences.

---

## 7. "How does your BDD layer avoid duplicating the other suites?"

**Files:** `qa-bdd-tests/src/test/java/com/insurancebilling/qa/bdd/steps/InvoicePaymentApiSteps.java`
and `InvoiceConsoleUiSteps.java`

The step definitions contain **no automation logic**. `InvoicePaymentApiSteps` uses `InvoiceApiClient`
and `BillingTestData` from `qa-api-tests`; `InvoiceConsoleUiSteps` uses `InvoiceDetailsPage` and
`InvoiceListPage` from `qa-ui-tests`. No locator and no `WebDriver` reference appears in the BDD module.

That is possible because framework code lives in `src/main/java` of its module and tests in
`src/test/java`, so the BDD module depends on ordinary library classes rather than a test-jar.

**State:** `qa-bdd-tests/src/test/java/com/insurancebilling/qa/bdd/support/ScenarioContext.java`, constructor-injected by picocontainer, fresh per scenario.
Static fields would be the usual route to an order-dependent suite.

**Runners split by tag:** `qa-bdd-tests/src/test/java/com/insurancebilling/qa/bdd/runners/ApiScenariosIT.java` (`@api`) and
`UiScenariosIT.java` (`@ui`), so a
browser problem and a contract problem do not arrive as the same red result. `BrowserHooks` is tagged
`@Before("@ui")` so API scenarios do not pay for starting Chrome.

### Follow-up: "Did anything go wrong setting that up?"

Yes — DEF-008. All six UI scenarios failed with `No WebDriver for this thread`, which looks like a driver
bug. The cause was that `BrowserHooks` lives in the `support` package while the runners declared
`glue = "...bdd.steps"`, and **Cucumber glue scanning is package-exact, not recursive**. The hooks were
never registered, so `@Before("@ui")` never fired. Both runners now list both packages.

---

## 8. "Walk me through your CI pipeline."

**File:** `.github/workflows/ci.yml` — five jobs.

1. **build** — `mvn -B clean install -DskipITs`, runs the 72 application tests, uploads the jar.
2. **api-tests** — downloads the jar, `scripts/start-app.sh`, `mvn -B verify -pl qa-api-tests`.
3. **ui-tests** — same, Selenium headless, uploads failure screenshots.
4. **bdd-tests** — same, uploads Cucumber HTML reports always.
5. **cypress-smoke** — Node 20, `npm ci`, explicit `cypress install`, `npx cypress run`.

**Points to make:**

- **The jar is passed between jobs, not rebuilt.** The suites run against the exact artifact the first
  job validated.
- **CI calls the same `scripts/start-app.sh` a developer calls**, so "works on my machine" and "works in
  CI" cannot diverge.
- **It polls `/actuator/health`, never sleeps.** A sleep long enough for a slow runner wastes time on
  every fast run; one tuned to a fast run fails intermittently on a slow one.
- **Artifacts are chosen for diagnosis:** reports always, application log and screenshots on failure
  only.

### Follow-up: "Any Maven gotchas?"

Two, both of which cost real time and are documented in the README:

- **`-DskipTests` does not suppress Failsafe 3.6.0.** The integration suites still executed and failed
  with connection refused. `-DskipITs` is the flag that actually excludes them.
- **Failsafe 3.6.0 dropped `suiteXmlFiles`** — DEF-005. It accepts the parameter, warns, ignores it, and
  runs every `*IT` class. `-Dapi.suite=suites/smoke.xml` reported BUILD SUCCESS having run all 43 tests
  instead of 5. Group selection now uses the plugin's `groups` parameter, and the suite XMLs were
  deleted rather than left looking functional.

---

## 9. "Tell me about a bug you found."

Pick by what the interviewer seems to value. All nine are in `docs/defect-reports.md`.

**Best overall — DEF-007, the start script reporting healthy for a server it did not start.** A stale
instance held port 8080; the new jar failed to bind and died; the health probe was answered by the old
process; the script printed "Application healthy". The SOAP suite then ran against a jar that predated
the SOAP endpoint and failed with 404s, which sent me looking at the servlet mapping instead of the port.

Why it is the best story: **a green run can certify code that was never deployed.** The fix is two
changes — refuse to start when something else already serves the port, and check process liveness
*before* health in the wait loop, since checking health first lets one early success end the loop before
the process is examined at all.

**Best application bug — DEF-004, the seeding race.** Seeding ran from an `ApplicationRunner`, which
executes *after* the context refresh that starts Tomcat. So the app accepted requests and reported
healthy with an empty database. That is precisely the window every suite lands in: wait for health, read
an invoice, intermittent 404. Moved to `@PostConstruct` on a separate bean — separate because a
`@PostConstruct` on the writer would self-invoke and bypass the transactional proxy.

**Best "my own test was wrong" — DEF-006**, the stale-element wait in section 1.

---

## 10. "What would you do differently with more time?"

Honest, in priority order:

1. **Add a real database.** In-memory H2 removes exactly the costs that dominate a real billing
   platform: query latency, connection pooling, lock contention. Testcontainers with PostgreSQL would
   make the performance numbers mean something. It was skipped because Docker is not available on this
   machine.
2. **Pagination on `GET /api/invoices`.** It returns every invoice. Fine for six; linear and eventually
   unacceptable for fifty thousand. A performance test against a large dataset would expose it.
3. **Drop Cypress, or make it carry distinct coverage.** Today it overlaps Selenium. The triangulation
   argument is real but thin; I would rather it tested something Selenium does not.
4. **Concurrency testing on payments.** JMeter submits concurrent payments to *different* invoices. Two
   simultaneous payments against the *same* invoice is the interesting case — whether the last 10.00 on
   a 10.00 balance can be accepted twice. There is no optimistic locking (`@Version`) on `Invoice`, so I
   would expect that to be a real defect under load.
5. **Accessibility checks** on the console.
6. **Contract testing** (Pact) if partners consumed the SOAP or REST services.

---

## Answers to questions I would find uncomfortable

**"Is the Jenkins pipeline real?"** It is a real, complete declarative pipeline written against this
project's actual layout and commands, and it has **never been executed** — no controller was available.
That is stated at the top of the `Jenkinsfile`, in the README, and in the test strategy. GitHub Actions
is the pipeline that actually gates merges.

**"Your performance numbers look too good."** They are, as a capacity claim, and `perf/README.md` says
so. The load generator shares a machine with the application, the database is in-memory, the dataset is
tiny, the run is five seconds, and there is no think time. The numbers are useful as a trend and as
proof that 200 concurrent payment writes produce no errors. They are not a capacity measurement.

**"Who reviewed this code?"** Nobody. It is a single-contributor repository and the PR checklists
describe a structured self-review of the diff; they never claim a second reviewer. GitHub does not count
an author's own approval, and I did not work around that.

**"Is 161 tests a lot for this much application?"** Yes, deliberately — the application exists to be
tested. The ratio would be wrong in a product repository. What I would defend is the *distribution*:
coverage is concentrated on the money-handling rules, and nine of the twenty-one requirements are
covered at four or more levels.

**"What is the weakest part?"** No SPA testing, because the console is server-rendered by choice. Nothing
here demonstrates waiting on asynchronous client-side state, which is most of modern UI automation. After
that: no database beyond H2, and no security testing because there is no authentication to test.

---

## Five files to read before an interview

| File | Why |
|---|---|
| `billing-app/src/main/java/com/insurancebilling/domain/Invoice.java` | Every billing rule, the ordering decision, and the derived-balance design |
| `billing-app/src/main/java/com/insurancebilling/api/GlobalExceptionHandler.java` | The 400/404/409/422 scheme that makes every negative assertion meaningful |
| `qa-api-tests/src/main/java/com/insurancebilling/qa/api/data/BillingTestData.java` | Test isolation — the thing that makes parallel and repeat runs safe |
| `qa-ui-tests/src/main/java/com/insurancebilling/qa/ui/pages/InvoiceDetailsPage.java` | The Page Object pattern and the staleness wait fix from DEF-006 |
| `.github/workflows/ci.yml` | The five-job pipeline and the artifact-passing design |

Then skim `docs/defect-reports.md` — it is the most interesting document in the repository, because it is
the only one describing things that went wrong.
