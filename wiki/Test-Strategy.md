# Test strategy

A summary. The full document, with every architectural decision and its trade-off, is
[`docs/test-strategy.md`](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/test-strategy.md).

## The levels, and what each is responsible for

The suites are layered so a failure points at a cause rather than at a symptom.

| Level | Tests | Runs against | Responsible for |
|---|---:|---|---|
| Domain unit | 27 | Plain objects | Billing arithmetic and state transitions |
| Application integration | 39 | Spring context + MockMvc | Controllers, mapping, HTTP status semantics |
| API automation | 108 | A running application over HTTP | The published REST and SOAP contract |
| UI automation | 60 | A real browser | The consoles a user actually operates |
| BDD | 43 | Both of the above | Behaviour stated in business language |
| Smoke | 12 | A real browser | The critical path, fast, on an independent toolchain |
| Performance | 1 plan | A running application | Behaviour under concurrent load |

## Deliberate departures from the usual advice

**The domain rules are tested at more than one level on purpose.** An overpayment is refused by a unit
test, by an integration test, by an API test, by a UI test and by a BDD scenario. That is not
redundancy for its own sake: each one answers a different question. The unit test asks whether the rule
is right; the API test asks whether the rule is *reachable* and answers with the right status code; the
UI test asks whether a person is told why. A rule that is correct and returns 500 passes the first and
fails the second.

**Every mutating test owns its data.** Fixtures are created through the API, never taken from the
seeded records. Pointing tests at a shared seeded invoice is the most common cause of suites that pass
alone and fail together, and of suites that pass once and fail on re-run because the first run consumed
the balance.

**Explicit waits only.** No implicit wait, no `Thread.sleep` anywhere in the UI modules. A sleep either
waits longer than necessary on every run or not long enough on a slow one, and tuning it trades one
failure mode for the other. Mixing implicit and explicit waits produces timeouts nobody can predict.

**The automation modules do not depend on the application.** They test the published contract and the
rendered DOM. Sharing an enum would mean renaming a constant changed the test and the application
together — the suite stays green while the contract breaks.

**400 versus 422 is a deliberate distinction.** A zero payment is `422 AMOUNT_NOT_POSITIVE`, not a
generic 400. Without the split, an assertion cannot tell "the client sent nonsense" from "the platform
applied a billing rule", and a test that only checks "an error happened" passes when the two are
swapped.

**Server-rendered rather than an SPA**, accepted with its cost. It keeps the stack Java-first and gives
the UI suites a deterministic DOM with no hydration race — and it means this project does not
demonstrate waiting on asynchronous client-side state, which is named as a limitation rather than left
to be noticed.

## What is deliberately not tested

| Not tested | Why |
|---|---|
| Authentication and authorisation | The application has none. Building half a security layer to test it would produce tests about a fiction. |
| Cross-browser | Chrome only. The console is server-rendered HTML with no JavaScript, so most of what a browser matrix catches does not apply. |
| Accessibility | Not covered. A real billing product would need it; this one does not claim it. |
| Database migrations | Schema is created from entities against in-memory H2. There are no migrations to test. |
| Mobile viewports | Usable, but no viewport assertions exist. |

## Related

- [Test report](Test-Report) — one full run, with evidence
- [Coverage and quality](Coverage-and-Quality) — what the coverage figure does and does not prove
- [Traceability matrix](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/requirements-traceability-matrix.md) — 23 requirements mapped to tests by class and method
- [Manual test cases](https://github.com/abdeljalilsennaoui/insurance-billing-qa-framework/blob/main/docs/manual-test-cases.md) — 22 cases, each marked automated or not
