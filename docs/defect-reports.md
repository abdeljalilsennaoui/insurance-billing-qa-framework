# Defect reports

Fourteen defects found while building and stabilising this project. Every one was actually encountered —
none is an illustrative example written to fill a template. Each is linked to the commit that fixed it,
so the claim can be checked against the history.

Three of them (DEF-005, DEF-007, DEF-008) were defects in the **test infrastructure** rather than in the
application. Those are the most dangerous class of defect in a QA project, because they make green runs
untrustworthy: two of the three produced a passing or apparently-healthy result while testing nothing,
or testing the wrong thing.

| ID | Title | Severity | Found by | Fixed in |
|---|---|---|---|---|
| DEF-001 | Every path-variable endpoint fails at runtime | Critical | API integration tests | `a4e4d85` |
| DEF-002 | `LazyInitializationException` mapping an invoice response | Critical | API integration tests | `de287f6` |
| DEF-003 | Session id appended to redirect URLs | Medium | Manual smoke check | `a69ad79` |
| DEF-004 | Application reports healthy before data is seeded | High | Manual smoke check | `3074d76` |
| DEF-005 | Test group selection silently ignored | High | Group count inspection | `459e670` |
| DEF-006 | `StaleElementReferenceException` on repeated form submission | High | Selenium suite | `63e8fc0` |
| DEF-007 | Start script reports healthy for a server it did not start | High | SOAP suite investigation | `c98a7d6` |
| DEF-008 | Cucumber hooks never registered | High | BDD UI scenarios | in PR #33 |
| DEF-009 | SOAP endpoint rejects `application/xml` | Medium | SOAP suite | in PR #35 |
| DEF-010 | Surefire claims an `*IT` class and runs it without an application | Medium | Coverage work | in PR #42 |
| DEF-011 | `stalenessOf` escapes as a CDP error on a CI runner | High | CI, not local | in PR #42 |
| DEF-012 | Overdue status decided by the host's default time zone | High | SonarQube Cloud static analysis | in PR #49 |
| DEF-013 | Language switch renders with no destination on a new page | Medium | Agent console tests | `4f2cfde` |
| DEF-014 | Documented test counts drifted apart from the suite | Low | Documentation audit | in PR #58 |

---

## DEF-001 — Every path-variable endpoint fails at runtime

**Severity:** Critical · **Priority:** Immediate · **Component:** Build configuration

**Environment:** JDK 21, Maven 3.9.16, Spring Boot 3.5.16

**Preconditions:** The REST controllers are implemented and the application compiles and starts.

**Steps to reproduce**

1. Start the application.
2. `GET /api/customers/1`

**Expected:** 200 with the customer, or 404 if absent.

**Actual:** 500. `IllegalArgumentException: Name for argument of type [java.lang.Long] not specified,
and parameter name information not available via reflection. Ensure that the compiler uses the
'-parameters' flag.`

**Impact:** Every endpoint taking a `@PathVariable` or `@RequestParam` was non-functional — most of the
API. 19 of 28 integration tests failed.

**Root cause:** Spring binds those arguments by parameter name, and `javac` discards parameter names
unless `-parameters` is passed. `spring-boot-starter-parent` sets this flag by default; this project
imports the Boot BOM instead of inheriting from that parent, so nothing was setting it.

**Resolution:** Added `<parameters>true</parameters>` to `maven-compiler-plugin` in the aggregator POM.

**Note:** a rebuild is required, not an incremental compile — the flag changes how existing, unmodified
sources are compiled, so `mvn test` without `clean` still failed after the fix and briefly made it look
ineffective.

**Lesson:** importing a BOM is not the same as inheriting the parent. The parent contributes plugin
configuration that the BOM does not.

---

## DEF-002 — `LazyInitializationException` mapping an invoice response

**Severity:** Critical · **Priority:** Immediate · **Component:** Persistence / API

**Preconditions:** An invoice exists on a policy held by a customer.

**Steps to reproduce**

1. `GET /api/invoices/{id}`

**Expected:** 200 with the invoice including `customerName`.

**Actual:** 500. `org.hibernate.LazyInitializationException: Could not initialize proxy
[com.insurancebilling.domain.Customer#9] - no session`

