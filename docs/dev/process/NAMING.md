# Naming

The single source for every naming rule in the dev process: job titles, branches, commits, PR titles
and bodies, the `cross-layer` label. `scripts/opsclear pr check` enforces the title, branch, body and
layer rules (ADR-0056, [0056-ci-process-checks.md](../decisions/0056-ci-process-checks.md)), and a
unit test fails if the types, scopes and branch prefixes below differ from the constants in
`scripts/opsclear`. Change a rule here and in the script in the same PR.

Check locally before opening a PR (needs no API key):

```
scripts/opsclear pr check --title '<PR title>' --body-file pr.md [--labels cross-layer]
```

Branch defaults to the current git branch, changed files to `git diff --name-only origin/main...HEAD`.

---

## Job titles

Job titles follow the pattern `<Layer>: <short description>`.

| Layer | Prefix | Example |
|-------|--------|---------|
| Architecture decision | `ADR:` | `ADR: org-level job templates` |
| Database migration | `DB:` | `DB: make project_id nullable, add org_id` |
| Backend | `Backend:` | `Backend: org-level template CRUD` |
| Frontend | `Frontend:` | `Frontend: org templates section in OrgSettingsPage` |
| E2E | `E2E:` | `E2E: cy.loginAs() custom command via Keycloak token grant` |
| Bug fix | `Bug:` | `Bug: 404 not returned for wrong-org template` |
| UX improvement | `UX:` | `UX: show the Complete button label on narrow screens` (commit `fix(frontend): …`) |
| Chore | `Chore:` | `Chore: upgrade Spring Boot to 3.4.1` |
| Tech debt | `Tech debt:` | `Tech debt: replace raw jOOQ inserts with model builder` |
| Future Consideration | (free title) | `Org-level job templates` |

Splitting and dependencies: [JOBS.md](JOBS.md).

## Branch names

Pattern: `<prefix>/<job-id>-<slug>`, slug lowercase `a-z0-9-`. The job ID must be one of the PR
title's jobs. Every branch starts from the latest `main`.

| Type | Pattern | Example |
|------|---------|---------|
| Feature (incl. ADR, DB, Backend, Frontend jobs) | `feature/<job-id>-<slug>` | `feature/JOB-109-org-templates-backend` |
| Bug fix, UX improvement | `fix/<job-id>-<slug>` | `fix/JOB-115-slug-validation` |
| E2E | `e2e/<job-id>-<slug>` | `e2e/JOB-224-approvals-flow` |
| Chore / Tech debt | `chore/<job-id>-<slug>` | `chore/JOB-310-testcontainers-docker29` |

## Commit subjects and PR titles

```
<type>(<scope>): <short summary> (JOB-123)
```

The PR title is the commit subject: PRs are squash-merged with the PR title and body as the commit
on `main`. A job ID is always required (work without a job gets a Maintenance job first). Several
jobs: `(JOB-1, JOB-2)`.

**Types:** `feat` `fix` `docs` `refactor` `test` `chore`

**Scopes (fixed list):** `backend` `frontend` `db` `e2e` `auth` `config` `ci` `docs` `deps` `infra` `adr`

Scope is always the layer or area, never the feature name. `adr` is for ADR documents
(`docs(adr): ...`). Cypress-only PRs use `test(e2e)` (or `fix(e2e)` for a flaky-test fix). Body
bullets and other commit details: [CONTRIBUTING.md](../../../CONTRIBUTING.md#commit-messages).

Examples:
```
feat(backend): add org-level template CRUD (JOB-109)
feat(db): V020 make project_id nullable, add org_id (JOB-110)
fix(backend): return 404 when template belongs to different org (JOB-115)
test(e2e): approvals flow (JOB-224)
docs(adr): add ADR-0054 for Claude Code agents and skills (JOB-293)
chore(deps): upgrade Spring Boot to 3.4.1 (JOB-310)
```

## PR body

- A `Closes JOB-x` line (merging finishes the job, the default) or `Part of JOB-x` (more PRs follow
  on the job), naming at least one of the title's jobs. Several jobs: `Closes JOB-1, JOB-2` or one
  line per job. Read at merge time by the job sync ([ADR-0055](../decisions/0055-github-job-sync.md)).
- No unfilled `JOB-___` placeholder.
- Text inside `<!-- -->` is ignored.
- **No AI attribution:** no `Co-Authored-By` line for an AI tool (Claude, Anthropic, Copilot,
  OpenAI, ChatGPT, Gemini, Cursor) and no "Generated with Claude Code" line, in commits or PRs. Don't quote these phrases in a PR
  body either: the check matches the "generated with ... claude code" phrase anywhere in the body,
  quoted or not.

Template and the other sections: [CONTRIBUTING.md](../../../CONTRIBUTING.md#pull-requests).

## Layers and the `cross-layer` label

A PR touches at most one of db / backend / frontend. Neutral paths can join any one layer.

| Layer | Paths |
|---|---|
| db | `backend/src/main/resources/db/migration/**`, `backend/src/main/generated/**` |
| backend | everything else under `backend/` |
| frontend | everything under `frontend/` except `frontend/cypress/**` |
| neutral | `frontend/cypress/**`, `docs/**`, `.github/**`, `.claude/**`, `scripts/**`, `api/**`, `keycloak/**`, root files |

If a PR can't be split (a migration plus its backend integration test), add the `cross-layer`
label: it passes the layer check only. The check also fails above 3000 changed files: split the PR.

## Other file names

- ADRs: `docs/dev/decisions/NNNN-<slug>.md`, see [ADR README](../decisions/README.md).
- Flyway migrations: `V{nnn}__{description}.sql`, see [BACKEND.md](BACKEND.md).
