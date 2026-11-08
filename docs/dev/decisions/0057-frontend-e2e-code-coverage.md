# ADR-0057: Frontend E2E Code Coverage

**Status:** Proposed
**Date:** 2026-11-08
**Author:** Jovan

## Context

The backend reports and gates unit and integration coverage with Jacoco. The frontend has no coverage measurement at all. The Cypress suite from ADR-0049 (47 specs) is the frontend's only automated test layer, and we can't see which parts of the React app it actually exercises. Measuring that shows untested screens and code paths, and tells feature jobs whether their new specs reach the code they add.

Promoted from JOB-235 (Future Consideration, PRJ-002) into PRJ-013 / MIL-041. This ADR is JOB-296.

### Facts from the existing system that shape this design

- **E2E runs against the Vite dev server.** The `e2e-run` composite action (`.github/actions/e2e-run/action.yml`) starts Keycloak, the backend and `npm run dev`, then runs `npx cypress run --spec …`. Locally it's the same (`npm run dev` + `npm run e2e:run`, E2E.md).
- **Production is built separately.** `frontend/Dockerfile` runs `npm run build` (`tsc -b && vite build`) and serves `dist/` with nginx. Instrumentation that only hooks into the dev server can't reach the production bundle.
- **Two CI tiers** (ADR-0049, JOB-258, JOB-265): `e2e-smoke` runs only `@smoke`-tagged tests, on PRs. `e2e-full` runs every test across 4 manually balanced shards (matrix, `fail-fast: false`) on push to `main`, nightly and `workflow_dispatch`, and never gates a PR.
- **Test results** are already published with `dorny/test-reporter` from JUnit XML (`cypress-multi-reporters`, `cypress/results/`).
- **Backend coverage** uses `madrapps/jacoco-report` with a 90% gate. It reads Jacoco XML only, not lcov.
- **No frontend unit tests.** TECHNICAL.md lists Vitest + React Testing Library, but neither is installed. E2E is the only frontend coverage source.
- **Frontend code:** 187 `.ts`/`.tsx` files under `frontend/src/` (`api`, `auth`, `components`, `demo`, `dev`, `features`, `hooks`, `i18n`, `types`, `utils`, plus `main.tsx` and `router.tsx`). Vite 7, `@vitejs/plugin-react` v5, Cypress 15, `@cypress/grep` registered in `cypress.config.ts` and `cypress/support/e2e.ts`.
- **E2E coverage is a weaker signal than unit coverage**: a line counts as covered when any spec makes the browser run it, whether or not anything asserts on its result. JOB-235 asked to decide on a threshold deliberately rather than copy the backend's 90%.

## Decision

Instrument the frontend with **`vite-plugin-istanbul`**, active only when the dev server runs with `COVERAGE=true`. Collect coverage with **`@cypress/code-coverage`**, which is enabled only in that mode. In CI, only `e2e-full` collects: each shard uploads its raw coverage, and a new **`e2e-coverage`** job merges the shards with `nyc`. It writes totals and a per-folder table to the run summary and uploads the HTML report as an artifact. Coverage is **report only** and never fails a build. Developers and agents can run it locally with an opt-in script. No DB or backend changes.

## Product decisions

### Report only, no gate

Coverage numbers are published and never fail a build, a shard or a PR. There's no baseline yet, and E2E coverage measures reach, not correctness. Whether to add a threshold or a no-decrease ratchet is decided later from real numbers, as a new Future Consideration (see Consequences).

### Where it's collected

- **`e2e-full` only.** It runs the whole suite, so its number means "what the E2E suite exercises".
- **`e2e-smoke` is unchanged.** It runs only `@smoke` tests, so its number would look artificially low, and it stays as fast as today.
- Coverage therefore appears after each push to `main`, each nightly run and each manual `workflow_dispatch` run (which can be pointed at a branch to see coverage before merging).

### What's reported

- **Run summary** of the `e2e-coverage` job: overall lines, statements, functions and branches, plus one row per top-level `src/` folder (`api`, `auth`, `components`, `demo`, `dev`, `features`, `hooks`, `i18n`, `utils`, and root files).
- **HTML report** (`nyc` / Istanbul, per file and per line) and `lcov.info` uploaded as the artifact `e2e-coverage-report`.
- **No PR comments and no external service** (Codecov, Coveralls).
- **Every source file counts**, including files no spec ever loads (shown as 0%), so an untested screen shows up as a gap instead of being invisible.

### When a shard fails

The merge runs anyway. It reports from the shards that uploaded coverage, and the summary starts with a line saying which shards are missing and that the numbers are incomplete. It never fails the workflow.

### Local use

Opt-in, for developers and agents checking what their specs reach:

```bash
cd frontend
npm run dev:coverage   # COVERAGE=true vite: instrumented dev server
npm run e2e:coverage   # COVERAGE=true cypress run: collects, writes coverage/ (HTML + lcov + summary)
```