**Impact:** Every invoice read failed. 16 of 16 invoice integration tests errored.

**Root cause:** The application runs with `spring.jpa.open-in-view=false`, so the Hibernate session
closes when the service transaction commits. The controller then maps the entity to a DTO, walking
`invoice → policy → customer` to read the customer name, by which point the proxy is unusable.

**Resolution:** `@EntityGraph(attributePaths = {"policy", "policy.customer", "payments"})` on the
invoice reads that feed responses, and `{"customer"}` on the policy reads.

**Why not enable `open-in-view`:** it would have made the symptom disappear without addressing the
cause, and would leave the query count dependent on whatever a view happens to touch. Declaring the
fetch on the query keeps it predictable and visible in code.

---

## DEF-003 — Session id appended to redirect URLs

**Severity:** Medium · **Priority:** Before UI automation · **Component:** Web console

**Steps to reproduce**

1. Submit a valid payment from the console with a client that holds no session cookie.
2. Inspect the `Location` header.

**Expected:** `http://localhost:8080/invoices/2`

**Actual:** `http://localhost:8080/invoices/2;jsessionid=B651F18262BAD0D89E7BD2E1EB1616ED`

**Impact:** Two problems. The session identifier travels in URLs, and therefore into logs and `Referer`
headers. And any UI assertion on the post-payment URL would be non-deterministic, passing or failing
depending on whether the browser had a cookie at that moment.

**Root cause:** Tomcat falls back to URL-based session tracking when a client presents no session
cookie.

**Resolution:** `server.servlet.session.tracking-modes: cookie`.

**Found by:** manual smoke check with `curl` before writing any UI automation — which is precisely when
it is cheapest to find.

---

## DEF-004 — Application reports healthy before data is seeded

**Severity:** High · **Priority:** Before CI · **Component:** Startup

**Steps to reproduce**

1. Start the application.
2. Poll `/actuator/health` until it returns 200.
3. Immediately `POST /invoices/1/payments`.

**Expected:** 302 redirect; the seeded invoice exists.

**Actual:** 404 — no invoice with id 1 yet.

**Impact:** An intermittent, environment-dependent failure in exactly the pattern every automated suite
uses: wait for health, then act. It would have surfaced as random CI flakiness attributed to the tests
rather than to startup ordering.

**Root cause:** Seeding ran from an `ApplicationRunner`, which Spring Boot executes *after* the context
refresh completes. Tomcat starts listening as part of that refresh, so there is a window in which the
application accepts requests and reports healthy with an empty database.

**Resolution:** Moved seeding to `@PostConstruct` on a dedicated `SeedDataInitializer` bean, which runs
while singletons are being created and therefore before the web server listens. A separate bean is
required because a `@PostConstruct` on the writer itself would self-invoke and bypass the transactional
proxy.

**Verification:** restarted and probed immediately after health returned 200 — `GET /invoices/1` → 200,
6 invoices present, payment → 302.

---

## DEF-005 — Test group selection silently ignored

**Severity:** High · **Priority:** Immediate · **Component:** Test infrastructure

**Steps to reproduce**

1. `mvn -B verify -pl qa-api-tests -Dapi.suite=suites/smoke.xml`

**Expected:** the 5 tests in the `smoke` group.

**Actual:** `BUILD SUCCESS`, having run all 43 tests.

**Impact:** The fast feedback loop the smoke group exists to provide did not exist, and nothing failed
to say so. The build reported success, so the only evidence was a deprecation warning buried in the
log: `Parameter 'suiteXmlFiles' is deprecated: not supported after 3.6.0`.

**Root cause:** `maven-failsafe-plugin` 3.6.0 dropped support for `suiteXmlFiles`. It accepts the
parameter, warns, ignores it, and falls back to running every `*IT` class.

**Resolution:** Group selection through the plugin's own `groups` parameter. The three TestNG suite XML
files were **deleted** rather than left in place, because configuration that looks functional while
being inert is worse than no configuration at all.

