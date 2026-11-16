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
| `e2e-smoke` | Every PR touching `backend/**` or `frontend/**` | Only tests tagged `@smoke` |
| `e2e-full` | Push to `main`, nightly cron (03:00 UTC) | The entire catalog, tagged or not, sharded across 4 parallel jobs |

Both jobs scan the same `cypress/e2e/**/*.cy.ts` spec-pattern — the split is a
[`@cypress/grep`](https://github.com/cypress-io/cypress/tree/develop/npm/grep) tag filter, not a
separate glob. `e2e-smoke` passes `--expose grepTags=@smoke` (via the `grep-tags` input on
`.github/actions/e2e-run`); `e2e-full` doesn't pass it, so it runs everything.

**`e2e-full` sharding (JOB-265)**: `e2e-full` runs as a 4-job `strategy: matrix` (`E2E Full
(shard-1)` .. `(shard-4)` in the Actions UI), each shard given its own explicit list of
`cypress/e2e/<folder>/**/*.cy.ts` globs and provisioning its own Keycloak/backend/frontend
stack independently — this dropped wall-clock from ~35min sequential to roughly a quarter of
that, at the cost of ~4x the CI minutes (each shard re-pays the ~3-4min provisioning cost).
Shards are hand-balanced by folder, not by an auto-balancing tool like `cypress-split` — this
repo has no Cypress Cloud recording to source real historical per-test timing from, so
"auto-balance by real timing" would mean hand-maintaining a timing file anyway, no simpler than
hand-balancing folders directly in `ci.yml`.

**Adding a new top-level spec folder** (e.g. a brand-new `cypress/e2e/some-new-area/`): it must
be added to one shard's `spec-pattern` list in `ci.yml` by hand, or it silently never runs in
`e2e-full` at all (a shard's glob list only matches what's explicitly listed — there's no
catch-all "everything else" shard). Adding tests to an *existing* folder needs no change — its
shard's `**/*.cy.ts` glob picks up new files automatically.

**For new spec files you add**: tag your single clearest happy-path test so `e2e-smoke` picks it
up —

```ts
it('creates a widget with a name only', { tags: '@smoke' }, () => {
  ...
});
```

One tagged test per feature-area file is the convention (JOB-258) — not every happy-path case in
the file, just the one that best proves the feature works end to end. Skip tagging anything that
depends on a real multi-minute wait or other slow/flaky-under-load mechanics (see
`auth-flows/session.cy.ts`'s file comment for a worked example — its real ~5-minute token-refresh
wait is deliberately excluded from `@smoke` even though it's arguably that file's "happy path");
those stay `e2e-full`-only. An untagged spec still runs fine in `e2e-full`, it just won't gate PRs
quickly — don't leave a new feature area with zero `@smoke` coverage indefinitely, but it's not a
hard blocker for the PR that adds it.

A backend-only PR still triggers `e2e-smoke` — E2E hits the real API, not mocks, so a backend
change can break a frontend flow with no frontend diff to show for it.

---

## Coverage

ADR-0057 ([0057-frontend-e2e-code-coverage.md](../decisions/0057-frontend-e2e-code-coverage.md))
measures which frontend code the Cypress suite runs. Instrumentation is Istanbul through
`vite-plugin-istanbul` on the dev server, collection is `@cypress/code-coverage`, and the report is
`nyc`. All three are opt-in, so plain `npm run dev` and `npm run e2e:run` stay uninstrumented.

**What it measures: reach, not assertions.** A line counts as covered when any spec makes the
browser run it, whether or not anything asserts on the result. Use it to find screens and code
paths no spec reaches, not as proof that something is tested. Every `src/**/*.ts(x)` file counts,
so files no spec loads show up at 0% instead of disappearing. `src/main.tsx` (bootstrap only) and
`*.d.ts` are excluded (`frontend/.nycrc.json`; `src/main.tsx` also in the plugin's `exclude` in
`vite.config.ts`, since the local report doesn't apply nyc's `exclude` to instrumented files).

**Report only.** Coverage never fails a run, a CI job or a PR. There's no threshold.

### Running it locally

```bash
cd frontend
npm run dev:coverage      # instrumented dev server, in one terminal; leave running
npm run e2e:coverage      # in another terminal; collects and writes coverage/
npm run e2e:coverage -- --spec 'cypress/e2e/dashboard/**/*.cy.ts'   # only some specs
```

- `dev:coverage` sets `COVERAGE=true VITE_COVERAGE=true`. The plugin reads `VITE_COVERAGE`
  (`requireEnv: true` in `vite.config.ts`) and only instruments the dev server, never
  `npm run build`. In the browser console, `window.__coverage__` exists only in this mode. The
  plugin's "Sourcemaps was automatically enabled" warning at startup is expected here.
- `e2e:coverage` sets `COVERAGE=true`, which `cypress.config.ts` passes to the browser as
  `Cypress.expose('coverage')`. Without it the coverage hooks skip themselves.
- Output: `coverage/index.html` (per file and line), `coverage/lcov.info`,
  `coverage/coverage-summary.json`, and the totals in the terminal. Raw data goes to
  `.nyc_output/`. Both folders are git-ignored. The raw data accumulates across runs: delete
  `.nyc_output/` for a fresh number.
- A run with `--spec` gives a partial number by design: it only counts the specs that ran.
- Point Cypress at another port with `CYPRESS_BASE_URL` as usual. Specs that log in need a port
  that Keycloak's redirect URIs and the backend's CORS allow (only `5173` locally).

## Job type: `E2E`

`E2E` is now a standing job type, alongside `ADR`/`DB`/`Backend`/`Frontend` (see `JOBS.md` and
[NAMING.md](NAMING.md#job-titles))
— introduced in PRJ-011 for this backfill, and the convention any project should use going forward
once it starts writing its own E2E specs, not something specific to this project.
