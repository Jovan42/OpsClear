# Development Workflow

Two tracks cover all work: **feature work** and **maintenance work**.

---

## Feature track

### 1. Future Considerations

All product ideas live in the **Future Considerations project** (PRJ-002) as jobs. Each job is a high-level description of an idea — what it is, why it matters, known constraints and open questions. Nothing is scheduled or committed here. The backlog is reviewed at phase boundaries only.

### 2. Phase planning

When starting new work, pick a set of related ideas from the backlog and group them into a phase. Create a new project with a name that describes what the phase delivers. Break the project into milestones — one Future Consideration per milestone as a rule, though two very closely related ones may be combined.

Once a Future Consideration is promoted to a milestone, close the Future Consideration job.

### 3. ADR

The first job of every milestone is its Architecture Decision Record. Start with a product-level discussion: what exactly are we building, why, how does it behave from the user's perspective. Once that is agreed, go deeper into the technical design: schema, endpoints, component breakdown, constraints and edge cases.

Write the ADR document in `docs/dev/decisions/` using the next sequential number. Review and merge it before any implementation starts.

### 4. Job breakdown

After the ADR merges, split the milestone into implementation jobs. Each job should produce one pull request. The standard minimum split is:

```
DB migration → Backend → Frontend
```

Larger milestones can have multiple jobs per layer — `Backend 1`, `Backend 2`, `Frontend 1`, `Frontend 2` and so on. The only hard rule is that DB, backend, and frontend must never be combined in the same job. Each job is one layer only.

Set BLOCKED_BY relationships: backend jobs are blocked by the DB migration job, frontend jobs are blocked by the backend jobs they depend on. Job templates exist in OpsClear for each standard job type — use them when creating jobs to pre-fill the description structure. See [JOBS.md](JOBS.md) for naming conventions and splitting rules.

### 5. Implementation

One PR per job. Backend jobs include all tests — services covered by unit tests, all new endpoints covered by integration tests. Frontend jobs end with a manual testing checklist verified before the PR is opened. See [BACKEND.md](BACKEND.md) and [FRONTEND.md](FRONTEND.md) for technical conventions.

### 6. Deploy

Every merge to `main` triggers an automatic deploy. The app is used to manage itself, so production is exercised continuously.

---

## Maintenance track

Bugs, chores, and tech debt found at any time go directly into the **Maintenance project** (PRJ-001) — no phase planning, no ADR. Create a job, fix it, open a PR, merge.

Maintenance has three permanent milestones:

| Milestone | What goes here |
|-----------|---------------|
| Bugs | Defects and incorrect behaviour |
| Chores | Config, deps, CI, tooling, docs, refactors with no behaviour change |
| Tech Debt | Larger structural improvements not urgent enough for a phase |

Tech debt that grows large enough to need an ADR graduates to a feature phase.

---

## Branch naming

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/<job-id>-<short-description>` | `feature/JOB-109-org-templates-backend` |
| Bug fix | `fix/<job-id>-<short-description>` | `fix/JOB-115-slug-validation` |
| Chore | `chore/<short-description>` | `chore/update-dependencies` |

---

## Commit format

```
<type>(<scope>): <short summary>
```

**Types:** `feat` `fix` `docs` `refactor` `test` `chore`

**Scopes (fixed list):** `backend` `frontend` `db` `auth` `config` `ci` `docs` `deps` `infra`

Scope is always the layer or area — never the feature name.

Examples:
```
feat(backend): add org-level template CRUD
feat(db): V020 make project_id nullable, add org_id
feat(frontend): org templates section in OrgSettingsPage
fix(backend): return 404 when template belongs to different org
chore(deps): upgrade Spring Boot to 3.4.1
```

---

## Pull requests

One PR per job. The PR title mirrors the commit subject. The PR description references the OpsClear job it implements and the ADR it is based on.
