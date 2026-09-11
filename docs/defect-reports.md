# Defect reports

Nine defects found while building and stabilising this project. Every one was actually encountered —
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

## Observations across the nine

- **Four of nine were found by automated tests** (DEF-001, DEF-002, DEF-006, DEF-008), two by **manual
  exploratory checking** (DEF-003, DEF-004), and three only by **deliberately verifying the tooling did
  what it was told** (DEF-005, DEF-007, DEF-009).
- **That middle group is the argument for manual smoke checks.** DEF-003 and DEF-004 were both found by
  driving the application with `curl` before writing any automation. DEF-004 in particular would
  otherwise have become intermittent CI flakiness blamed on the tests.
- **The last group is the uncomfortable one.** DEF-005 and DEF-007 both produced *successful* output
  while doing the wrong thing. Neither would ever have been caught by adding more tests; they were
  caught by checking that a command had the effect it claimed.
