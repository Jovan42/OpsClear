# Contributing to OpsClear

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
  └── feature/auth-jwt
  └── feature/jobs-crud
  └── fix/login-validation
```

1. Create branch from `main`
2. Make commits following convention above
3. Push and create PR
4. Review (if team)
5. Merge to `main` (squash for feature branches)

### When to Branch

- **Direct to main:** Small docs fixes, typos, config tweaks
- **Feature branch:** New features, bug fixes, refactors

---

## Code Review Checklist

_To be added when team grows_

---

## Coding Standards

_To be added (Java style, TypeScript style, etc.)_