Plain `npm run dev` and `npm run e2e:run` behave exactly as today: no instrumentation, no collection, no warnings.

### Never in production

`vite-plugin-istanbul` runs only when `COVERAGE=true` is set and Vite is serving (`forceBuildInstrument: false`). `npm run build` and the Dockerfile don't set it, and the plugin ignores builds anyway, so the production bundle is unchanged.

## Technical design

### Database

None.

### API

None.

### Frontend (instrumentation and collection)

- **Dependencies (dev):** `vite-plugin-istanbul` (a version supporting Vite 7), `@cypress/code-coverage`, `nyc`.
- **`vite.config.ts`**: add `istanbul({ include: 'src/*', exclude: ['node_modules', 'cypress'], extension: ['.ts', '.tsx'], requireEnv: true, forceBuildInstrument: false })`. `requireEnv: true` makes it check `VITE_COVERAGE`, so the npm scripts set `VITE_COVERAGE=true` along with `COVERAGE=true`. The implementation job confirms the exact env name for the installed plugin version.
- **`cypress.config.ts`**: in `setupNodeEvents`, call `require('@cypress/code-coverage/task')(on, config)` (ESM import equivalent) **before** returning config, alongside the existing grep plugin and tasks. Set `config.env.coverage = process.env.COVERAGE === 'true'`, so collection is off unless asked for.
- **`cypress/support/e2e.ts`**: `import '@cypress/code-coverage/support'`, which skips itself when `Cypress.env('coverage')` is false.
- **`.nycrc.json`** in `frontend/`:
  ```json
  {
    "all": true,
    "include": ["src/**/*.ts", "src/**/*.tsx"],
    "exclude": ["src/**/*.d.ts", "src/main.tsx"],
    "extension": [".ts", ".tsx"],
    "reporter": ["html", "lcov", "json-summary", "text-summary"],
    "report-dir": "coverage",
    "temp-dir": ".nyc_output"
  }
  ```
  `main.tsx` is excluded because it only bootstraps the app. Everything else counts, including `src/demo` (exercised by the landing-page specs) and `src/dev/DesignPage` (a real route).
- **npm scripts:** `dev:coverage` (`COVERAGE=true VITE_COVERAGE=true vite`) and `e2e:coverage` (`COVERAGE=true cypress run`).
- **`.gitignore`**: `frontend/coverage/` and `frontend/.nyc_output/`.

### CI

**`e2e-run` composite action**: new input `coverage` (default `'false'`).
- When `'true'`: start the frontend with `COVERAGE=true VITE_COVERAGE=true npm run dev` and run Cypress with `COVERAGE=true`.
- After Cypress (`if: always()` and coverage on): upload `frontend/.nyc_output/` as artifact `e2e-coverage-raw-<artifact-name>` (`if-no-files-found: ignore`, short retention, e.g. 7 days).

**`e2e-full`**: pass `coverage: 'true'`. `e2e-smoke` doesn't, so it's unchanged.

**New job `e2e-coverage`** in `ci.yml`:

```yaml
e2e-coverage:
  name: E2E Coverage
  needs: e2e-full
  if: always() && needs.e2e-full.result != 'skipped'
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v7
    - uses: actions/setup-node@v7
      with: { node-version: '22', cache: 'npm', cache-dependency-path: frontend/package-lock.json }
    - run: npm ci
      working-directory: frontend
    - uses: actions/download-artifact@v4
      with: { pattern: 'e2e-coverage-raw-*', path: coverage-raw }
    # Copy each shard's out.json to frontend/.nyc_output/<shard>.json, note missing shards,
    # run `npx nyc report` (reads .nycrc.json, all: true), and write totals + per-folder
    # table from coverage/coverage-summary.json to $GITHUB_STEP_SUMMARY.
    - uses: actions/upload-artifact@v4
      with: { name: e2e-coverage-report, path: frontend/coverage, retention-days: 30 }
```

- **Merging raw coverage, not lcov**: `nyc report` over all shards' raw JSON combines hit counts exactly. Each shard loads different pages, so each one alone undercounts.
- **Missing shards**: the step compares the downloaded artifacts with the 4 shard names from the matrix and prints the "incomplete" line when any are missing. With no artifacts at all, it writes "no coverage collected" and exits 0.
- **Per-folder table**: a small inline Node script over `coverage-summary.json` groups files by their first folder under `src/`. No extra action.
- **Never fails**: report only. The job only fails on its own infrastructure errors (checkout, npm ci).

### Cost

Instrumented code runs slower, so each `e2e-full` shard takes longer (typically 10–30%). That's acceptable because `e2e-full` never gates a PR. `e2e-smoke` and production are unaffected.

### Process docs

- **E2E.md**: a "Coverage" section: what's measured and what it means (reach, not assertions), the local scripts, where to find the CI report, report only.
- **TECHNICAL.md**: a testing-table row for frontend E2E coverage (Istanbul via `vite-plugin-istanbul`, `@cypress/code-coverage`, `nyc`).
- The Vitest line in TECHNICAL.md that doesn't match the code is left to the process-docs audit (JOB-323).

