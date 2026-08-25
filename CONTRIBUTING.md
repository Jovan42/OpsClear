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

- **Subject line:** max 50 chars, imperative mood ("add" not "added")
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
| `chore` | Config, deps, build |

### Scope

Use the module or area affected: `auth`, `jobs`, `projects`, `docker`, `adr`, etc.

### Examples

```
feat(auth): add user registration endpoint

- Create User entity with email and password fields
- Add BCrypt password hashing in AuthService
- Create POST /auth/register endpoint
- Add validation for email format and password length
```

```
fix(jobs): prevent invalid status transitions

- Add status transition validation in JobService
- Return 400 error for invalid transitions
- Add unit test for transition rules
```

```
docs(adr): add authentication design decision

- Create ADR-0002 for JWT authentication
- Document token structure and expiration strategy
- Add alternatives considered section
```

```
chore(docker): add PostgreSQL to docker-compose

- Configure postgres:16 with health check
- Add persistent volume for data
- Set default credentials for local dev
```

---

## Branching Strategy

### Branch Naming

```
<type>/<short-description>
```

Examples:
- `feature/auth-jwt`
- `feature/jobs-crud`
- `fix/login-validation`
- `docs/api-readme`

### Workflow (GitHub Flow)

```
main (always deployable)
  └── feature/1.1-auth-adr
  └── feature/1.1-auth-user-entity
  └── feature/1.1-auth-jwt
  └── fix/login-validation
```

**Small, focused PRs** - one module can have multiple branches/PRs.

1. Create branch from `main`
2. Make focused changes (1-3 related tasks)
3. Push and create PR
4. Review (if team)
5. Merge to `main` (squash for feature branches)
6. Delete branch after merge

### Branch Naming

Pattern: `feature/<phase>.<module>-<description>`

Examples for Module 1.1 (Auth):
- `feature/1.1-auth-adr` - ADR documentation only
- `feature/1.1-auth-user-entity` - User entity + migration
- `feature/1.1-auth-jwt` - JWT service + filter
- `feature/1.1-auth-endpoints` - Controller + tests

Other examples:
- `feature/2.1-jobs-entity` - Job entity + migration
- `fix/auth-token-expiry` - Bug fix

### When to Branch

- **Direct to main:** Small docs fixes, typos, config tweaks
- **Feature branch:** Any module work (always)

---

## Pull Requests

### Title

Same format as commit messages: `<type>(<scope>): <short summary>`

### Body

```
## Summary

- <change 1>
- <change 2>

## Test plan

- [ ] <manual or automated check>

Closes #<issue-number>
```

### Rules

- **Always include `Closes #<issue>`** to auto-close the GitHub issue on merge
- **No tool/AI attribution** in PR descriptions
- **PR title = main commit message** for single-commit PRs
- **Tests ship with the PR** — every implementation PR must include its tests; there are no separate "write tests" PRs

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