**Verification:** by count — at the time of the fix, `smoke` 5, `negative` 30, `regression` 43. Those
numbers have since grown to 6, 32 and 50 as the SOAP tests joined the same groups; the README carries the
current figures. Counting is the verification, whatever the numbers are.

**Lesson:** a passing build is not evidence that the thing you asked for happened. Assert on what the
tooling actually did.

---

## DEF-006 — `StaleElementReferenceException` on repeated form submission

**Severity:** High · **Priority:** Immediate · **Component:** UI automation

**Steps to reproduce**

1. Run the Selenium suite.
2. Observe `severalPartialPaymentsSettleTheInvoiceThroughTheUi` and
   `thePaymentHistoryListsEveryRecordedPayment` — both submit the payment form more than once.

**Expected:** 18 of 18 pass.

**Actual:** 16 of 18. `StaleElementReferenceException: stale element reference: stale element not
found`, plus an assertion failure reading pre-submit values.

**Root cause:** My own wait condition. After clicking submit, it waited for "a banner is present, or the
outstanding balance is displayed" — but the outstanding balance is displayed on the page *already on
screen*. The condition was satisfied before the browser had navigated anywhere, so the test read stale
values and the next interaction touched a document being replaced.

**Resolution:** Hold an element from the submitted page and wait for `ExpectedConditions.stalenessOf`
on it. An element goes stale only once the browser has discarded the document containing it, which is a
true navigation signal, and it holds for both outcomes since a success redirects and a rejection
re-renders. The identical mistake in the list page's status filter (waiting for a page title present on
both pages) was corrected the same way.

**Explicitly not done:** no `Thread.sleep`, no retry. Both mask a race rather than remove it, and leave
a suite passing for timing reasons that change between machines.

**Verification:** 18 of 18 pass, locally and on the CI runner.

---

## DEF-007 — Start script reports healthy for a server it did not start

**Severity:** High · **Priority:** Immediate · **Component:** Test infrastructure

**Steps to reproduce**

1. Leave an application instance running on port 8080.
2. Build a new jar containing new functionality.
3. `scripts/start-app.sh`

**Expected:** a clear failure — the port is taken.

**Actual:** `Application healthy after 0s (PID 47892)`. PID 47892 had already died with
`APPLICATION FAILED TO START` (port in use); the health probe was answered by the old instance,
PID 35987.

**Impact:** The worst kind of false signal. The SOAP suite ran against a jar that predated the SOAP
endpoint and failed with 404s, sending the investigation towards the endpoint and the servlet mapping
rather than the port. Generalised: a green run can certify code that was never deployed.

**Root cause:** Two compounding issues. The script polled health without checking that the process it
launched was alive, and the wait loop tested health *before* liveness, so a single early success ended
the loop before the process was examined at all.

**Resolution:** Refuse to start when something already serves the port and was not started by this
script, printing the holder PID; and check process liveness before health on every pass of the loop.

**Note:** only reproducible locally, because every CI runner is fresh. That is exactly why it was worth
fixing — the failure mode appears where people iterate, and costs the most time there.

---

## DEF-008 — Cucumber hooks never registered

**Severity:** High · **Priority:** Immediate · **Component:** BDD infrastructure

**Steps to reproduce**

1. Run `UiScenariosIT`.

**Expected:** 6 browser scenarios pass.

**Actual:** all 6 error with `IllegalState: No WebDriver for this thread. Tests must extend BaseUiTest
so a driver is started before use.`

**Root cause:** `BrowserHooks` lives in `com.insurancebilling.qa.bdd.support`, and the runners declared
`glue = "com.insurancebilling.qa.bdd.steps"`. Cucumber glue scanning is **package-exact, not recursive
from a parent package**, so the hooks were never registered and `@Before("@ui")` never fired.

**Resolution:** Both runners now declare both packages, with a comment recording why.

**Why it was misleading:** Cucumber reports unregistered hooks as a *step failure* inside the scenario,
so the message pointed at the driver factory. The actual fault was one line of configuration.

---

## DEF-009 — SOAP endpoint rejects `application/xml`

**Severity:** Medium · **Priority:** Immediate · **Component:** SOAP automation

**Steps to reproduce**

