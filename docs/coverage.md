# Code coverage

Line and branch coverage via JaCoCo, with one wrinkle that makes this more interesting than a plugin
and a percentage.

## The wrinkle: black-box suites are invisible to ordinary coverage

`jacoco:prepare-agent` instruments the JVM that Surefire forks. That captures the application's own 254
unit and integration tests perfectly well. It captures **nothing** from the API, UI and BDD suites,
because those drive the application over HTTP in a *separate process*. From the test JVM's point of
view, no class in `billing-app` was ever called.

The effect is not subtle. Measured on this project, the SOAP package — exercised exclusively by
black-box tests — reports **44% line coverage in-process and 94% once the agent is attached to the
application**. `BillingApplication.main` goes from 33% to 100%, because `main` only runs when the real
application starts.

Reporting the in-process number alone would therefore libel the black-box suites: it would show them
contributing nothing, when they are the only thing testing several classes.

## The two reports

| Report | What it measures | How to produce it |
|---|---|---|
| **In-process** | The application's own 254 unit and integration tests | `mvn clean install` — automatic, no flags |
| **Full-stack** | The above **plus** the API, UI, BDD and reset suites driving the live application | `scripts/coverage.sh` |

```bash
# In-process only — runs as part of any normal build
mvn -B clean install -DskipITs
open billing-app/target/site/jacoco/index.html

# Full stack — builds, starts the instrumented application, runs every black-box suite, merges
./scripts/coverage.sh
open billing-app/target/site/jacoco-full/index.html
```

### How the full-stack measurement works

1. `mvn clean install -DskipITs` produces `jacoco.exec` from the in-process tests.
2. `scripts/start-app.sh` is called with `JACOCO=true`, which adds
   `-javaagent:jacoco-agent.jar=destfile=jacoco-e2e.exec,output=file,append=true` to the application
   process. `append=true` matters: the suites are several separate Maven invocations against one
   application instance.
3. The API, UI and BDD suites run, then the `test-support` group last — it resets the database, so it
   must not run while anything else holds a fixture.
4. `scripts/stop-app.sh` stops the application. **The agent writes its data from a JVM shutdown hook, so
   the file does not exist until the process has exited** — which is why the script stops the application
   explicitly rather than leaving it to the exit trap.
5. `jacoco:merge` combines both `.exec` files and `jacoco:report` renders the merged result.

The agent jar is resolved through Maven (`dependency:copy` of `org.jacoco:org.jacoco.agent:runtime`) so
its version cannot drift from the plugin's.

## Measured coverage

| Metric | In-process | Full-stack |
|---|---|---|
| Line | 1200/1311 — **91.5%** | 1290/1311 — **98.4%** |
| Branch | 155/186 — **83.3%** | 163/186 — **87.6%** |
| Instruction | 5009/5524 — 90.7% | 5408/5524 — **97.9%** |
| Method | 353/385 — 91.7% | 376/385 — **97.7%** |
| Class | 72/75 — 96.0% | 75/75 — **100%** |

Measured on 2026-09-15 at 1.2.0, by `scripts/coverage.sh`. The application grew from 42 classes to 75
in this release, so these are not the same denominators as the 1.1.0 figures and the percentages should
not be read as a trend.

Generated code (`com/insurancebilling/soap/generated/**`, produced from the XSD by `xjc`) is excluded.
Coverage of generated code measures the generator, not this project's tests, and including it would pad
the figure with setters nobody wrote.

## Every remaining gap, named

Branch coverage is the honest number here: **87.6%**, and it improves only slightly with the black-box
suites, because most of what is missed is not reachable by any route through the application. **21
missed lines and 23 missed branches**, across sixteen classes — every one of them listed below.

| Class | Lines | Branches | What is missed, and why |
|---|---:|---:|---|
| `BankAccountReference` | 2 | 4 | Validation halves that a caller cannot reach: the record refuses a null holder and anything but three digits, and every construction path already filters those. |
| `BillingService` | 1 | 4 | Null-defaulting on optional request fields (`billingType`, `description`, bank details) whose absent case the API schema does not permit. |
| `Invoice` | 2 | 3 | The `policy == null` half of a null-and-state check, the cancelled guard in `refreshStatus`, and its `else → UNPAID` branch. All three protect against states that cannot arrive; unchanged from 1.1.0. |
| `BillingAccountWebController` | 2 | 2 | The empty-terms fallback and the unknown-tab fallback, both reachable only by hand-editing a URL. |
| `AgentConsoleWebController` | 2 | 1 | The empty-portfolio branch. Every run has seeded accounts, so an empty book never renders. |
| `Installment` | 2 | 1 | State transitions from a status the schedule generator never produces. |
| `PaymentInformationResponse` | 2 | 1 | The no-bank-details branch, for an account opened without them. |
| `SeedDataLoader$SeedDataWriter` | 1 | 2 | The idempotence guard. Reset deletes every row before `load()`, so the early return never fires. |
| `InvoiceStatusEndpoint` | 2 | 0 | `catch (DatatypeConfigurationException)`. The JDK always supplies a datatype factory. |
| `InvoiceWebController` | 0 | 2 | `form.getMethod() == null` and the `rawAmount == null` half of a null-or-blank check. An HTML form submits an empty string, never null. |
| `LocalisationConfiguration$SupportedLocalesOnly` | 1 | 1 | The branch taken when the request names a language the console is not published in *and* no cookie is set. |
| `PolicyTerm` | 1 | 1 | A guard against posting to a term that is not in force, refused earlier by the service. |
| `BillingAccount`, `BillingAccountController`, `BillingTransaction` | 1 each | 0 | Accessors with no caller in any current path. |
| `WebPageModelAdvice` | 0 | 1 | The blank-query-string branch; every console URL that reaches it has a path. |

