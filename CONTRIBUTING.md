# Contributing to OpsClear

## Process docs

Day-to-day workflow, job conventions, and layer-specific technical conventions live in
`docs/dev/process/`, not here:

- [WORKFLOW.md](docs/dev/process/WORKFLOW.md) — feature/maintenance tracks, phase planning, ADR-first
- [JOBS.md](docs/dev/process/JOBS.md) — job naming, splitting, and dependency conventions
- [BACKEND.md](docs/dev/process/BACKEND.md) — backend technical conventions
- [FRONTEND.md](docs/dev/process/FRONTEND.md) — frontend technical conventions
- [E2E.md](docs/dev/process/E2E.md) — when E2E coverage is required, sizing, naming, CI tiers

This file covers commit/PR/branch mechanics only.

## Commit Messages

### Format

```
<type>(<scope>): <short summary>

- <change 1>
- <change 2>
- <change 3>
```

### Rules

- **Subject line:** imperative mood ("add" not "added"), ideally under 72 characters including
  the job reference
- **Job reference:** end the subject with the OpsClear job ID when there is one: `(JOB-123)`
- **Blank line** after subject
- **Each `-` line:** one short sentence, one change
- **One commit = one logical change** (bullets explain the parts)

### Types

| Type | Use for |
|------|---------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation |
| `refactor` | Code restructure (no behavior change) |
| `test` | Tests only |
| `chore` | Config, deps, build, CI, tooling |

### Scope

Use a scope from the fixed list in [WORKFLOW.md](docs/dev/process/WORKFLOW.md#commit-format). Scope is
the layer or area, never the feature name.

### Examples

```
feat(backend): add org-level template CRUD (JOB-109)

- Add OrgTemplateController with list/create/update/delete
- Add service-level org membership checks
- Cover endpoints with integration tests
```

```
fix(frontend): keep job filters when switching projects (JOB-215)

- Store filters per project in the URL
- Add regression test
```

```
docs(adr): add ADR-0054 for Claude Code agents and skills (JOB-293)

- Document skills vs subagents split
- Add permissions table and scripts/opsclear CLI design
```

```
chore(deps): upgrade Testcontainers to 1.21.4 for Docker Engine 29 (JOB-310)

- Override the Spring Boot BOM version
```

---

## Branching

Branch naming is defined in [WORKFLOW.md](docs/dev/process/WORKFLOW.md#branch-naming)
(`feature/JOB-123-…`, `fix/JOB-123-…`, `chore/…`).

**Everything goes through a PR**, including small docs fixes. Never push directly to `main`.

1. Create a branch from the latest `main`
2. Make the changes for one job
3. Push and open a PR
4. Review
5. Squash-merge to `main` (this deploys automatically)
6. Delete the branch

---

## Pull Requests

### Title

Same as the commit subject: `<type>(<scope>): <short summary> (JOB-123)`

### Body

```
## Summary

- <change 1>
- <change 2>

## References
- Job: JOB-123 (PRJ-012 / MIL-034)
- ADR: ADR-0052 (if any)

## Test plan

- [ ] <manual or automated check>
```

### Rules

- **Reference the OpsClear job** (and the ADR, for feature work). Link the PR to the job in OpsClear.
- **No tool/AI attribution** in commits or PR descriptions: no `Co-Authored-By` trailer for Claude or any AI tool, and no "Generated with Claude Code" line.
  `.claude/settings.json` turns Claude Code's own footers off; don't add them by hand
- **PR title = commit subject** for single-commit PRs
- **Tests ship with the PR**: every implementation PR includes its tests; there are no separate
  "write tests" PRs

---

## Testing

Tests are written **in the same PR as the feature**, not deferred to end-of-phase.

- Every new service method → unit test
- Every new controller endpoint → integration test
- PRs without tests for new functionality will not be merged

---

## Code Review Checklist

_To be added when team grows_

---

## Coding Standards

_To be added (Java style, TypeScript style, etc.)_