1. POST a valid `GetInvoiceStatusRequest` envelope to `/ws` with `Content-Type: application/xml`.

**Expected:** 200 with the status response.

**Actual:** non-200, no SOAP response. The identical envelope sent with `curl` and
`Content-Type: text/xml` returned 200 correctly.

**Root cause:** SOAP 1.1 specifies `text/xml`, and Spring WS refuses other content types. REST Assured's
`ContentType.XML` sends `application/xml`.

**Resolution:** The test sends `text/xml; charset=utf-8` explicitly. This is also the more honest test,
since it is what a real SOAP 1.1 client sends.

**Secondary issue found at the same time:** `XmlPath` expressions written as `**.status` produced
`IllegalArgumentException: The parameter "status" was used but not defined` — a wildcard is parsed as a
GPath *parameter reference*, not a path. Fixed by naming the path from the document root.

---

## DEF-010 — Surefire claims an `*IT` class and runs it without an application

**Severity:** Medium · **Priority:** Immediate · **Component:** Test infrastructure

**Steps to reproduce**

1. Name an integration test `TestSupportResetIT`, extending `BaseApiTest`.
2. Run `mvn clean install -DskipITs` with no application running.

**Expected:** the class is a Failsafe integration test; `-DskipITs` excludes it and the build passes.

**Actual:** `BUILD FAILURE`. Surefire ran it during the `test` phase:
`Running com.insurancebilling.qa.api.TestSupportResetIT` → `java.net.ConnectException: Connection
refused` in the suite-level health check.

**Root cause:** Surefire's default includes are `Test*.java`, `*Test.java`, `*Tests.java` and
`*TestCase.java`. `TestSupportResetIT` matches the **first** pattern. Ending in `IT` is not enough to keep
a class out of Surefire's hands — the name must also avoid starting with `Test`.

**Resolution:** renamed to `ResetEndpointIT`, with the reason recorded in the class Javadoc so the next
person naming a test-support class does not rediscover it.

**Why it is worth recording:** the failure looked like an environment problem — "connection refused, the
app must not be up" — when the real cause was that a file name matched a pattern. The misleading part is
that the class was correctly suffixed for Failsafe, so the naming looked deliberate and correct.

## DEF-011 — `stalenessOf` escapes as a CDP error on a CI runner

**Severity:** High · **Priority:** Immediate · **Component:** UI automation

**Environment:** Failed on `ubuntu-latest` with the runner's Chrome. **Passed consistently on macOS**, over
many local runs including two consecutive full-suite passes.

**Steps to reproduce:** run the Selenium suite on a GitHub-hosted runner.

**Expected:** 18 of 18 pass, as locally.

**Actual:** 17 of 18. `aPartialPaymentReducesTheOutstandingBalance` errored:

```
WebDriver unknown error: unhandled inspector error:
{"code":-32000,"message":"Node with given id does not belong to the document"}
  at ExpectedConditions$24.apply(ExpectedConditions.java:686)
  at InvoiceDetailsPage.waitForPageReplacement(InvoiceDetailsPage.java:121)
```

**Root cause:** the fix for DEF-006. `ExpectedConditions.stalenessOf` decides an element is stale by
*touching* it and catching `StaleElementReferenceException`. When a document has been discarded
mid-navigation, ChromeDriver may instead raise a CDP-level `WebDriverException` carrying
`Node with given id does not belong to the document`. `stalenessOf` does not catch that, so it escapes as a
test error rather than being treated as the staleness it actually represents.

Which exception arrives depends on how far navigation has progressed when the probe lands, so it is a race
— and one whose odds differ by machine and Chrome version. The DEF-006 fix was correct about *what* to wait
for and wrong about *how* to detect it.

**Resolution:** stop touching the old element. Before navigating, set a marker on `window`; afterwards,
wait until the marker is gone **and** `document.readyState === 'complete'`. A full page load creates a
fresh `window`, so the marker's disappearance is positive proof the document was replaced, and asking the
document about itself never dereferences something that may already be dead.

Applied in `BasePage.markCurrentDocument` / `waitForNewDocument`, used by both the payment form and the
list filter.