Being able to account for every uncovered branch is worth more than the percentage. The figure could be
pushed to 100% by deleting the defensive guards or by writing tests that construct impossible states
through reflection — both of which would make the code worse to chase a number.

**One of these is a real gap rather than an unreachable one.** `AgentConsoleWebController`'s
empty-portfolio branch is perfectly reachable — it just needs a database with no accounts in it, which
no suite arranges because every suite seeds. It is named here rather than quietly counted among the
defensive ones.

## What coverage found

Adding coverage was not a documentation exercise; it located real problems that the test list could not
show, because nothing *looked* missing:

**Dead code — six members with zero call sites, now deleted:**
`InvoiceService.findByNumber`, `Policy.lapse()`, `Policy.getInvoices()`, `Customer.getPolicies()`,
`Money.round()`, `Payment.getInvoice()`. Earlier audits had asserted the repository contained no dead
code; coverage disproved that in one run.

**Two untested endpoints, now covered:**

- `POST /api/test-support/reset` was at **zero** lines covered. It is a real endpoint that deletes every
  row, and nothing tested it. Now covered by `ResetEndpointIT`, in its own `test-support` group —
  excluded from `regression` because the regression suite runs four threads in parallel and a reset would
  delete fixtures other tests were using, producing failures in innocent tests.
- `GET /api/policies/{id}` was never called. Now covered by `aPolicyCanBeRetrievedById` and
  `anUnknownPolicyIsNotFound`.

**A Surefire naming trap** — recorded as DEF-010. The reset test was first called
`TestSupportResetIT`, which matches Surefire's default `Test*.java` include despite ending in `IT`.
Surefire claimed it and ran it during `mvn install`, before any application existed, failing the build
on connection refused.

## What coverage does not tell you

Worth being clear about, because a high number invites the wrong conclusion:

- **It measures execution, not assertion.** A test that calls every method and asserts nothing scores
  identically to one that checks every outcome. 99% line coverage is consistent with a suite that proves
  nothing.
- **Branch coverage is the more honest metric**, and it is the lower one here — 87.6% against 98.4% line.
  Quoting only the line figure would be the flattering half of the truth.
- **It says nothing about the cases you did not think of.** Every payment rule is covered; coverage
  cannot tell me whether a rule is *missing*. Concurrent payments against the same invoice are untested,
  and `Invoice` has no `@Version`, so that is a plausible real defect — at 99% line coverage.
- **It is not a quality target.** No coverage gate is configured. A threshold chosen before seeing the
  numbers is arbitrary; one chosen to match them is decoration. The figure is published and the gaps are
  named instead.

## What CI publishes

Both reports, and the full-stack one is the figure that reaches the badges.

The build job uploads the **in-process** report as the `coverage-report-in-process` artifact, as it
always has. A `coverage` job then assembles the full-stack measurement from data the pipeline was
already producing:

| Job | What it contributes |
|---|---|
| `build` | `jacoco.exec` — the application's own 254 unit and integration tests |
| `api-tests`, `ui-tests`, `bdd-tests` | one `jacoco-e2e.exec` each, written by the agent inside the application process those suites drove |
| `coverage` | downloads all four, merges them, renders the report, sends it to Codecov and SonarQube Cloud |

The `cypress-smoke` job contributes nothing: it runs no Maven build, so there is no agent jar on that
runner, and its coverage would repeat what the Selenium suite already records.

Each suite job starts the application with `JACOCO=true scripts/start-app.sh` — the same switch
`scripts/coverage.sh` uses locally — and uploads what the agent wrote on shutdown.

**Nothing is re-run to produce this.** An earlier version of this document argued that the full-stack
figure would cost several minutes on every pull request, because it assumed the only way to get it was
to run every suite a second time inside one job. That is true of the obvious approach and not true of
this one: the suites already run, attaching the agent costs them almost nothing, and the extra job only
collects what they recorded.

One difference from a local `scripts/coverage.sh` run remains, and it was measured rather than
estimated at 1.1.0: CI reported **97.1%** line coverage against 99.0% locally, and compared class by
class the entire difference was `TestSupportController` — 7 of 16 lines in CI, 16 of 16 locally. The
`test-support` group wipes the database, so it is excluded from CI and runs last and alone locally. The
figures in this document are from a local run, which includes it.

**That class-by-class comparison has not been repeated at 1.2.0.** The cause has not changed and the
same gap is expected, but the exact CI percentage for this release is whatever the badge reports, not a
number restated here.

The tooling that consumes these reports — SonarQube Cloud, Codecov, and what each is and is not allowed
to block — is described in [`code-quality.md`](code-quality.md).
