# Agile, Jira and Zephyr workflow

How this project was actually run, and how the same process maps onto the Jira and Zephyr tooling a QA
team would use.

> **Stated plainly:** GitHub Issues was the real tracker for this project. No Jira or Zephyr instance was
> involved. The mapping below describes how the work *would* be organised in those tools, and is written
> because the concepts and vocabulary are part of the job, not because the tools were used here.

## How this project was actually run

| Artefact | Where | Count |
|---|---|---|
| Backlog items | GitHub Issues | 30 |
| Release container | GitHub Milestone `v1.0 - QA Automation Portfolio` | 1, closed with 25 issues |
| Categorisation | GitHub labels (`feature`, `testing`, `api`, `ui`, `bdd`, `ci-cd`, `documentation`, `performance`, `soap`, `bug`, `automation`, `refactor`, `enhancement`) | 13 of 20 in use |
| Units of delivery | Pull requests, each closing one or more issues | 27 merged |
| Quality gate | GitHub Actions, 6 jobs, required green before merge | 1 pipeline |

The 1.1 and 1.2 releases were run from a written plan and a branch per pull request rather than from
new milestones; the milestone above covers the 1.0 scope it was opened for and was closed with it.

Each issue carried an **Objective**, **Requirements** and **Acceptance Criteria** as checkboxes, with
**Dependencies**, **Testing notes** or **Technical considerations** where they added information. That is
the same structure a well-written Jira story uses; only the field names differ.

## Mapping to Jira

| This project | Jira equivalent | Note |
|---|---|---|
| Issue labelled `feature` | Story | e.g. #5 "Implement invoice REST API" |
| Issue labelled `testing` / `automation` | Task, or a Story when it delivers a capability | e.g. #8 "Build REST Assured API test framework" |
| Issue labelled `bug` | Bug | The nine defects in `defect-reports.md` would each be a Bug here; in this project they were fixed inside the PR that found them and recorded in the defect log |
| Issue labelled `documentation` | Task | |
| Milestone `v1.0` | Fix Version, or Epic | |
| Acceptance criteria checkboxes | Acceptance Criteria field | |
| PR closing an issue | Linked development branch / "Resolves" transition | |
| CI job status | Build status via the Jira–CI integration | |

Issue states here are binary (open/closed). A Jira board would add the intermediate columns — To Do, In
Progress, In Review, Done — and the "In Review" column is where a QA engineer spends most of their time.

## Ceremonies, and what QA contributes to each

| Ceremony | QA contribution |
|---|---|
| **Backlog refinement** | Ask the questions that expose missing acceptance criteria. On this project the one that mattered was "what should happen when someone pays more than is owed?" — which produced REQ-04 and REQ-05, and the discovery that overpayment must be judged against the *remaining* balance rather than the invoice total. That distinction is the kind of thing that surfaces in refinement or in production, not in between. |
| **Sprint planning** | Size the test effort, not just the build effort. The Selenium suite took longer than the console it tests, because of DEF-006. |
| **Daily stand-up** | Flag blocked verification early. "The API suite is green but it was running against a stale build" (DEF-007) is a stand-up item, not something to discover at the end of a sprint. |
| **Development** | Write tests alongside the feature. In this project the API integration tests found DEF-001 and DEF-002 on the day the endpoints were written, not a sprint later. |
| **Code review** | Review the tests as carefully as the code. A test asserting only a status code, where two different rules both return 422, would pass review while proving nothing. |
| **Sprint review** | Demonstrate against acceptance criteria, including the refusal paths. A demo that only shows the happy path hides exactly the behaviour most likely to be wrong. |
| **Retrospective** | Feed defect patterns back. The pattern here: three of nine defects were in the *test infrastructure* and two produced green output while testing nothing — which argues for verifying that tooling does what it was told, not only that it reports success. |

## Test management in Zephyr

Zephyr (or Xray, or TestRail) adds structured test management on top of Jira. The equivalents for this
project:

| Zephyr concept | This project |
|---|---|
| **Test** | A case in [`manual-test-cases.md`](manual-test-cases.md), TC-001 to TC-022 |
| **Test Cycle** | One run of a suite against a build — what a CI job does here |
| **Test Execution** | A single test result within a cycle, with status and evidence |
| **Execution evidence** | The artifacts CI uploads: failure screenshots, Cucumber HTML reports, Failsafe XML, the application log |
| **Coverage link** | The requirement-to-test mapping in [`requirements-traceability-matrix.md`](requirements-traceability-matrix.md) |
| **Automation link** | The class and method names recorded against each requirement in that matrix |

A realistic structure would be:

```
Epic: Invoice billing
├── Story: Pay an invoice (REQ-01, REQ-02, REQ-03)
│   ├── Test: TC-001  Record a partial payment           [automated]
│   ├── Test: TC-002  Settle an invoice                  [automated]
│   └── Test: TC-003  Instalments add up exactly         [automated]
├── Story: Refuse invalid payments (REQ-04 … REQ-11)
│   ├── Test: TC-004  Overpayment refused                [automated]
│   ├── Test: TC-006  Zero and negative refused          [automated]
│   └── Test: TC-009  Cancelled invoice refuses payment  [automated]
└── Story: Invoice visibility (REQ-15, REQ-18)
    ├── Test: TC-013  List shows balances and statuses   [automated]
    └── Test: TC-019  Console legible at common widths   [manual]
```

The automated/manual flag matters: a test-management tool that reports 100% automation while four cases
are in fact manual is worse than one reporting 82% honestly, because the first number gets quoted in a
release decision.

## Definition of ready

An issue was only picked up when:

- the objective stated the outcome, not the implementation
- acceptance criteria were checkable rather than aspirational
- dependencies on other issues were identified
- it was small enough to finish in one pull request

## Definition of done

Applied to every one of the 14 pull requests:

- the feature works and was exercised manually at least once
- automated tests cover it at the appropriate level
- those tests pass in CI, on the pipeline, not only locally
- no test was skipped, disabled or weakened to get there
- affected documentation updated in the same PR
- the full diff self-reviewed before merge
- any defect found along the way fixed in the same PR, or recorded as an open issue

## Where this process differed from a real team

Honest differences, since they affect how this repository should be read:

1. **Review was self-review.** One contributor, so there was no independent approval. The PR checklists
   describe a structured self-review of the diff and say so; they never imply a second reviewer.
2. **No sprint boundaries.** The work ran continuously rather than in timeboxes. The milestone stands in
   for a release, not for a sprint.
3. **Refinement was a conversation with myself.** The questions a second person would have asked had to
   be asked deliberately — which is measurably weaker, and is part of why manual exploratory checking
   (which found DEF-003 and DEF-004) was worth the time.
4. **Estimation was not tracked.** No story points, no velocity. With one contributor and no commitment
   to anyone, the numbers would have measured nothing.