**Why this one is the most instructive in the list:** it only ever failed in CI. Locally it passed every
time, including the deliberate two-consecutive-runs isolation check. A suite that is green on a developer
machine is evidence about that machine, and nothing more — which is the whole argument for the pipeline
being the gate rather than a local run.

**Not done:** no retry, and no `ignoring(WebDriverException.class)` on the wait. Ignoring the exception
class would have made the symptom disappear while leaving the wait probing a dead reference, and would
have swallowed genuine driver errors with it.

## DEF-012 — Overdue status decided by the host's default time zone

**Severity:** High · **Priority:** High · **Component:** Billing domain / application configuration

**Found by:** SonarQube Cloud static analysis, rule `java:S8688` ("`LocalDate.now()` should not be used
without a `ZoneId` or `Clock`"), raised **10 times** on `billing-app`. Not by any test.

**Environment:** JDK 21, Spring Boot 3.5.16. Reproducible on any host whose default zone is not the
insurer's — including every GitHub Actions runner, which is UTC.

**Preconditions:** an invoice exists whose due date is today.

**Steps to reproduce**

1. Start the application on a host in UTC — `TZ=UTC java -jar billing-app/target/billing-app.jar`,
   or any CI runner.
2. Create an invoice due today, Eastern time.
3. At 20:00 Eastern — 00:00 the next day in UTC — `GET /api/invoices/{id}`.

**Expected:** `"overdue": false`. It is still the due date for the business; the invoice has all
evening to be paid.

**Actual:** `"overdue": true`, and the next listing promotes the invoice to `OVERDUE`. The customer is
late by four hours of somebody else's calendar.

**Impact:** the business date was a property of the host, not a decision. Every date-dependent
behaviour inherited it: the `overdue` flag on the REST and SOAP responses, the `OVERDUE` promotion in
`InvoiceService`, the console's list and detail pages, and the whole seeded baseline, which is built
relative to "today". Moving the deployment between zones would silently change invoice statuses, with
nothing in the code to point at.

**Root cause:** `LocalDate.now()` resolves against `ZoneId.systemDefault()`. The domain was never the
problem — `Invoice.isOverdue(LocalDate asOf)` and `markOverdueIfDue(LocalDate asOf)` both take the
reference date as an argument, and say in their Javadoc that they do so deliberately. The defect was
entirely in who supplied that argument: five classes each called `LocalDate.now()` on their own,
so the answer came from the JVM's environment rather than from the application's configuration.

`Payment` had the same shape in a quieter form: `receivedAt = Instant.now()` inside the entity
constructor. Not zone-dependent, but an unstateable business fact — nothing could say when a payment
was recorded except the machine.

**Why 165 tests and 99% line coverage did not catch it**

Every suite evaluates the overdue rule in the same zone it was written in, and builds its fixtures from
`LocalDate.now()` read from that same default zone. The test and the code make the identical
assumption, so they agree with each other no matter what that assumption is. The suites also use
comfortable margins — due in 30 days, or 10 days past due — so no assertion was ever near the midnight
boundary where two zones disagree.

Coverage made it worse rather than better, in the sense that matters: those lines report as covered,
because they *were* executed. Line coverage records that a line ran. It cannot record that it ran with
the right date.

**Resolution:** a `Clock` bean built from `billing.time-zone`, defaulting to `America/Toronto`
(`BillingTimeConfiguration`), injected into `InvoiceService`, `InvoiceController`,
`InvoiceWebController`, `InvoiceStatusEndpoint` and `SeedDataLoader`. All ten `LocalDate.now()` calls
became `LocalDate.now(clock)`. `Invoice.applyPayment` now takes the receipt instant as an argument,
exactly as `isOverdue` takes the reference date, and the application supplies it from the same clock.

An unknown zone id fails the context at startup instead of falling back to the system default — a
silent fallback would restore the defect the bean exists to remove.

**Regression test:** `InvoiceOverdueBusinessZoneTest`. It freezes the clock at `2026-01-15T04:30Z`,
which is the **15th** in UTC and still the **14th** in `America/Toronto`, where it is 23:30 the previous
evening; an invoice due on the 14th must not be overdue, and one due on the 13th must be. The zone the
clock is frozen in is read back from `billing.time-zone`, so the test asks the application the question
its own configuration answers rather than restating a literal.

Run against the unfixed code it fails **4 of 6** — the `overdue` flag, the console's copy of it, the
seeded issue date and the payment timestamp all came from wall-clock time. Against the fix, 6 of 6
pass. The two that passed before the fix are the fixture's own straddles-midnight guard, and the
complement case: an invoice due on the 13th is overdue under either reading, which is what makes the
other assertions meaningful rather than a suite that says "never overdue".

**Deliberately left alone:** the two `Instant.now()` calls in `ErrorResponse`. An `Instant` carries no
zone, so `java:S8688` does not raise them and there is no zone bug to fix; the field is a diagnostic
stamp on an error envelope rather than a fact recorded about the business. Threading the clock through
a record's static factories and the exception handler would add wiring and change no behaviour. The
payment timestamp is different in kind, which is why it was changed and this was not.

**Lesson:** a test suite can only disagree with the code about things the two of them do not assume
together. Both sides here read the date from the same default zone, so no amount of additional tests
written the same way would have found it — and the coverage figure, being a record of execution rather
than of correctness, reported the defective lines as fully exercised. It took a tool that reads the
source instead of running it.

## DEF-013 — Language switch renders with no destination on a new page

**Severity:** Medium · **Priority:** Medium · **Component:** Web console / Spring MVC configuration

**Found by:** a test written for the agent console, `theLanguageSwitchKeepsTheAgentInPlace`. Not by
looking at the page — the switch is present, styled and clickable, and the fault is invisible until
somebody clicks it.

**Environment:** JDK 21, Spring Boot 3.5.16. Reproducible anywhere.

**Preconditions:** the agent console exists at `/agent` and renders `fragments/header`.

**Steps to reproduce**

1. Open `/agent`.
2. Click **Français**.

**Expected:** the same page, in French — the behaviour every other console page has.

**Actual:** the link has no usable destination. The header renders
`th:href="@{${currentPath}(lang='fr')}"` against a `currentPath` that is null on this page, so the
reader goes nowhere useful and the console is effectively English-only for them.

**Impact:** a bilingual console that is not bilingual on one of its screens. Low severity in the sense
that nothing is miscalculated; higher than it looks in the sense that the whole point of publishing in
both official languages is that a French reader is never stranded, and this stranded them silently.

**Root cause:** `WebPageModelAdvice` supplies `currentPath` and is declared
`@ControllerAdvice(assignableTypes = {InvoiceWebController.class, BillingAccountWebController.class})`.
The scoping is deliberate and correct — an untargeted advice would also run for every JSON endpoint —
but it means **a new screen has to be added to that list, and nothing fails when it is not**. Thymeleaf
renders a null model attribute as an empty string rather than raising, so the page is served with a
200 and looks finished.

**Resolution:** `AgentConsoleWebController` added to the advice, and a note in the class Javadoc saying
that any page rendering `fragments/header` belongs there.

**Regression guard:** `LocalisedConsoleWebTest.everyConsolePageOffersAWorkingLanguageSwitch` walks
**every console path** and asserts the switch points back at that path. Written against the list of
pages rather than the list of controllers on purpose: the controller list is the thing that was wrong,
so a test reading it would have agreed with the defect.

**Lesson.** This is the same shape as DEF-005 and DEF-007 — a configuration that silently does less
than it appears to. The pattern worth naming is *registration by enumeration*: anything that works by
listing the participants will eventually be missing one, and the test has to be written against the
population rather than against the list.

---

## DEF-014 — Documented test counts drifted apart from the suite

**Severity:** Low · **Priority:** Medium · **Component:** Documentation

**Found by:** an audit of the documentation before the 1.2.0 release. No test could have found it,
because no test read prose.

**Environment:** the repository at `bb00237`, before this release.

**Steps to reproduce**

1. Read `README.md:25` — "**171**, all passing".
2. Read `docs/test-report.md:12` — 165.
3. Read `docs/test-strategy.md:244` — nine defects. `README.md:29` — 12. `docs/test-report.md:301` —
   eleven.

**Expected:** one number per fact, everywhere.

**Actual:** eight figures disagreed across five documents:

| Claim | Said | Also said |
|---|---|---|
| Total tests | 171 (README, test-strategy) | 165 (test-report, code-quality) |
| Defects | 12 (README) | eleven (test-report), nine (test-strategy) |
| API tests | 52 (README) | 54 (test-strategy) |
| Application integration | 45 (README) | 39 (test-strategy) |
| Application's own tests | 72 (coverage) | 66 (test-report) |
| TestNG groups | 7/33/52 (README) | 6/32/50 (defect-reports) |
| `coverage.md:95` | "five members" | then lists six |
| `test-report.md:10` | `billing-app` 1.0.0 | the project was 1.1.0 |

**Impact:** low in itself — nothing is miscalculated and no user is affected. It matters because of
what it is evidence of. This repository's argument is that its claims can be checked; a reader who
finds two numbers for the same fact has been given a reason to stop checking the others. In a
commercial setting the same drift in a test summary report is what makes a release sign-off worthless.

**Root cause:** every figure was typed by hand, in several places, and updated by whoever remembered.
Adding roughly three hundred tests across four pull requests made the divergence obvious, but the
mechanism was there from the first document: **a number in prose has no author and no owner**, so it
ages the moment the thing it describes changes.

**Resolution:** the counts are now generated, not typed.

- `scripts/count-tests.sh` parses the surefire and failsafe XML and the Cucumber JSON of the last run
  into `docs/test-inventory.md`.
- Every headline figure in the documents is wrapped in a marker — `<!--count:api-->108<!--/count-->` —
  naming which inventory total it claims to be.
- `scripts/check-doc-numbers.sh` compares the two and exits non-zero on any disagreement, naming the
  file, the line, the claim and the truth.
- CI runs that check, so the pipeline fails on a stale number exactly as it fails on a broken test.

Every figure in the table above was reconciled in the same change.

**Why a marker rather than parsing the prose.** A check that guessed which numbers in a document were
test counts would have to ignore the seeded amounts, the performance figures, the coverage percentages
and the dates — and would either miss real drift or fail on numbers that are not claims about the
suite. The marker makes the author state the intent, which is the part a regex cannot recover.

**Lesson.** Documentation that can go stale silently is a defect with a long fuse, and the fix is the
same one applied to flaky tests: remove the human step. The screenshots in this repository were already
generated by the UI suite for precisely this reason; the counts had simply never been given the same
treatment.


## Observations across the fourteen

- **Six of fourteen were found by automated tests** (DEF-001, DEF-002, DEF-006, DEF-008, DEF-011,
  DEF-013), two by **manual exploratory checking** (DEF-003, DEF-004), three only by **deliberately
  verifying the tooling did what it was told** (DEF-005, DEF-007, DEF-009), one by **adding coverage
  measurement** (DEF-010), one by **static analysis** (DEF-012), and one by **auditing the
  documentation** (DEF-014).
- **DEF-011 was found only by CI.** It passed every local run, including the two-consecutive-passes
  isolation check. That is the clearest evidence in this repository for why the pipeline is the gate and a
  local green run is not.
- **That middle group is the argument for manual smoke checks.** DEF-003 and DEF-004 were both found by
  driving the application with `curl` before writing any automation. DEF-004 in particular would
  otherwise have become intermittent CI flakiness blamed on the tests.
- **The last group is the uncomfortable one.** DEF-005 and DEF-007 both produced *successful* output
  while doing the wrong thing. Neither would ever have been caught by adding more tests; they were
  caught by checking that a command had the effect it claimed.
- **DEF-012 is the one no test could have found.** Not "no test did" — no test written the way these
  tests are written could, because the suite and the application read the date from the same default
  zone and therefore agreed with each other. It is also the clearest limit on the coverage number in
  this repository: every line involved reported as covered, at 99%, throughout.
- **DEF-013 and DEF-014 are the same defect in two materials.** Both are a list that has to be kept in
  step with a population by somebody remembering: the controllers named in a `@ControllerAdvice`, and
  the test counts typed into prose. Both were fixed the same way — check the population, not the list.