### Constraints & edge cases

- **A new top-level `cypress/e2e/<area>/` folder missing from every shard** is skipped by `e2e-full` (ADR-0049 known trade-off), so its coverage is missing too. Nothing new here, and the `pr-reviewer` checklist already checks it.
- **`all: true` with TypeScript**: files never loaded are counted from source, so their totals can differ slightly from instrumented counts. That's acceptable for a report-only number.
- **Coverage of the Keycloak hosted pages** isn't measured. They aren't our React code.
- **Coverage of `src/demo`** comes from the landing and features-page specs, which run the demo with MSW in the browser. That's real app code and counts.
- **Local runs** collect only for the specs actually run. `e2e:coverage --spec …` gives a partial number by design.
- **`workflow_dispatch` on a branch** gives that branch's coverage, for a before/after comparison by hand.

## Alternatives considered

### Fixed threshold gate
Rejected (decided). There's no baseline, and E2E coverage measures reach, not correctness. A number picked now would be arbitrary.

### No-decrease ratchet against `main`
Deferred (decided). It needs a stored previous result and a baseline. It's a candidate for the follow-up Future Consideration.

### Collect on `e2e-smoke` (PRs) too
Rejected (decided). The smoke subset would give a misleadingly low number on every PR, and slow the PR tier.

### Run `e2e-full` on every frontend PR
Rejected (decided). That's about 4 extra shard runs per PR for a report-only number. `workflow_dispatch` on a branch covers the occasional need.

### Sticky PR comment (lcov reporter action)
Rejected (decided). It needs PR-time collection, which we don't do. The run summary and artifact are enough.

### Codecov / Coveralls
Rejected (decided). They need a third-party account and token for history graphs we don't need yet.

### `babel-plugin-istanbul` through `@vitejs/plugin-react`
Rejected (decided). Same output, but extra Babel config and a slower transform for every file.

### Native V8 coverage through the DevTools protocol
Rejected (decided). Less standard with Cypress, Chromium only, and it needs converting to Istanbul format for `nyc`.

### Only report files that specs load
Rejected (decided). Never-visited screens would vanish from the report and inflate the percentage.

### Skip the report when a shard fails
Rejected (decided). A partial, clearly labelled report is more useful than none, and nightly flakes would otherwise leave gaps.

### Totals only, or a per-file table in the summary
Rejected (decided). Totals alone don't show where the gaps are, and a per-file table is hundreds of rows. Per-file detail is in the HTML artifact.

### Coverage-only CI mode separate from `e2e-full`
Rejected. A second full run just for coverage would double the cost. Instrumenting the existing run is cheaper.

## Consequences

### Positive
- First visibility into what the frontend's only automated test layer actually exercises, broken down by area.
- Feature jobs can check locally whether their new specs reach the code they add.
- No effect on production, `e2e-smoke` or PR speed.

### Negative
- `e2e-full` shards get slower.
- Three new dev dependencies and a little conditional config in `vite.config.ts` and `cypress.config.ts`.
- Coverage is only visible after a merge (or a manual run), not on the PR itself.
- E2E coverage can look reassuring for code that runs but is never asserted on.

### Neutral
- After a few weeks of numbers, add a Future Consideration to PRJ-002: "Frontend E2E coverage threshold or ratchet". The Chore job's PR proposes it.
- E2E.md and TECHNICAL.md gain a coverage section and row.

## Implementation order

1. **Frontend 1: Istanbul instrumentation and Cypress coverage collection**: dev dependencies, `vite.config.ts` plugin behind `COVERAGE` / `VITE_COVERAGE`, `@cypress/code-coverage` in `cypress.config.ts` and `cypress/support/e2e.ts` behind `config.env.coverage`, `.nycrc.json`, `dev:coverage` / `e2e:coverage` scripts, `.gitignore`, E2E.md "Coverage" section (local use). Verify `npm run build` output has no `__coverage__`, and that plain `e2e:run` is unchanged. *Not blocked.*
2. **Chore 1: collect, merge and report E2E coverage in CI**: `coverage` input on `e2e-run`, raw-coverage upload per shard, `e2e-full` passes it, new `e2e-coverage` job (merge, missing-shard notice, totals and per-folder summary, HTML/lcov artifact), E2E.md CI part, TECHNICAL.md row, proposal of the threshold Future Consideration. Verified with a `workflow_dispatch` run on the branch. *Blocked by Frontend 1.*

## References

- ADR-0049: E2E test coverage (Cypress framework, CI tiers, shards)
- ADR-0056: CI process checks (layer rules these jobs follow)
- JOB-235 (Future Consideration, PRJ-002): original idea
- JOB-296 (PRJ-013 / MIL-041): this ADR's job
- JOB-206, JOB-258, JOB-265: E2E CI pipeline, smoke tagging, sharding
- JOB-323: process-docs audit (TECHNICAL.md Vitest mismatch)
