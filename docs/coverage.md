# Code coverage

Line and branch coverage via JaCoCo, with one wrinkle that makes this more interesting than a plugin
and a percentage.

## The wrinkle: black-box suites are invisible to ordinary coverage

`jacoco:prepare-agent` instruments the JVM that Surefire forks. That captures the application's own 66
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
| **In-process** | The application's own 66 unit and integration tests | `mvn clean install` — automatic, no flags |
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
| Line | 445/489 — **91.0%** | 484/489 — **99.0%** |
| Branch | 48/54 — **88.9%** | 48/54 — **88.9%** |
| Instruction | 1892/2070 — 91.4% | 2049/2070 — **99.0%** |
| Method | 151/164 — 92.1% | 163/164 — **99.4%** |
| Class | 40/42 — 95.2% | 42/42 — **100%** |

Generated code (`com/insurancebilling/soap/generated/**`, produced from the XSD by `xjc`) is excluded.
Coverage of generated code measures the generator, not this project's tests, and including it would pad
the figure with setters nobody wrote.

## Every remaining gap, named

Branch coverage is the honest number here: **88.9%, and it did not improve with the black-box suites**,
because the six missed branches are not reachable by any route through the application. All six, plus
the five missed lines, are listed below. None is a hole in the testing; each is defensive code whose
failing side cannot occur.

| Location | Missed | Why it is unreachable |
|---|---|---|
| `Invoice:118` | `policy == null` half of `policy != null && !policy.isActive()` | Every invoice is created against a policy. Only a unit test constructing a detached `Invoice` could take this branch. |
| `Invoice:182` | `if (status == CANCELLED) return;` in `refreshStatus` | `refreshStatus` runs only after a payment is accepted, and a cancelled invoice refuses payment earlier at line 105. The guard protects against a state that cannot arrive. |
| `Invoice:187, 190` | the `else { status = UNPAID; }` branch | `refreshStatus` runs only after a payment has been added, so `amountPaid` is always positive. |
| `InvoiceWebController:78` | `form.getMethod() == null` | The form's `<select>` always submits a value. |
| `InvoiceWebController:97` | `rawAmount == null` half of `rawAmount == null \|\| rawAmount.isBlank()` | An HTML form submits an empty string, never null, so only `isBlank()` is reached. |
| `SeedDataLoader:79, 80` | `if (customers.count() > 0) return;` | An idempotence guard. Reset deletes every row before calling `load()`, so the early return never fires. |
| `InvoiceStatusEndpoint:76, 77` | `catch (DatatypeConfigurationException)` | The JDK always supplies a datatype factory. |

Being able to account for every uncovered branch is worth more than the percentage. The figure could be
pushed to 100% by deleting the defensive guards or by writing tests that construct impossible states
through reflection — both of which would make the code worse to chase a number.

## What coverage found

Adding coverage was not a documentation exercise; it located real problems that the test list could not
show, because nothing *looked* missing:

**Dead code — five members with zero call sites, now deleted:**
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
- **Branch coverage is the more honest metric**, and it is the lower one here — 88.9% against 99.0% line.
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
| `build` | `jacoco.exec` — the application's own 66 unit and integration tests |
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

One difference from a local `scripts/coverage.sh` run remains. The `test-support` group is excluded from
CI, because it wipes the database and cannot run beside anything else, so `TestSupportController.reset`
reads as uncovered there and covered here. The numbers in this document are from a local run, which
includes it.

The tooling that consumes these reports — SonarQube Cloud, Codecov, and what each is and is not allowed
to block — is described in [`code-quality.md`](code-quality.md).
