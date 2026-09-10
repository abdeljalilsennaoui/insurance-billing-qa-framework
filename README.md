# Insurance Billing QA Framework

End-to-end QA automation framework for a simulated insurance billing platform.

The repository contains two halves that are deliberately kept separate:

- **The system under test** — a Spring Boot insurance billing application (customers, policies,
  invoices, payments) with a REST API, a SOAP endpoint and a server-rendered web console.
- **The QA automation** — API, UI, BDD, smoke and performance suites that exercise that
  application the way a test automation engineer would on a real billing platform.

## Module layout

| Module | Purpose |
|---|---|
| `billing-app` | Spring Boot 3 application under test (domain, REST API, SOAP endpoint, web console) |
| `qa-api-tests` | REST Assured + TestNG API automation |
| `qa-ui-tests` | Selenium 4 + Page Object Model UI automation |
| `qa-bdd-tests` | Cucumber feature files and step definitions covering UI and API |
| `cypress` | Cypress smoke suite |
| `perf` | JMeter performance plan |
| `docs` | Test strategy, manual test cases, traceability matrix, defect reports |

## Prerequisites

- JDK 21
- Maven 3.9+
- Google Chrome (for the Selenium and Cypress suites)
- Node.js 20+ (for the Cypress suite)

## Status

Under active development. Each area is tracked as a GitHub issue and delivered through a pull
request; see the issue list for current progress.
