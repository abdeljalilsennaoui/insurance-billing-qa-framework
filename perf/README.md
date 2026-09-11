# Performance testing

A JMeter load plan over the invoice API, with the numbers actually measured on the machine used to
develop this project.

## What the plan does

`invoice-api-load.jmx` exercises three operations under concurrent load:

| Sampler | Request |
|---|---|
| `10 List invoices` | `GET /api/invoices` |
| `20 Get invoice` | `GET /api/invoices/{id}` |
| `30 Submit payment` | `POST /api/invoices/{id}/payments` |

Each thread first creates its own customer, policy and invoice in a once-only controller, then loops
over the three operations above.

**Why per-thread fixtures matter here.** If every thread paid against one shared invoice, they would
race to settle it and most payment requests would be refused with a legitimate `422`
`EXCEEDS_OUTSTANDING_BALANCE`. The run would show a high error rate that looks like a performance
problem and is nothing of the kind — it would be the application correctly enforcing a billing rule.
Each thread therefore owns an invoice with a deliberately large total (100,000.00) and pays 1.00 at a
time, so the balance cannot be exhausted within a run.

Everything is parameterised through `__P()` defaults, so nothing about the environment is baked in:

```bash
# Defaults: 10 threads, 5s ramp-up, 20 loops
JMETER_HOME=/path/to/apache-jmeter-5.6.3 perf/run-load-test.sh

# Heavier run against another host
HOST=staging.example.com JMETER_HOME=... perf/run-load-test.sh 20 10 25
```

JMeter is **not** a project dependency and the script installs nothing. It locates JMeter via
`JMETER_HOME` or `PATH` and fails with a clear message if neither is set. It also checks the
application's health endpoint first, rather than spending minutes producing numbers that describe
nothing.

## Measured results

Run of 2026-09-11, 10 threads, 5s ramp-up, 20 loops — 630 samples, **0% errors**.

| Sampler | Samples | Error % | Mean (ms) | p90 | p95 | p99 | Max | Throughput/s |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `10 List invoices` | 200 | 0.00 | 15.6 | 39.0 | 51.9 | 92.7 | 178 | 44.9 |
| `20 Get invoice` | 200 | 0.00 | 11.2 | 21.9 | 31.9 | 59.8 | 424 | 45.1 |
| `30 Submit payment` | 200 | 0.00 | 12.1 | 29.8 | 36.0 | 43.0 | 78 | 45.2 |
| `01 Create customer` | 10 | 0.00 | 64.2 | 396.7 | 435.0 | 435.0 | 435 | 2.3 |
| `02 Create policy` | 10 | 0.00 | 18.9 | 49.1 | 51.0 | 51.0 | 51 | 2.3 |
| `03 Create invoice` | 10 | 0.00 | 20.5 | 44.9 | 46.0 | 46.0 | 46 | 2.3 |
| **Total** | **630** | **0.00** | **14.0** | **31.0** | **39.0** | **62.7** | **435** | **138.6** |

Measured on: macOS, x86_64, JDK 21, single machine running both JMeter and the application, in-memory
H2 database, application started with `scripts/start-app.sh`.

### Reading these numbers honestly

**This is not a capacity measurement, and it should not be quoted as one.** Several things make it
unsuitable for that, and all of them are properties of the setup rather than the application:

- **The load generator and the application share one machine**, competing for the same CPU. Past a
  modest thread count the numbers describe contention between JMeter and the server, not server
  capacity.
- **The database is in-memory H2.** A real billing platform talks to a networked relational database,
  where query latency and connection pooling dominate. Those are exactly the costs this setup removes.
- **The dataset is tiny.** `GET /api/invoices` returns every invoice with no pagination; over tens of
  thousands of rows its cost would grow linearly and it would become the slowest operation by far.
- **The run is 5 seconds.** Long enough to exercise the path, far too short to show garbage collection
  behaviour, connection pool exhaustion, or memory growth.
- **No think time.** Threads hammer continuously, which is useful for finding a throughput ceiling and
  unlike real clerk behaviour.

The two outliers are worth naming rather than smoothing over. `01 Create customer` has a 435ms max
against a 64ms mean, and `20 Get invoice` shows a 424ms max against an 11ms mean. Both are first-call
costs: JIT warm-up and Hibernate preparing statements on the first execution of each query. A run with
a warm-up phase excluded from reporting would not show them. They are left in because hiding them
would make the table look better than the measurement was.

### What the plan is actually good for

- Confirming the API holds up under concurrency without errors — 630 requests, 0 failures, including
  200 concurrent payment writes with no lost updates or spurious rule rejections.
- Catching a regression in **relative** terms: if `20 Get invoice` goes from 11ms to 200ms after a
  change, that is a signal worth investigating regardless of absolute numbers.
- Demonstrating a parameterised, re-runnable plan that could be pointed at a properly provisioned
  environment without editing it.

### Not run in CI

Deliberately. A shared GitHub-hosted runner has variable and unpredictable CPU, so response-time
thresholds there would either be so loose they never fail or so tight they fail randomly — and a
performance gate that fires at random gets ignored, which is worse than not having one. The plan is
run manually against a known machine and its results recorded here.

Output lands in `perf/results/`, which is git-ignored: a `.jtl` file and an HTML report describe one
run on one machine and are not project artefacts.
