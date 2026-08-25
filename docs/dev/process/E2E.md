# E2E Testing (Cypress)

ADR-0049 (MIL-031) established Cypress as the framework and delivered the one-time backfill
(PRJ-011). This doc is what turns that into an ongoing practice: when a new feature job must
include E2E coverage, how much, where the files go, and which CI tier picks it up. Without a
written, proportional bar here, E2E coverage either gets skipped silently on new work or turns
into a tax that slows every future PR — see ADR-0049's Product Decisions.

---

## When E2E is required

**Every feature-adding job from now on**, sized proportionally to what it ships — not the full
edge-case depth of the PRJ-011 backfill, which exists specifically to catch the codebase up once.
A new job that adds or changes user-facing behavior needs E2E coverage in the *same* PR — same
principle as a new service method needing a unit test, or a new endpoint needing an integration
test (`docs/dev/process/BACKEND.md`).

**Ships inside the Frontend job that adds the behavior**, not as a separate job — same as how
tests already live inside their implementation job per `JOBS.md`'s splitting rules, not split out
on their own. The `E2E:` job-type prefix is for E2E work substantial enough to be its own job:
this backfill's own per-area jobs (JOB-208 onward), and framework/infra work like JOB-203–207.
Day-to-day new-feature E2E coverage is a spec file added to the relevant Frontend job's PR, not a
new job in the tracker.

**Not required** for:
- Pure refactors with no behavior change
- Backend-only changes with no new/changed frontend surface (still covered by backend integration
  tests)
- Chores, config, CI/tooling changes

## Sizing guidance

Minimum bar for a new screen or feature: **one happy path + its distinct error states.** Not the
full happy/validation/edge-case catalog the backfill jobs use (see ADR-0049's Appendix for what
that looks like) — that depth is for the one-time backfill of already-shipped features, not the
ongoing bar for new ones. A genuinely complex new feature earns more cases; a small UI tweak to
existing, already-covered behavior may need none if the existing specs already exercise the
changed path.

When unsure how much is enough, err toward the smaller side and note what was deliberately left
out in the PR description — better to ship a proportional spec and flag the gap than block on
writing exhaustive coverage for a small change.

---

## Running locally

Requires the frontend dev server (and, for anything beyond the trivial smoke spec, the full local
stack — Postgres, backend, Keycloak per `docker-compose up`) running first.

```bash
cd frontend
npm run dev          # in one terminal — leave running

npm run e2e:open      # interactive runner, in another terminal
npm run e2e:run       # headless, same as CI
```

`baseUrl` defaults to `http://localhost:5173` (`cypress.config.ts`) and is overridable via the
`CYPRESS_BASE_URL` env var for CI or a non-default dev server port.

## Structure and naming

```
cypress/
  e2e/<feature-area>/<screen>.cy.ts   # one folder per ADR-0049 feature area
  support/
    e2e.ts        # loaded before every spec
    commands.ts   # custom commands (cy.loginAs(), JOB-204)
```

- **Feature-area folders** match ADR-0049's Appendix breakdown 1:1 (`cypress/e2e/job-list/`,
  `cypress/e2e/milestones/`, etc.) — reuse an existing folder for a change within that area; only
  add a new one for a genuinely new feature area, matching how `JOBS.md`'s job-type breakdown
  works.
- **One file per distinct screen/flow** within that area (`<screen>.cy.ts`) — e.g.
  `job-list/filters.cy.ts` alongside `job-list/grouping.cy.ts`, not one giant file per area.
- Spec titles read as sentences (`describe('Job List filtering', ...)`, `it('filters by status and
  priority together', ...)`) — they show up directly in CI annotations and the local runner, so
  they should be readable without opening the file.

## Authentication: `cy.loginAs(email)`

Standard auth pattern for every spec **except** the dedicated Auth Flows suite (JOB-208), which
drives the real hosted login/register/reset pages on purpose — per ADR-0049 §3.

```ts
cy.loginAs('alice@example.com'); // password defaults to the standard seeded demo password
cy.visit('/projects');           // token is seeded into every cy.visit() for the rest of the test
```

Works against any of the seeded demo users (`testuser`/`alice`/`bob`/`carol`/`dave@example.com`,
per `CLAUDE.md`'s demo users table — run `./scripts/seed.sh` first if they don't exist yet
locally). Fetches a real token from Keycloak's token endpoint directly (Resource Owner Password
Credentials grant — same request `scripts/seed.sh` itself already makes to verify the seed), no
login UI involved. Order relative to `cy.visit()` doesn't matter within a test — call it once,
every `cy.visit()` afterward picks up the session automatically.

---

## CI: smoke vs. full

Two jobs in `.github/workflows/ci.yml` (JOB-206), sharing the same provisioning via the
`.github/actions/e2e-run` composite action:

| Job | Runs on | What |
|-----|---------|------|
| `e2e-smoke` | Every PR touching `backend/**` or `frontend/**` | Fast subset — currently the full spec set (JOB-208+ is what gives smoke a real, smaller subset to run) |
| `e2e-full` | Push to `main`, nightly cron (03:00 UTC) | The entire catalog |

**For new spec files you add**: nothing to configure — both jobs currently run
`cypress/e2e/**/*.cy.ts`, so a new spec is picked up automatically by both. If a future job
introduces a real smoke/full split (e.g. tagging one happy-path test per area for `e2e-smoke`),
update this section then; until that exists, don't hand-pick which tier your spec runs in.

A backend-only PR still triggers `e2e-smoke` — E2E hits the real API, not mocks, so a backend
change can break a frontend flow with no frontend diff to show for it.

---

## Job type: `E2E`

`E2E` is now a standing job type, alongside `ADR`/`DB`/`Backend`/`Frontend`/`Infra` (see `JOBS.md`)
— introduced in PRJ-011 for this backfill, and the convention any project should use going forward
once it starts writing its own E2E specs, not something specific to this project.
