# Contributing

This document records the engineering conventions used in this repository so that the Git history
stays readable and every change is traceable back to a tracked piece of work.

## Workflow

```
Issue  ->  feature branch  ->  commits  ->  tests  ->  pull request  ->  CI  ->  review  ->  merge  ->  close issue
```

Work is never committed directly to `main`. Every change starts from a GitHub issue and lands
through a pull request that references it.

## Branch naming

| Prefix | Used for |
|---|---|
| `feature/` | application functionality (domain, API, UI, SOAP) |
| `test/` | automation frameworks and test suites |
| `ci/` | pipeline configuration |
| `docs/` | documentation |
| `fix/` | defect fixes and test stabilisation |
| `refactor/` | structural change with no behaviour change |
| `chore/` | build files, tooling, repository housekeeping |

Branches are short-lived and scoped to a single issue or closely related group of issues. Remote
branches are deleted once their pull request is merged.

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/):

```
<type>: <imperative summary>

Optional body explaining what changed and why.
```

Types in use: `feat`, `fix`, `test`, `refactor`, `docs`, `ci`, `chore`, `build`.

Each commit is one logical development checkpoint and leaves the build in a coherent state. Commits
are not padded, split artificially, or rewritten after they are pushed, and commit dates are never
modified.

## Merge strategy

**Standard merge commits.** Pull requests are merged with `--merge`, not squashed.

The reason is that the individual commits inside a pull request carry information worth keeping: the
order in which a framework was built up, and in particular the `fix:` commits that followed a test
failure. Squashing a ten-commit pull request into a single commit would erase exactly the part of
the history that shows how problems were diagnosed and resolved.

Merge commits keep the per-PR grouping visible (`git log --first-parent` reads as one line per
pull request) while `git log` still shows the detailed work.

## Pull requests

Every pull request states its summary, the changes it makes, how it was tested, and the issue it
closes. Before merging:

- CI is green. A failing pipeline is fixed, never bypassed and never worked around by disabling a
  test.
- The full diff has been reviewed.
- No secrets, credentials or environment files are included.
- No dead code, commented-out blocks, or disabled tests without a documented reason.
- Documentation is updated when behaviour or commands change.

This is a single-contributor repository, so the review step is a structured self-review of the diff
rather than an independent approval.

## Definition of done

An issue is closed only when the feature is implemented, automated tests cover it, those tests pass
in CI, and any affected documentation has been updated.
