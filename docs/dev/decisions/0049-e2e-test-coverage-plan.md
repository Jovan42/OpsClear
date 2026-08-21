# ADR-0049: E2E Test Coverage — Framework, Process, and Full Backfill

**Status:** Proposed
**Date:** 2026-08-20
**Author:** Jovan Manojlovic

## Context

Cypress is named in `CLAUDE.md`'s tech stack table but nothing is actually configured anywhere
in the repo — no config, no CI wiring, no test files. JOB-186's original Future Consideration
scoped this as a phased approach (tooling → golden path → one happy path per add-on → incremental
edge-case growth). That scope has been deliberately expanded: **full, comprehensive E2E coverage
of every feature already shipped is now the goal**, not a golden path plus incremental growth.
Tracked as its own multi-milestone project, PRJ-011:

1. **MIL-031 — E2E framework & process**: the tooling and the ongoing policy for how E2E gets
   handled going forward.
2. **MIL-032 — Backfill: core product features**: retroactive coverage of everything already
   shipped outside billing, one job per feature area.
3. **MIL-033 — Backfill: billing (Paddle)**: kept separate because payment-flow testing needs a
   fundamentally different approach (webhook simulation, sandbox test cards) than standard
   browser E2E.

This ADR is the output of a full code-first audit of the repository — every backend controller,
every frontend feature folder, and every merged ADR (0002–0048) — done specifically to answer
JOB-195's five open questions with evidence rather than guesses, and to produce the actual test
catalog rather than just a plan to write one later. The catalog is in the Appendix; this section
covers the framework decisions the catalog depends on.

## Decision

Stand up Cypress against a real, fully-provisioned stack (Postgres + backend + Keycloak), gated
into CI as a new job following the existing `paths-filter` pattern, with a two-tier run strategy
(fast smoke suite on every relevant PR, full comprehensive suite on merge to main) and a written
process doc making E2E a standing requirement for new feature work, not just a one-time backfill.

## Resolved open questions

### 1. Feature-area breakdown

The originally proposed ~14 areas were too coarse once actually measured against the code — the
real feature surface splits into **21 backfill jobs under MIL-032** plus **4 under MIL-033**, and
**4 ADRs turned out to have no implementation to test at all**:

**MIL-032 — Core product features (21 jobs, see Appendix for the full per-job catalog):**

| # | Feature area | ADRs |
|---|---|---|
| 1 | Auth flows (login / registration / password reset — real Keycloak UI) | 0002, 0036 |
| 2 | Organisation management (create, settings, members, invites) | 0026, 0005, 0027 |
| 3 | API Keys | 0025 |
| 4 | User Settings & Preferences (theme, defaults) | 0018, 0019, 0023 |
| 5 | Job List & Filtering | 0014 |
| 6 | Job Create/Edit (incl. template use, markdown toolbar) | 0007, 0039 |
| 7 | Job Status Transitions & Blocking | 0007, 0008, 0020 |
| 8 | Notes | 0009 |
| 9 | Job Relationships | 0022 |
| 10 | Status History | 0028 |
| 11 | Projects (CRUD, lifecycle, members) | 0004, 0013, 0005 |
| 12 | Milestones | 0021, 0024 |
| 13 | Job Templates — project & org-level | 0032, 0033, 0042 |
| 14 | Job & Project Links | 0035 |
| 15 | Recurring Schedules (+ missed runs + cron preview) | 0034 |
| 16 | Job Types | 0042 |
| 17 | Approvals workflow + queue | 0010, 0016 |
| 18 | Dashboard | 0017 |
| 19 | Public Landing Page & `/features` interactive demos | 0029, 0040 |
| 20 | Localization (i18n) smoke coverage | 0037 |
| 21 | Mobile Nav, Dark Mode, Empty States (cross-cutting UI) | 0038, 0019, 0041 |

**MIL-033 — Billing (4 jobs, its own testing approach — see §2):**

| # | Feature area |
|---|---|
| 22 | Subscription selection & feature gating |
| 23 | Paddle checkout / upgrade / downgrade / cancellation (webhook-driven lifecycle) |
| 24 | Billing history & past-due state |
| 25 | Super Admin: Pricing config, Credit grants, Customer feedback |

**Not implemented — excluded from backfill, not a gap in this ADR:**
ADR-0045 (Org-Wide Project Directory), ADR-0046 (Cross-Project Approval Queue), ADR-0047 (Quick
Project Switcher), ADR-0048 (Manual Refresh Button) are all `Proposed` with zero code behind
them — no endpoints, no pages, no components (confirmed by grep across the full tree). Scheduling
E2E backfill work against code that doesn't exist would be nonsensical busywork. Per the process
doc (§4 below), each of these gets its E2E coverage written *alongside* its eventual
implementation job, not before.

**Plus one golden-path job, not in the table above** — a full-lifecycle integration simulation
supplementing the 21 area jobs, not a 22nd area. See §6 below and Appendix §26.

### 2. Billing/Paddle E2E approach

Not one approach — three, chosen per test case based on what it actually exercises (full
breakdown per case in the Appendix, MIL-033):

- **(a) Browser E2E against Paddle's sandbox + test card numbers** — reserved for the checkout
  UI shell only (opening the inline frame, the running-total summary above it, abandon/back).
  Driving an actual card payment through Paddle's iframe from Cypress is slow and flaky; this
  tier is deliberately kept small.
- **(b) Direct webhook POST simulation** — the dominant approach. `POST /api/webhooks/paddle`
  with a hand-signed body (HMAC-SHA256, matching `PaddleWebhookService`'s verification) drives
  the entire subscription lifecycle, credit consumption, idempotency, and out-of-order delivery
  deterministically, with no dependency on Paddle's own sandbox being reachable or stateful
  between runs.
- **(c) Backend integration test, not E2E at all** — logic with no meaningful UI surface
  (signature-verification internals, price-sync-to-Paddle mechanics, mixed-change-detection
  math) stays in `@SpringBootTest`/Testcontainers, per the existing `onlyIntegrationTests`
  convention, rather than being forced into Cypress.

### 3. Auth strategy in CI

Backend integration tests today mock the JWT entirely (`jwt().jwt(jwt -> ...)` per
`CLAUDE.md`) and CI has never run against a real Keycloak instance — confirmed via `grep` across
both `backend/src/test` and `.github/workflows/`. That's sufficient for backend tests but not for
E2E, since ADR-0036's actual deliverable is the *branded Keycloak login/register/reset pages
themselves* — those can only be verified by driving real Keycloak.

Decision: **run a real Keycloak container in CI** (realm import from the existing dev realm
export, alongside the Postgres service the `integration-tests` job already provisions) and split
auth handling into two lanes:
- **Feature area #1 (Auth flows)** drives the actual hosted login/register/reset-password pages
  through the browser — this is the one area where UI-driven auth is the point of the test.
- **Every other feature area** authenticates via a Cypress custom command
  (`cy.loginAs('alice@example.com')`) that hits Keycloak's token endpoint directly with the
  Resource Owner Password Credentials grant using the seeded demo users' real credentials
  (`password123`, per `CLAUDE.md`'s demo users table) and injects the resulting token into the
  app's expected storage — bypassing the login UI so that, e.g., a Milestones test isn't also
  re-testing login on every single run.

### 4. Test data strategy

`scripts/seed.sh` already does a full reset-and-repopulate against Keycloak + Postgres for local
dev — the CI job adapts the same shape rather than inventing a new one: **truncate + reseed once
per E2E job run**, not per-spec and not per-test (too slow at hundreds-of-cases scale), against a
known, deterministic dataset (the same demo org/users `seed.sh` already produces, so the same
fixtures work for both local Cypress runs and CI). Individual specs that mutate state (creating a
job, changing a subscription) clean up via API calls in their own `afterEach`/`before` hooks
rather than relying on database-level isolation between specs.

### 5. Where E2E tests run

Given the full comprehensive suite is large (25+ feature-area jobs, each with a full
happy/error/edge catalog per the Appendix), running everything on every PR would materially slow
down every future feature PR — explicitly a constraint JOB-195 already flagged. Two-tier run
strategy, following the existing `dorny/paths-filter`-gated job pattern
(`.github/workflows/ci.yml`):

- **Smoke tier** (new `e2e-smoke` job): one happy-path test per feature area (~25 tests), gated
  on `needs.changes.outputs.frontend == 'true' || needs.changes.outputs.backend == 'true'` (E2E
  hits real backend endpoints, not mocks, so a backend-only PR can still break it) — runs on
  every PR.
- **Full tier** (new `e2e-full` job): the entire catalog in the Appendix, runs on merge to `main`
  only (not on every PR), plus nightly on a schedule to catch time-sensitive regressions (cron
  DST edges, credit/discount expiry windows) that don't correlate with any specific commit.

### 6. Golden-path full-lifecycle simulation (added before implementation started)

A gap surfaced while scoping implementation, before any of MIL-031/032/033 had started: every case
above, however exhaustive, is either one feature in isolation or a single-actor role-permutation
check (log in as role X, assert 200/403 on one action). None of them tell one continuous story the
way an actual team would use the app — org stood up, people invited and added, a project built
out, its jobs followed through to completion by genuinely different means. This class of bug (does
the Dashboard actually reflect a live sequence of cross-user mutations correctly; does state
survive several actors handing a job back and forth) is structurally invisible to per-area specs
no matter how exhaustive they are.

Resolution: **one additional job** — not a 22nd feature area, a cross-cutting integration
narrative — supplementing the 21 MIL-032 area jobs, not replacing any of them. Full scenario in
Appendix §26.

- **Cast**: Alice creates the org (becomes Owner) → invites Bob by email (accepts, becomes Admin)
  → Alice/Bob add Carol, Dave, and a fifth member to the org (already-have-accounts path, not
  invite) → all added as members of one new project.
- **Governing principle: prefer a fixture over a live external dependency, everywhere except the
  one test whose actual job is verifying that external integration itself.** MIL-033's own
  (a)/(b)/(c) tagging already does this for most billing cases (upgrade/downgrade logic, past-due
  state, credit consumption all run against webhook-simulated or fixture state, not a live
  checkout, precisely to avoid Paddle-sandbox flakiness) — the only cases that genuinely need live
  Paddle are the ones in the checkout job (§1, area 23) whose actual subject is the checkout
  screen. This scenario's own subject is jobs/notes/approvals/dashboard working together across
  actors, not checkout — checkout is already exhaustively covered elsewhere — so the same
  principle applies here too.
- **Alice's org gets a fixture-seeded active subscription, not a real Paddle checkout.** A DB seed
  step (extending the E2E reseed pattern, §4) inserts an `org_subscriptions` row shaped like a
  real one — a realistic `paddle_subscription_id`, `subscription_status: ACTIVE`, a tier/add-on
  selection covering everything the story touches — rather than routing Alice through Paddle's
  actual sandbox checkout. This is deliberately **not** `is_internal: true`: an internal org skips
  `hasAddon()`/`hasRealBilling()` checks entirely, a different code path than what a real paying
  customer hits; a fixture row with realistic fields exercises the same gating logic a real
  subscription would, just without the network round-trip to establish it. Removes the only
  external-network dependency this scenario would otherwise have — no live Paddle call, no
  webhook-delivery problem to solve, no CI dependency on Paddle sandbox reachability at all.
- **Three jobs, three different real-world paths, three different assignees**:
  - Carol's job: straightforward — New → In Progress → Completed, a note added, a link attached
    (service-icon auto-detection exercised for free)
  - Dave's job: friction — New → In Progress → Blocked (with reason) → unblocked → Completed, a
    `BLOCKED_BY` relationship to another job, a note added by a *different* actor than the
    assignee (Bob checking in)
  - Fifth member's job: gated — New → In Progress → approval requested → Alice reviews the
    approval queue → approves → Completed
- **Cross-checks woven through the story, not just asserted at the end**: the Dashboard's
  Blocked/Overdue/Pending-Approvals sections are checked as state actually changes, and role
  boundaries are spot-checked inline as they naturally arise (Carol can't decide the pending
  approval, can't reopen a Completed job, can't delete the project) rather than as separate
  isolated tests — proving the boundary holds mid-flow, not just in a purpose-built permission
  test.
- **Runs in `e2e-smoke`, every PR**, not `e2e-full`. Unlike the exhaustive catalog (the actual
  target of this ADR's "don't slow every PR" constraint above), this is one bounded spec, not a
  combinatorial explosion of cases — and its purpose is specifically to catch an integration
  regression *before* merge. Running it only post-merge/nightly would defeat that purpose. Since
  step 1 is now a DB fixture rather than a live Paddle call, this tier placement carries no
  external-service dependency at all — the smoke tier's only failure modes are the app's own code
  and the already-provisioned Postgres/Keycloak CI containers, same as every other smoke check.
- **Dedicated fixture identities**: needs a pool of fresh Keycloak users with zero org membership
  at test start, distinct from the standard seeded demo users (`testuser`/`alice`/`bob`/`carol`/
  `dave`@example.com per `CLAUDE.md`) that the other 25 area specs authenticate as — those five
  already belong to existing demo orgs/projects, so reusing them here would either skip the
  org-creation step or corrupt their state for every other spec depending on them. `scripts/
  seed.sh` already deletes and recreates a fixed Keycloak user set on every run; the E2E reseed
  step (§4 above) extends the same pattern with a second, separate identity pool reserved for this
  scenario, recreated the same way on every full reseed so the test stays idempotent across runs.

## Product decisions

- **Full comprehensive coverage is the goal**, superseding JOB-186's original phased framing —
  confirmed unchanged from the FC scoping conversation.
- **New process doc**: `docs/dev/process/E2E.md`, alongside `WORKFLOW.md`/`JOBS.md`/
  `BACKEND.md`/`FRONTEND.md`, defining: when a new feature's implementation job must include E2E
  coverage going forward (every feature-adding job from now on, sized proportionally — a new
  screen needs a happy path + its distinct error states at minimum, not the full edge-case depth
  of this backfill), file/naming conventions (`cypress/e2e/<feature-area>/<screen>.cy.ts`,
  matching the Appendix's grouping), and how the two CI tiers above are chosen per change.
- **New job type: `E2E`** (org-wide convention, alongside existing `ADR`/`DB`/`Backend`/
  `Frontend`/`Infra`) so this work is tagged and filterable going forward.
- **One job per feature area**, per the table above — 21 jobs for MIL-032, 4 for MIL-033.
- **Not-yet-built features are explicitly excluded from backfill** (§1) — their E2E coverage is
  the responsibility of whatever implementation job eventually builds them, enforced by the new
  process doc, not retrofitted here.
- **One additional golden-path job** (§6) — a multi-actor, full-lifecycle simulation running in
  `e2e-smoke` (pre-merge), supplementing the 21 MIL-032 area jobs rather than replacing any of
  them.

## Technical design

### Database
None directly, aside from one fixture seed step: the golden-path scenario's reseed (§4, §6)
inserts a realistic-shaped `org_subscriptions` row (real-looking `paddle_subscription_id`,
`subscription_status: ACTIVE`) for its fixture org, so the scenario never has to drive a live
Paddle checkout to unlock the features it exercises. Real Postgres is used as CI infrastructure
(already provisioned for `integration-tests`; the new E2E job reuses the same service block).

### API
None — test infrastructure, not a product feature. Webhook simulation (§2b) calls the existing
`POST /api/webhooks/paddle` endpoint as a client, no new endpoint added.

### Backend
None expected beyond making sure the Keycloak realm export used for local dev is CI-importable
as-is (confirm no local-only settings — e.g. `localhost`-hardcoded redirect URIs — block import
in the CI network context).

### Frontend
- Cypress installed and configured from scratch (`cypress.config.ts`, `cypress/support/`,
  `cypress/e2e/<feature-area>/`).
- `cy.loginAs()` custom command per §3.
- Test files organized by feature area exactly matching the job breakdown table above.

### CI (`.github/workflows/ci.yml`)
- New `changes`-gated `e2e-smoke` job (every PR) and `e2e-full` job (merge to `main` + nightly
  cron), both provisioning Postgres (reusing the existing service block shape) and a Keycloak
  container with realm import, then `npm run build` + `cypress run`.
- `actions/setup-java`/`setup-node` versions follow whatever JOB-201's chore bump lands as, not
  duplicated here.
- `e2e-smoke` needs no Paddle secrets — the golden-path scenario's subscription state is DB-seeded
  (§6), not driven through a live checkout, so `PADDLE_API_KEY`/`PADDLE_WEBHOOK_SECRET` stay scoped
  to `e2e-full`'s billing-area cases (JOB-230's `[a]`/`[b]`-tagged checkout cases) where they're
  actually exercised.
- `scripts/seed.sh`'s Keycloak delete-and-recreate pattern is extended with a second, dedicated
  identity pool reserved for the golden-path scenario (§6) — kept separate from the standard demo
  users so the scenario can create a genuinely fresh org without disturbing fixtures the other 25
  specs depend on.

### Constraints & edge cases
- Full comprehensive coverage is a genuinely large effort — three milestones (framework first,
  then two backfill tracks) keep it sequenced rather than one undifferentiated pile.
- The process doc must set a realistic, proportional bar for new-feature E2E coverage so it
  doesn't become a tax that slows every future PR — see Product decisions above.
- Billing/Paddle testing spans three different testing strategies within one milestone — MIL-033
  jobs should each specify up front which of (a)/(b)/(c) each of their test cases uses, per the
  Appendix, so implementers don't default to slow/flaky browser E2E where a webhook POST would do.

## Alternatives considered

### Single flat MIL-032 with the original ~14-area breakdown

Rejected after the actual code audit — several "areas" (e.g. "job tracking") turned out to
bundle 4–5 independently testable surfaces (list, create/edit, status transitions, blocking,
notes, relationships, history), and lumping them into one job would produce an unreviewably large
PR per job. The finer 21-job breakdown maps one-to-one with what the research forks actually
found as distinct, independently-shippable test suites.

### Backfilling E2E for the four unimplemented cross-project-visibility ADRs anyway (as placeholder/skipped tests)

Rejected — writing `it.skip()` stubs against endpoints and pages that don't exist yet produces
maintenance debt with no test value; the process doc's "E2E ships with the feature" rule already
covers this when those ADRs are eventually implemented.

### Testing all Paddle/billing flows via real Paddle sandbox checkout only

Rejected — confirmed via the billing research fork that actually completing sandbox card payments
from Cypress for every state-changing scenario (upgrade, downgrade, cancel, past-due, credit
consumption, idempotent redelivery) would be slow and flaky; webhook POST simulation covers the
same backend state transitions deterministically and is what most of the catalog uses.

### Running the full E2E suite on every PR

Rejected — explicitly flagged as a JOB-195 constraint ("must not become a maintenance burden that
slows down every future feature PR"). The two-tier smoke/full split gets fast per-PR signal
without paying the full suite's cost on every push.

### Mocking Keycloak entirely in CI (auth bypass everywhere, no real Keycloak container)

Considered, since backend integration tests already do this. Rejected specifically because
ADR-0036's actual deliverable — the branded custom login/register/reset pages — cannot be
verified without a real Keycloak instance rendering them. A hybrid (real Keycloak for the one
auth-flow area, token-endpoint bypass everywhere else) gets both correctness and speed.

### Golden-path scenario: real Paddle sandbox checkout as step 1

The original design (superseded during ADR review, before implementation started). Rejected on
reflection — checkout is already exhaustively covered by the dedicated billing job (area 23,
`[a]`/`[b]`-tagged), so re-driving it here is redundant coverage, not additional coverage, while
still paying its full cost: a new pre-merge dependency on Paddle sandbox reachability (a sandbox
outage would fail this check on every PR for a reason unrelated to the PR), plus the CI complexity
of simulating webhook delivery to an ephemeral, not-publicly-reachable runner. The governing
principle above (fixtures over live external dependencies, except for the test whose actual
subject is that dependency) applies here directly — this scenario's subject is cross-feature
integration, not checkout.

### Golden-path scenario: fixture-level `is_internal` bypass instead of a realistic subscription row

Considered as the simpler fixture option. Rejected specifically because `is_internal` orgs skip
`hasAddon()`/`hasRealBilling()` checks entirely — a materially different code path than what a
real paying customer's request actually hits. A fixture row shaped like a genuine active
subscription (real-looking `paddle_subscription_id`, `subscription_status: ACTIVE`) exercises the
same gating logic a real customer would trigger, just without the live network round-trip needed
to establish it — same principle, more accurate fixture.

### Golden-path scenario placed in `e2e-full` instead of `e2e-smoke`

Considered, on the reasoning that anything this broad "belongs" with the exhaustive catalog.
Rejected — the exhaustive catalog and this scenario serve different purposes (depth vs. an
integration story); only running it post-merge would mean a broken cross-feature interaction ships
to `main` before anyone finds out, which is exactly backwards from why this test exists.

## Consequences

### Positive
- Full, code-verified test catalog exists as of this ADR — implementation jobs can start against
  a concrete checklist rather than an abstract "write E2E tests" ticket.
- The audit surfaced several real product/code issues independent of E2E itself (see "Findings"
  below) — direct value beyond the test plan.
- The 21+4 job breakdown is sized close to what a single PR can reasonably cover, unlike the
  original ~14-area estimate.
- Real-Keycloak-in-CI closes an actual coverage gap (auth pages have never been tested against a
  live IdP in any CI run to date).
- The golden-path job (§6) closes a coverage class no per-area spec can reach — a cross-feature,
  multi-actor integration regression — and does so pre-merge, not after, with zero external
  service dependency since its subscription state is fixture-seeded, not live-checked-out.

### Negative
- Running Keycloak in CI is new infrastructure with its own failure modes (realm import,
  container startup ordering) not previously exercised anywhere in this pipeline.
- 25 feature-area jobs is a lot of sequential/parallel implementation work even with the
  framework in place — MIL-032/033 will take meaningfully longer than a single-job estimate would
  have suggested.
- The smoke/full split means a regression only caught by an edge-case test won't block a PR —
  it'll surface on the next full run (merge-to-main or nightly), not immediately. Accepted
  tradeoff for not slowing down every PR.

### Neutral
- Several findings below (stale ADR text, a permission-check gap, a UI dead-code branch) are
  logged here for visibility but not fixed by this ADR — they're implementation-time findings for
  whichever job ends up touching that code, or separate Bug/Chore tickets if the user wants them
  tracked now.
- The golden-path job needs a second, dedicated pool of Keycloak fixture identities distinct from
  the standard demo users — one more thing the E2E reseed step provisions and resets, following a
  pattern `scripts/seed.sh` already implements for the existing demo users.

## Findings during this audit (not exhaustive, surfaced for visibility — not all necessarily need separate tickets)

- **ADR-0009 (Notes) is stale**: it documents a 2000-char limit; the actual `CreateNoteRequest`
  validation and frontend `NOTE_MAX` constant both agree on 10000. Doc fix, not a code bug.
- **Job Relationships — possible permission gap**: `JobRelationshipService` only calls
  `requireMember` (not `requireOwnerOrAdmin`) on both create *and* delete, so a plain `MEMBER`
  can add/remove relationships via a direct API call even though the UI's delete button is
  gated to OWNER/ADMIN only (`canManage` prop). Worth confirming intended behavior before writing
  the corresponding E2E test, since automating the current behavior either bakes in a gap or
  documents an intentional (if UI-inconsistent) design.
- **COMPLETED-project mutation guard is inconsistent**: jobs, notes, and status changes are all
  blocked with 409 on a `COMPLETED` project; job relationships and block-reason creation are not
  guarded at the service layer at all (only hidden client-side). Worth a dedicated test either way
  to lock in whichever behavior is actually intended.
- **`detectPreset` dead-code branch** (`ScheduleFormModal`'s cron-preset detection): the
  daily-detection condition is unreachable because an earlier, identical check returns first —
  editing a schedule created via the Daily preset may reopen the edit modal on the "Advanced" tab
  instead of "Daily". Worth a direct look outside this ADR's scope; flagged here since the E2E
  test for "edit a Daily-preset schedule" will otherwise silently encode the bug as expected
  behavior.
- **Approvals ADR (0016) vs. actual code**: the ADR describes a 409-conflict UX as "modal closes
  + toast"; the real `ApprovalDecisionModal` instead keeps the modal open with an inline banner.
  Doc is stale, not the code — test the real behavior.
- **Recurring schedule assignee-clearing side effect**: updating a schedule down to zero
  assignees silently force-sets it to `PAUSED_NO_ASSIGNEES`, even if the caller didn't request a
  pause. Confirmed intentional per the service code, but easy to regress unnoticed without an
  explicit test — included in the Appendix.

## Implementation order

1. **MIL-031** — Cypress install/config, `cy.loginAs()` command, real-Keycloak-in-CI wiring
   (realm import), `docs/dev/process/E2E.md`, new `E2E` job type, `e2e-smoke`/`e2e-full` CI jobs
   (start with a trivial placeholder spec to prove the pipeline end-to-end before backfill work
   lands on top of it)
2. **MIL-032** — the 21 core-feature jobs, built against the framework from step 1, each scoped
   to its row in the breakdown table and its section in the Appendix, plus the golden-path job
   (§6, Appendix §26) once the dedicated fixture identity pool and its fixture-subscription seed
   step are in place
3. **MIL-033** — the 4 billing jobs, each tagging its test cases (a)/(b)/(c) per §2 before
   implementation starts

## Definition of done

- [ ] ADR document written in `docs/dev/decisions/`
- [ ] Reviewed and merged
- [ ] JOB-195 (Future Consideration/ADR ticket) closed
- [ ] MIL-031/032/033 implementation jobs created in PRJ-011 with `BLOCKED_BY` relationships
      (032/033 jobs blocked by the relevant MIL-031 framework jobs)
- [ ] New `E2E` job type created
- [ ] Golden-path job (§6, Appendix §26) created in MIL-032, `BLOCKED_BY` the same MIL-031
      framework jobs as the rest of the backfill

## References

- JOB-195 (PRJ-011 / MIL-031): the ADR ticket this document resolves
- JOB-186: original Future Consideration this project promotes and supersedes the phased scope of
- ADR-0044: Payment Gateway Integration (Paddle) — source of truth for the billing lifecycle
  tested in MIL-033
- ADR-0040: Interactive Feature Demos — source of truth for the `/features` demo catalog in
  Appendix §19, including the confirmed JOB-199 regression case
- CLAUDE.md: demo users table (seed data reused as E2E fixtures)

---

## Appendix: Full Test Case Catalog

Produced by a code-first audit (backend controllers/services, frontend feature folders, all
merged ADRs) — code is treated as source of truth over ADR text wherever the two disagree (each
such disagreement is called out inline). Every case is written specific enough to become a
Cypress test title. `[a]`/`[b]`/`[c]` tags in the MIL-033 section refer to the three testing
strategies in §2 above.

Deliberately out of scope, confirmed via the same controller/component audit: `HealthController`
(`/api/health`, infra liveness check, not a product feature) and `frontend/src/dev/DesignPage.tsx`
(an internal component showcase, never linked from the shipped app) — neither has user-facing
behavior worth E2E coverage.

### MIL-032 — Core Product Features

#### 1. Auth Flows (Login, Registration, Password Reset)

**Happy path**
- Unauthenticated user hitting any protected route is redirected to Keycloak login, logs in with
  a valid seeded demo user, redirected back to the app authenticated
- Session persists across a page reload (`onLoad: 'check-sso'`), no re-login prompt
- Logout clears the session; visiting a protected route afterward redirects to login again
- Access/refresh token silently refreshes in the background during a long session without
  disrupting the user
- New user self-registers via the branded `opsclear` Keycloak theme (ADR-0036) with a unique
  email/password meeting the realm's 8-char minimum → account created, redirected into the app,
  `UserSyncService.syncFromJwt` upserts a `users` row on first login
- First-login user with no org membership is prompted to create an organisation, cannot access
  any project/job until they do
- "Forgot password" → reset email flow → sets a new password meeting policy → logs in with it

**Validation & error cases**
- Wrong password → Keycloak's own error, stays on login page
- Login for a non-existent account → generic error, no user enumeration
- Keycloak unreachable on app boot → "Authentication service is temporarily unavailable" with a
  working "Try again" button (`AuthContext.tsx`)
- Registration with an email already in use → inline duplicate-account error
- Password below the realm's policy minimum on registration or reset → inline validation error
- Required fields blank on registration → Keycloak's own required-field validation blocks submit
- Reset link used twice → second use rejected/expired
- New password on the update-password screen failing policy → inline error, not submitted

**Edge cases**
- Direct navigation to a deep link while unauthenticated → after login, lands back on that exact
  deep link, not a generic home page
- Access token expires mid-session with no valid refresh token → forced back through login,
  in-progress unsaved form state is lost (expected, not a bug)
- Reset-link expiry and brute-force lockout screens fall back to Keycloak's default unbranded
  theme (explicit ADR-0036 scope-out) — verify they still function, just unstyled
- Multiple tabs open, logout in one — document actual cross-tab behavior rather than assuming

#### 2. Organisation Management (Create, Settings, Members, Invites)

**Happy path**
- User with no org submits name + 2–3 letter slug → 201, creator auto-added `OWNER`,
  `org_settings`/`org_sequences` seeded (ADR-0027 defaults `PRJ`/`JOB`/`MIL`), redirected to
  `/org/settings`
- Slug entered lower/mixed-case normalizes to uppercase on save and display
- OWNER updates org name/slug → 200, reflected immediately
- OWNER soft-deletes the org → 204, org and its projects behave as if gone
- OWNER/ADMIN adds an existing user (found via search) as `MEMBER`/`ADMIN` → 201
- OWNER changes a non-owner member's role `MEMBER` ⇄ `ADMIN` → 200
- OWNER/ADMIN removes a non-owner member → 204, member's existing project memberships/assigned
  jobs are left untouched (owner reassigns manually)
- Any member can list org members read-only
- OWNER/ADMIN sends an invite to a non-member email → 201, pending with inviter name + 7-day
  expiry
- Invited user (must already have an OpsClear account) opens `/invites/{token}` while logged in
  as the matching email, accepts → 200, added as `MEMBER`
- OWNER/ADMIN revokes a pending invite → 204
- **User search** (`GET /api/users?email=`, backs the add-member/assign-job pickers): typing 2+
  chars of an email prefix returns matches scoped to the caller's own org only

**Validation & error cases**
- Blank org name → 400; name > 100 chars → 400
- Blank slug → 400; slug not matching `^[A-Za-z]{2,3}$` → 400 (both client zod and server)
- Slug already taken (case-insensitive) → 409 `SLUG_ALREADY_EXISTS`
- Non-member on `GET /organisations/{id}` → 403 `NOT_A_MEMBER`
- Non-OWNER on `PATCH`/`DELETE` org → 403 `INSUFFICIENT_PERMISSIONS_OWNER`
- `GET /organisations/mine` with no org membership → 204 No Content, not 404 — verify frontend
  treats this as "prompt org creation," not an error
- Plain `MEMBER` on add/remove member → 403; non-`OWNER` on role change → 403
- Adding `role: OWNER` → 403 `CANNOT_ASSIGN_OWNER_ROLE` (same on role-change path)
- Adding a nonexistent `userId` → 404; adding an existing member again → 409 `ALREADY_A_MEMBER`
- Changing/removing the OWNER → 403 `CANNOT_CHANGE_OWNER_ROLE` / `CANNOT_REMOVE_OWNER`
- Invite: invalid email → 400; blank email → 400; inviting an existing member → 409
  `EMAIL_ALREADY_MEMBER`; inviting an email with a pending invite already → 409 `ALREADY_PENDING`
- Revoking a nonexistent/foreign invite → 404; revoking an already-accepted/revoked invite → 400
  `INVALID`
- Accepting an unknown token → 404; accepting while logged in as a *different* email than the
  invite → 403 `EMAIL_MISMATCH` (real testable case: log in as User B, open User A's invite link)
- Accepting an email that became a member via another path in the meantime → 409
  `EMAIL_ALREADY_MEMBER`
- User search queried with <2 chars → 400; blank query → 400; caller with no org → 403
  `NOT_IN_ORG`

**Edge cases**
- User attempts to create a second org while already in one — confirm actual enforced behavior
  via direct API, not just UI absence of the option
- User search results capped at 10 — confirm truncation, not an error, with 11+ matches
- **Multi-tenant isolation on user search**: a real cross-tenant-leak risk worth an explicit
  negative test — search from Org A for a known Org B user's email (even an exact match) must
  return empty results, never surface a user from another org
- Slug auto-uppercases and strips non-letters while typing (paste of `"ab1!c"` → `"ABC"`)
- Re-saving an org's own current slug is not a collision (self excluded from uniqueness check);
  a deleted org's slug becomes reusable by a new org — verify this is intentional
- Org-prefix immutability (ADR-0027, once a job/project/milestone exists) — confirm this is
  actually enforced somewhere; not visible in the controller/service read during this audit,
  possibly a gap
- Invite case-insensitive email match (`User@Example.com` sent, `user@example.com` accepts) →
  succeeds (`equalsIgnoreCase`)
- Invite token expiry (7 days) actually enforced on accept, not just revoked/accepted state
- Unauthenticated user opening an accept-invite link is routed through login first, lands back on
  the accept page with the token intact
- Cross-tenant isolation: Org A's OWNER cannot view/update/delete Org B's settings/members/invites
  via guessed UUIDs (403/404 per the guards above)

#### 3. API Keys

**Happy path**
- User with the `API_KEYS` addon creates a named key → 201, raw key shown exactly once with a
  copy button and persistent warning; list shows prefix, name, created date, "never used"
- Copy-to-clipboard copies the full raw key
- Listing never returns the hash or raw key, only `keyPrefix`
- Revoking a key → 204, excluded from the active list
- A revoked key used in `X-Api-Key` on a subsequent call is rejected
- A key actively used updates `last_used_at`, reflected on next list fetch

**Validation & error cases**
- Blank name → 400; name > 100 chars → 400
- Revoking a key not owned by the caller → 404 (also proves cross-user isolation)
- Revoking an already-revoked key → 404, not idempotent-204 — confirm intentional
- Any endpoint without the `API_KEYS` addon → blocked by `@RequiresAddon`

**Edge cases**
- "Unused for 90+ days" badge boundary — test exactly at, just under, and just over 90 days
- `expiresAt` is stored but explicitly not enforced in the auth filter (ADR-0025 V1 gap) — assert
  an expired key still authenticates today, as a documented-gap regression guard, not a bug fix
- Raw key never reappears after closing/reopening the create modal

#### 4. User Settings & Preferences

**Happy path**
- `/settings` reachable via user menu; theme toggle (Light/Dark/System) persists to
  `localStorage['opsclear:preferences']` and survives reload
- `System` theme reacts live to OS `prefers-color-scheme` changes without reload
- Job-list/dashboard/navigation preference defaults (ADR-0023) persist the same way and are read
  across the app

**Validation & error cases**
- Corrupted JSON in the preferences localStorage key falls back to defaults via `usePreferences`'
  try/catch — seed garbage before load and assert the fallback

**Edge cases**
- Clearing storage resets to defaults (expected)
- Brief flash-of-incorrect-theme on cold load for explicit Light/Dark is a documented, accepted
  gap (ADR-0019) — assert the post-hydration steady state, not zero-flash
- Two tabs with different in-memory preference state don't live-sync without a reload (expected
  unless the ADR wants otherwise — confirm before treating as a bug)

#### 5. Job List & Filtering

**Happy path**
- Status tabs (New/In Progress/Blocked/Completed/All) show correct counts, filter correctly
- Search (`?q=`) matches title/client, debounced 300ms
- Filter by priority, milestone, job type (type filter only when `JOB_TYPES` addon on and types
  exist)
- Sort each column asc/desc; default sort from user preference
- Grouped-by-milestone view with per-milestone progress bar vs. flat view
- Row click navigates to job detail; "+ New Job" opens the create modal, disabled on a
  COMPLETED project
- `MEMBER` sees only jobs assigned to them; OWNER/ADMIN see all

**Validation & error cases**
- Non-member of the project → 403; nonexistent/soft-deleted project → 404
- No org membership at all → 403 `NOT_IN_ORG`
- Failed fetch → `PageError` with retry

**Edge cases**
- 0 jobs → empty state with create CTA (only with no active filters); filtered-to-empty shows
  different copy, no CTA
- `hideCompletedFromAll` preference removes COMPLETED from "All" counts/list but not from its own
  tab
- Sort by `deadline` with mixed null/non-null — nulls sort last
- Milestone filter forces flat view even when grouped is the default preference
- Empty milestone group still renders as a collapsed/expanded shell

#### 6. Job Create/Edit (incl. templates, markdown toolbar)

**Happy path**
- Create with only `title`; create with every field populated
- Edit pre-fills existing values, saves via full-replace `PUT`
- Start from a template — wildcard resolution fills fields, usage recorded only on successful
  create
- Template `assigneeMode: FIXED` auto-selects; `ASK` focuses the assignee field; default leaves
  blank
- Markdown toolbar (all 5 consumers: job description, notes, project description/settings,
  templates): each of the 7 buttons wraps a selection or inserts a placeholder at cursor with
  correct focus/selection restore; Preview tab renders exactly what Write produced

**Validation & error cases**
- Blank `title` → 400 both sides; `title` > 255 → 400
- `description` > 10000 chars → 400 (note: ADR-0007 doesn't document this limit explicitly — code
  is source of truth)
- `assignedTo` referencing a nonexistent user → 404; `milestoneId`/`typeId` not in this project →
  404
- `PUT /jobs/{id}` as `MEMBER` (even if assigned) → 403 — **update is OWNER/ADMIN only**, unlike
  create which any member can do
- Create/update on a COMPLETED project → 409

**Edge cases**
- `PUT` is full-replace — omitting `milestoneId`/`typeId` on edit clears them
- **Priority asymmetry**: omitted on create defaults to `MEDIUM`; omitted on update *preserves*
  the existing value rather than resetting it — explicit regression test, easy to break
- Template selection with a `milestoneId` no longer in the project → silently dropped from the
  form
- Deadline round-trip through `<input type=date>` — verify no ±1 day shift near midnight/timezone
  boundaries
- Markdown toolbar: multi-line selection wrapped in list/quote syntax prefixes per-line vs.
  bold/italic/code wrapping once; repeated clicks on already-wrapped text — verify actual
  (not assumed) toggle-vs-double-wrap behavior

#### 7. Job Status Transitions & Blocking

**Happy path — legal transitions**
- `NEW→IN_PROGRESS` (no confirm, optimistic), `IN_PROGRESS→COMPLETED` (confirm, optimistic),
  `IN_PROGRESS→BLOCKED` (opens block modal, requires reason, not optimistic),
  `BLOCKED→IN_PROGRESS` (confirm, optimistic), `COMPLETED→IN_PROGRESS` (OWNER/ADMIN only, no
  confirm, optimistic)
- Each writes a `job_status_history` row; BLOCKED also invalidates block-reasons
- Block-reason combobox: filter existing, find-or-create new, resurrect a soft-deleted reason on
  exact-text re-entry rather than duplicating

**Validation & error cases**
- Illegal transitions all → 400: `NEW→COMPLETED`, `NEW→BLOCKED`, `COMPLETED→NEW`,
  `BLOCKED→COMPLETED`, `COMPLETED→BLOCKED`
- `BLOCKED` transition with blank reason → 400
- `MEMBER` (not assigned) attempting any status change → 403; assigned `MEMBER` attempting
  `COMPLETED→IN_PROGRESS` (reopen) → 403, OWNER/ADMIN-only even though other transitions allow
  the assignee
- Status change on a COMPLETED project → 409; on a soft-deleted job → 404
- `DELETE /block-reasons/{id}` as `MEMBER` → 403; deleting a nonexistent/already-deleted reason →
  404

**Edge cases**
- UI button visibility (`JobStatusBar`) must match the backend matrix — test via direct API call
  bypassing the UI too, not just button presence/absence
- Optimistic update reverts on server rejection, no permanently-wrong badge
- Two soft-deleted-reason-text-identical block reasons share one DB row (unique per
  `(project_id, reason)`) — deleting it must not affect either job's already-set reference
- Reason text whitespace-trimmed before find-or-create (`" Waiting "` ≡ `"Waiting"`)

#### 8. Notes

**Happy path**
- Add a note, appears at thread bottom after refetch (not optimistic); oldest-first; author
  resolved from cached member list
- Character counter `n/10000` — confirmed against code, not ADR-0009's stale "2000" text
- First submission per browser session shows an immutability confirm dialog (sessionStorage
  flag), subsequent submissions in-session skip it
- Project-level grouped notes view: jobs ordered by most recent note, notes within a job
  ascending, only jobs with ≥1 note included

**Validation & error cases**
- Blank content → 400; > 10000 chars → 400 both sides
- Note create/list on a job in a different project than the URL → 404
- Note create on a COMPLETED project → 409 (UI hides the form; verify the guard still fires if
  bypassed via direct API)
- `NOTES` addon off → 403 on all endpoints

**Edge cases**
- No edit/delete route exists for notes at all — verify `DELETE /notes/{id}` 404s/405s, confirming
  immutability isn't just a missing UI affordance
- Markdown injection/XSS attempt in note content — confirm `rehype-sanitize` strips script
  tags/event handlers
- Content is server-side `.strip()`ped — leading/trailing-whitespace-only notes don't produce
  visually distinct duplicates

#### 9. Job Relationships

**Happy path**
- Add `BLOCKED_BY`/`RELATED_TO`/`DUPLICATES` to another job in the same project via search
- Relationship shows on both jobs, flipped label on the incoming side
- Remove via × button; section auto-expands if the job already has relationships on load

**Validation & error cases**
- Missing `targetJobId`/`type` → 400; self-reference → 400 `SELF_REFERENCE`
- Duplicate `(source, target, type)` → 409 `ALREADY_EXISTS` (note: the reverse triple is *not*
  blocked by this constraint — verify whether A→B and B→A of the same type coexisting is
  intended or a gap)
- Cross-project `targetJobId` → 404; nonexistent/soft-deleted target → 404
- Deleting a relationship not belonging to the given job (neither source nor target) → 404
- **Flagged in the ADR body above**: service only requires `requireMember`, not
  `requireOwnerOrAdmin`, on create *and* delete — verify actual intended permission before
  automating current behavior into the test
- `JOB_RELATIONSHIPS` addon off → 403

**Edge cases**
- Deleting a job cascades — the other side's relationship list must no longer reference it, no
  orphan rows
- Add-relationship search excludes the current job from results
- Same COMPLETED-project-guard-gap flagged in the ADR body — verify via direct API whether
  relationships can be added/removed on a COMPLETED project (likely allowed today, unlike
  jobs/notes/status)

#### 10. Status History

**Happy path**
- Job creation writes the first entry (`changedFrom: null, changedTo: NEW`); every subsequent
  transition appends, BLOCKED entries carry the reason inline
- Oldest-first ordering; duration between consecutive entries computed client-side, omitted on
  the last (open-ended) entry
- Section defaults expanded if non-empty

**Validation & error cases**
- `JOB_STATUS_HISTORY` addon off → 403 — **this is the exact endpoint behind JOB-199**: confirmed
  root cause is `useJobHistory`'s `enabled` flag gating on `hasAddon('JOB_STATUS_HISTORY')`,
  which silently resolves `false` outside an `OrgContext.Provider`. Include an explicit regression
  test rendering the history section outside the normal context tree (see Appendix §19 for the
  actual failing surface) and assert it still fetches once fixed.
- History for a job in a different project than the URL → 404

**Edge cases**
- No edit/delete route for history entries — confirm 404/405
- Reopening then re-completing a job produces two separate `IN_PROGRESS→COMPLETED` entries —
  duration calc must handle repeated transitions through the same state pair
- A job that never changed status still has exactly one entry (creation)

#### 11. Projects (CRUD, Lifecycle, Members)

**Happy path**
- Create with name only or name+description+block reasons; creator auto-`OWNER`
- List defaults to ACTIVE tab; COMPLETED/ALL tabs filter correctly
- OWNER/ADMIN edits name/description inline; MEMBER sees settings fields disabled, no Danger Zone
- OWNER marks COMPLETED with zero open jobs; reactivates back to ACTIVE
- OWNER deletes with type-exact-name confirmation
- Members: search-add by email, change role, remove (not self, not OWNER)

**Validation & error cases**
- Blank/whitespace name → 400; name too long → 400 (note: 80-char frontend zod cap is *stricter*
  than the 255-char backend limit — verify both layers independently, since the frontend could
  block valid input the backend would accept)
- Duplicate name under the *same* owner → 409 `NAME_ALREADY_EXISTS`; same name under a *different*
  owner in the same org succeeds (uniqueness is per-owner, not per-org)
- `>20` block reasons or a blank one in the list → 400
- `MEMBER` on `PUT` → 403; `ADMIN` (not OWNER) on status change or delete → 403
  `INSUFFICIENT_PERMISSIONS_OWNER` — stricter than edit
- Marking COMPLETED with open jobs → 409 `HAS_OPEN_JOBS` with count in the message
- Adding an already-member user → 409 `ALREADY_A_MEMBER`; assigning/changing to `OWNER` → 403;
  removing the OWNER → 403
- `memberId` belonging to a different project → 404

**Edge cases**
- Delete-confirmation input is case- and whitespace-sensitive against the exact project name
- `ACTIVE→COMPLETED→ACTIVE→COMPLETED` cycle repeats correctly, no open-jobs check on reactivation
- Removing a member who's the sole assignee on a recurring schedule cascades — schedule rotation
  shrinks, auto-pauses (`PAUSED_NO_ASSIGNEES`) if it hits zero — cross-reference Appendix §15
- Non-member of a project but member of the org fetching by ID → 403, not 404 (existence isn't
  hidden at that layer — confirm order of checks)

#### 12. Milestones

**Happy path**
- Create name-only or with description/deadline; progress bar reflects already-loaded job counts
- Progress format toggle (fraction/percentage) reflects immediately from preference, no refetch
- "View jobs →" navigates pre-filtered; delete ungroups referencing jobs rather than deleting them
- Overdue deadline styling vs. future; MEMBER read-only, no controls

**Validation & error cases**
- Blank/overlong name → 400; `MEMBER` on write endpoints → 403
- Cross-project milestone ID on update/delete → 404; `MILESTONES` addon off → 403

**Edge cases**
- Deadline exactly "today" is not counted overdue (date-truncated comparison) — explicit boundary
  test
- Deleting a milestone referenced by a job template — template's milestone field should tolerate
  a now-missing milestone (hidden, not crash) per ADR-0032
- Progress bar at 0 jobs, 100% complete, and 0% complete — all three render correctly

#### 13. Job Templates — Project & Org-Level

**Happy path**
- Create with name only or fully populated; `assigneeMode` NONE/FIXED/ASK behave per ADR-0032
- Every base wildcard resolves: `{{date}}`, `{{day}}`, `{{month}}`, `{{year}}`, `{{week}}`,
  `{{quarter}}`, `{{project}}`, `{{creator}}`, `{{assignee}}`, `{{occurrence}}`
- Arithmetic wildcards resolve with correct rollover: `{{date±N}}`, `{{month±N}}` (Dec↔Jan),
  `{{quarter±N}}` (Q4↔Q1 across years), `{{year±N}}`, `{{week±N}}`
- Using a template calls `/use`, increments `usedCount` atomically, visible on the list
- Org-scoped templates: combined `GET /projects/{id}/templates` returns both scopes tagged, UI
  prefixes org templates `[Org]`; `defaultTypeName` resolves case-insensitively per-project
- Deleting a template with zero *active* (non-paused) referencing schedules succeeds

**Validation & error cases**
- Blank name → 400; invalid `priority`/`assigneeMode` enum bypassing the UI → 400;
  `deadlineOffsetDays ≤ 0` → 400
- Project-scoped template with `defaultTypeName` set → 400 (org-only field); org-scoped with
  `defaultTypeId` set → 400 (project-only field)
- Delete a template referenced by ≥1 *active* schedule → 409 with schedule names listed
  (JOB-111 precedent) — paused-only referencing schedules do not block delete
- `MEMBER` on create/update/delete → 403; `/use` allowed for any member (confirm intended)
- Org `MEMBER` (org-level role, distinct from any project role) on org-template writes → 403
- Unresolved/malformed wildcard tokens (`{{nonsense}}`) left as literal text, no error; arithmetic
  on non-time tokens (`{{project+1}}`) not supported, falls through to literal

**Edge cases**
- `{{month+2}}` in November rolls correctly to January; `{{quarter-2}}` in Q1 rolls back across
  the year boundary
- ISO week calculation at year boundaries (Dec 31 in week 1 of next year, etc.) doesn't produce a
  wrong/negative number
- Deleting a template doesn't affect jobs already created from it (no persisted FK)
- Two templates with identical names in one project — confirm dropdown disambiguation isn't
  genuinely ambiguous
- Renaming a job type after an org template's `defaultTypeName` was configured against the old
  name → next use silently fails to match, type left blank (best-effort by design)

#### 14. Job & Project Links

**Happy path**
- Any member adds a job- or project-level link; blank label on a recognized-service URL
  (GitHub/GitLab/Figma/Notion/Linear/Vercel/Confluence/Jira) auto-fills the service name;
  unrecognized host falls back to a favicon, then a generic icon
- OWNER/ADMIN edits/deletes; copy-URL button works
- `mailto:`/`ftp:` accepted (loose scheme policy)

**Validation & error cases**
- Blank URL → 400; **`javascript:`/`data:`/`vbscript:` scheme → 400 `INVALID_URL`** — explicit
  XSS-prevention test including case variants and whitespace/control-char smuggling before the
  scheme
- No scheme at all (bare `example.com`) → 400; malformed/unparseable URL → 400
- `label` > 100 chars → 400
- `MEMBER` on edit/delete → 403 (add is open to all members — asymmetry worth its own explicit
  test since it's easy to regress)
- Cross-job/cross-project `linkId` → 404; `JOB_LINKS` addon off → 403

**Edge cases**
- Subdomain-based service detection (`mycompany.atlassian.net`) and `www.` normalization both
  resolve to the correct known icon
- Clearing an auto-filled label before submit saves `label: null`, falls back to hostname display
- Editing a URL from a known to an unrecognized service updates the icon on next render
- Deleting the owning job/project cascades link deletion with no orphan rows

#### 15. Recurring Schedules (+ Missed Runs + Cron Preview)

**Happy path**
- Create via Daily/Weekly/Monthly presets or Advanced raw cron with live human-readable
  translation and a debounced 5-next-runs preview
- Create with an ordered assignee rotation, or none at all
- Create already-paused via a future `pausedUntil`; create with `expiresAt`
- Pause via 1 day/week/month/custom date/indefinitely; resume clears `pausedUntil`
- Deleting a schedule leaves already-materialized jobs' "created by schedule" label functional
  until the schedule itself 404s, then hidden gracefully
- Status badges: `ACTIVE`, `PAUSED`, `PAUSED_NO_ASSIGNEES`, `EXPIRED`
- Cron preview (`POST /api/schedules/preview`) returns 5 correct future occurrences, live-updates
  in the form as fields change
- **Missed runs** (`.../schedules/{id}/missed-runs`): list shows any occurrences the poller
  couldn't materialize (e.g. after downtime); "Materialize" on a single row (OWNER/ADMIN) creates
  the job and removes it from the missed-run list; "Dismiss" on a single row removes it with no
  job created; bulk "Dismiss all" (only shown when count > 1) clears every row in one action, and
  a single remaining row still dismisses correctly via its own control

**Validation & error cases**
- Blank name or no template selected → 400/404; invalid raw cron → 400 `INVALID_CRON`
- **Cron producing occurrences <1 hour apart → 400 `CRON_INTERVAL_TOO_SHORT`** — explicit boundary
  test at exactly 3600s (passes) vs. 3599s (fails)
- Invalid IANA timezone → 400 `INVALID_TIMEZONE`, on both create/update and the preview endpoint
- `MEMBER` on any write endpoint → 403 (read endpoints, including missed-runs, are member-level) —
  this includes `MEMBER` attempting materialize/dismiss/dismiss-all, all OWNER/ADMIN-only writes
- Cross-project `scheduleId`/`missedRunId` → 404; dismissing an already-dismissed/nonexistent run
  → 404
- `RECURRING_SCHEDULING` addon off → 403

**Edge cases**
- **Clearing all assignees on update silently force-sets `PAUSED_NO_ASSIGNEES`**, even without an
  explicit pause request — explicit regression test: schedule ACTIVE → edit, remove all
  assignees, save → confirm it's no longer ACTIVE
- Editing the assignee list while `current_rotation_index` points past the new (shorter) list —
  round-robin modulo keeps the pointer valid, next materialized job goes to the mathematically
  correct next person, not always index 0
- **`detectPreset` dead-code branch** (flagged in the ADR body) — create via Daily preset, reopen
  the edit modal, confirm which tab is actually pre-selected; if "Advanced" instead of "Daily,"
  that's the known bug, document accordingly rather than asserting the buggy behavior as correct
- Monthly preset UI caps day-of-month at 28, avoiding the "no Feb 30" problem — Advanced mode
  allows entering an out-of-range day; confirm and document actual Spring `CronExpression`
  behavior (skip vs. error) since the UI's guard is bypassable
- A daily schedule crossing a DST transition still fires once at the correct local time, no skip
  or double-fire
- Materializing a missed run sets `deadline = expected_at + offsetDays` (the *original* missed
  date), not "today + offsetDays"
- Cron preview endpoint does **not** enforce the ≥1-hour-interval rule (only create/update do) —
  confirm the form doesn't mistake a successful preview for save-eligibility

#### 16. Job Types

**Happy path**
- OWNER/ADMIN creates from the 10 fixed swatches; edits name/color; reorders via chevrons
  (adjacent `displayOrder` swap); deletes when unreferenced
- MEMBER read-only view; list/detail badges and dashboard type-breakdown populate correctly

**Validation & error cases**
- Blank/overlong name → 400; invalid color enum bypassing the picker → rejected at the **database
  level** (Postgres enum) — explicit direct-API test confirming a clean error, not a leaked SQL
  exception
- `MEMBER` on writes → 403
- Delete referenced by jobs only → 409 `STILL_REFERENCED_BY_JOBS`; by templates only → 409
  `STILL_REFERENCED_BY_TEMPLATES`; **by both → 409 `STILL_REFERENCED_BY_JOBS_AND_TEMPLATES`** with
  both counts — explicit test for this least-common three-way branch
- `JOB_TYPES` addon off → 403

**Edge cases**
- Rapid double-reorder click (two independent PUTs, not a batch endpoint) doesn't leave
  `displayOrder` inconsistent
- Deleting a type still matched by an org template's `defaultTypeName` (name-only matching, no
  FK) has no delete guard — confirm this is accepted per ADR-0042's best-effort design

#### 17. Approvals Workflow + Queue

**Happy path**
- MEMBER requests approval on their own assigned job; OWNER/ADMIN request on any job in the
  project → 201, PENDING
- OWNER/ADMIN approve/reject with or without a comment → 200, status + `approverId`/`decidedAt`
  set
- A job can accumulate multiple simultaneous PENDING approvals, each independently decidable
- Queue page groups by job, oldest-request-first within group, groups ordered by oldest pending
  item; "→ Job" navigates to detail; successful decide removes the card

**Validation & error cases**
- Blank/overlong `description` → 400; `MEMBER` requesting on an unassigned job → 403
- Non-member of the project → 403; `MEMBER` attempting to decide or list pending → 403
- `PATCH .../status` with `status: PENDING` → 400 `CANNOT_SET_PENDING`
- Decide an approval not belonging to the given job → 404; wrong-project job → 404; COMPLETED
  project → 409
- **Concurrent decision race**: two OWNER/ADMIN sessions decide the same approval near-
  simultaneously → first wins 200, second gets 409 `ALREADY_DECIDED` — real, testable via the
  atomic `WHERE status='PENDING'` update
- `APPROVALS` addon off → 403 on every endpoint; org `PAST_DUE` → writes blocked 403, **reads
  still succeed** (list/pending)
- `MEMBER` navigating directly to the queue URL → silently redirected to the job list, no "access
  denied" flash
- **409 on decide in the UI**: the actual `ApprovalDecisionModal` stays open with an inline red
  banner — test the real behavior, not ADR-0016's stale "modal closes + toast" text

**Edge cases**
- Approver deciding their own requested approval — allowed, no self-approval restriction
- Job soft-deleted after an approval was requested but before decision → decide 404s
- Whitespace-only description handling — confirm 400-before-trim vs. persisted-trimmed

#### 18. Dashboard

**Happy path**
- `/projects/:id` redirects to `/projects/:id/dashboard`
- Summary cards (NEW/IN_PROGRESS/BLOCKED/COMPLETED/OVERDUE, +PENDING_APPROVALS for OWNER/ADMIN)
  with correct counts; clicking navigates pre-filtered
- Donut chart proportional, hidden at zero jobs
- **Job-type breakdown widget** (not documented in ADR-0017, confirmed live in code) — bars
  sorted by count descending, hidden with no types/no typed jobs
- Blocked section sorted oldest-first with reason shown; overdue sorted soonest-first (excludes
  COMPLETED, includes overdue-and-BLOCKED)
- Pending approvals section (OWNER/ADMIN only) shows up to 5 with a "view all" link past that
- `MEMBER`'s dashboard scoped to their own assigned jobs only, pending-approvals section absent
  entirely
- COMPLETED project shows a banner; project description renders as markdown
- Zero-jobs vs. jobs-but-all-sections-empty get distinct empty states

**Validation & error cases**
- `DASHBOARD` addon off → upgrade card, no fetch; failed fetch → `PageError` + retry
- Non-member → 403; nonexistent project → 404

**Edge cases**
- User-preference section toggles (`showBlockedSection` etc.) hide a section even with data —
  verify the "all clear" empty state's OR-logic still accounts correctly for a hidden-but-
  non-empty section
- Deadline exactly "now" is the not-yet-overdue boundary
- A BLOCKED-and-overdue job counts in both `overdueCount` and appears in both sections
- No manual refresh exists yet (ADR-0048 unimplemented) — 30s `staleTime` + refetch-on-focus is
  the only freshness mechanism today; don't test for a refresh button that doesn't exist

#### 19. Public Landing Page & `/features` Interactive Demos

**Happy path — Landing (`/`)**
- Unauthenticated → full landing page; authenticated → redirected to `/projects`
- Hero/CTA buttons trigger `keycloak.register()`
- Pricing calculator: member/project sliders and add-on toggles update the running total live;
  annual toggle recomputes with a "save X" badge; "coming soon" add-ons are non-interactive

**Happy path — `/features` demos (13 cards, all confirmed wired with `loadSlides`)**
- Each card's shrunk live preview renders real seeded data on page load (not a static fallback)
- Clicking opens a full-screen overlay with a persistent "Demo — sample data" badge; closing
  resets to slide 0 against a fresh baseline
- Escape closes; multi-slide cards (approvals) page via arrows/keys with a slide counter
- Interactive mutations inside a demo (approve/reject, add note, add/delete relationship,
  create/revoke key, submit feedback) resolve against MSW mock state only, never a real backend

**Validation & error cases**
- MSW worker registration failure → falls back to the static preview, logged warning, no
  unhandled rejection
- A slide's render throwing is caught by a per-slide `ErrorBoundary`; switching slides resets it

**Edge cases — KNOWN BUG, should currently FAIL until JOB-199 is fixed**
- **Notes demo card**: opens empty despite seeded `BASE_NOTES['demo-job-01']` content. Root cause
  confirmed by code: `useNotes` gates `enabled` on `hasAddon('NOTES')`; `DemoQueryScope` (which
  this slide uses) provides no `OrgContext.Provider`; the context's default `hasAddon: () =>
  false` resolves the query to `enabled: false`, so it never fires — no error, no loading state,
  just silently empty.
- **History demo card**: same root cause via `useJobHistory` gating on
  `hasAddon('JOB_STATUS_HISTORY')`. Both should fail today and pass once JOB-199 lands.
- **Confirmed NOT affected** (explicit passing regression tests, one per card, to guard against a
  future hook regaining a `hasAddon` gate without its demo wrapper being updated): `api-keys`,
  `links`, `relationships`, `feedback-credits`, and the `approvals` demo's second slide — none of
  their underlying hooks (`useApiKeys`, job's `LinksSection`/`RelationshipsSection`, `useFeedback`,
  `ApprovalList`) are `hasAddon`-gated.
- All 13 cards mount simultaneously on page load — verify no request-storm/duplicate-record bug
  (the exact class of bug a `useMemo` fix in `FeaturesPage.tsx` was already guarding against):
  adding a note in the `notes` demo must not create duplicate notes
- `resetDemoData()` on close then immediately reopening produces an identical baseline every time

**Edge cases — Landing page**
- Slider at min/max boundary indices; `selectedTier` resolving to `null` when no exact
  member/project band pair matches (doesn't crash, base falls back to 0)
- Rapid slider drag doesn't produce stale/flickering price

#### 20. Localization (i18n) — Smoke Coverage

**Happy path**
- Default locale (no stored preference) renders English; switching locale via the language
  switcher re-renders all visible strings immediately, no reload
- Selection persists via `usePreferences`/localStorage and survives reload
- Every one of the 12 namespace files has matching keys between `en/` and `sr/`
- API `ErrorResponse.error` category renders translated in both locales; `message` stays English
  in both (by design)

**Validation & error cases**
- A key present in one locale but missing in the other — verify actual fallback behavior
  (expected: English, i18next default) rather than a raw literal key string leaking to the UI

**Edge cases**
- Locale switch mid-form-fill loses no data, only label text changes
- Pricing calculator hardcodes `Intl.NumberFormat('sr-RS')` regardless of UI locale — confirm
  this is intentional (RSD currency formatting, not UI-language-driven) not a bug
- RTL is explicitly out of scope (ADR-0037) — one negative test confirming no RTL CSS accidentally
  applies is enough, no broader RTL suite needed

#### 21. Mobile Nav, Dark Mode, Empty States (cross-cutting UI)

**Happy path — Mobile nav drawer**
- At `md:hidden` widths, hamburger replaces the desktop nav row; opening shows the same items,
  same order, same locked-addon treatment as desktop; selecting an item closes the drawer and
  navigates; backdrop click closes without navigating
- Pending-approvals badge on the hamburger matches the desktop nav's count

**Happy path — Dark mode**
- `light`/`dark`/`system` themes apply the `dark` class on `<html>` correctly; `system` reacts
  live to OS scheme changes without reload; persists across reload

**Happy path — Empty states (shared component, 14 call sites)**
- Each call site renders icon+message correctly when empty; action-bearing sites show a working
  create CTA; action-less sites (status history, "all caught up" queue, no-results relationship
  search) show no CTA by design

**Happy path — App shell / routing**
- Navigating to an unknown path (`*` route) redirects to `/projects`, matching the same
  no-hint-given pattern used for unauthorized `/super-admin/*` access
- A route-level render error is caught by `RouteErrorPage` (React Router `errorElement`) rather
  than a blank white screen — worth one deliberate-throw test per top-level route group to confirm
  the boundary is actually wired, not just present in the router config

**Validation & error cases**
- **Role-gating regression test (the specific bug ADR-0041 fixed)**: a `MEMBER` viewing an empty
  Milestones/Schedules/Templates/OrgSettings page must NOT see the "create first X" CTA — the
  original bug checked `hasAddon()` only, not role. Explicit per-page × Member-role test is
  high-value regression coverage, not optional nice-to-have. Owner/Admin viewing the same empty
  state DOES see the CTA (positive counterpart).

**Edge cases**
- Viewport resize crossing the `md:` breakpoint with the drawer open doesn't leave it stuck
  open/hidden inconsistently
- Switching `dark`/`light` → `system` mid-session correctly attaches/detaches the media-query
  listener with no leak across repeated toggling
- Dark mode's documented FOIT flash on cold load (ADR-0019, accepted gap) — assert
  post-hydration steady state only, not zero-flash

---

### MIL-033 — Billing (Paddle, Subscriptions, Super Admin)

Every case tagged `[a]` browser E2E vs. Paddle sandbox, `[b]` direct webhook POST simulation, or
`[c]` backend integration test only — per §2 above.

#### 22. Subscription Selection & Feature Gating

**Happy path**
- Owner opens the tier/add-on picker (member/project sliders, add-on cards) driven by the
  catalog endpoint `[a]`; adjusting selections updates the running total live, client-side, no
  network call `[a]`
- Annual toggle recalculates with a savings banner `[a]`
- No-subscription-yet owner sees "first purchase" mode (Continue to Payment, not Save) `[a]`
- Org with an active add-on sees the corresponding nav item/section unlocked; without it, sees an
  upgrade card (full page) or a single collapsed locked row (job-detail sections) `[a]`
- `is_internal` org bypasses every add-on and past-due check on every gated endpoint `[b/c]`

**Validation & error cases**
- Non-owner on `PUT .../subscription` → 403 `[b]`; unknown `tierId` → 404 `[b]`; invalid
  `billingCycle` → 400 `[b]`
- Downgrade to a tier whose `max_members`/`max_projects` is below current active counts → 422/403
  with the count in the message `[b]`
- **Direct API call bypassing the UI to a gated endpoint with an add-on staged-but-unpaid** (no
  real Paddle subscription) → 403 — the exact JOB-200 gap closure (`hasRealBilling()` checked
  before `hasAddon()`) `[c]`
- Gated write while `subscription_status = PAST_DUE` → 403; gated **read** while PAST_DUE → 200,
  succeeds `[b]`
- User with no organisation at all hitting a gated endpoint → 403 `[c]`

**Edge cases**
- `is_internal` skips downgrade member/project-count validation entirely — DB-seeded only, no API
  surface to set it, so this is a fixture-setup concern, not a UI test `[c]`
- Tier with `max_projects = NULL` (unlimited) — downgrade check must skip that comparison `[b]`
- Coming-soon add-ons always render locked regardless of `hasAddon()` (driven by catalog
  `available:false`, not the AOP gate) `[a]`

#### 23. Paddle Checkout / Upgrade / Downgrade / Cancellation

**Happy path — first-time checkout**
- Select plan, "Continue to Payment" → inline checkout opens with correct item list + total
  `[a, sandbox test card]`
- Completing checkout polls (`awaitingWebhook`, 2s/20s timeout) until status flips ACTIVE `[a]`
- `subscription.created`/`activated` webhook creates the first `org_subscriptions` row, tier/
  add-ons resolved from the webhook's own item price IDs, never trusted from the client `[b]`
- Abandoning checkout returns to the picker with no subscription created `[a]`

**Happy path — upgrade (net price increase)**
- Increasing tier/add-ons triggers a preview showing the immediate-charge amount + any credit
  applied, before a confirm modal `[a/b]`; confirming charges `prorated_immediately`, local
  tier/add-ons update **immediately**, not waiting for the webhook `[b]`
- Live debounced preview (500ms) updates "extra to pay now" as sliders move `[a]`

**Happy path — downgrade (deferred to renewal)**
- Decreasing tier/add-ons stages `pending_tier_id`/`pending_addon_ids` +
  `paddle_pending_downgrade_effective_at`, Paddle items updated `full_next_billing_period` (zero
  immediate billing) `[b]`; UI shows an amber pending-downgrade banner with the effective date
  `[a]`
- Webhook reporting the period actually rolled over promotes the pending downgrade to active `[b]`
- Cancelling a pending downgrade before it takes effect reverts Paddle items via `do_not_bill`
  (verified zero billing impact), clears pending fields `[a/b]`

**Happy path — cancellation & resume**
- Cancel schedules end-of-period cancellation; status stays ACTIVE until the webhook's
  `subscription.canceled` event fires `[a/b]`; UI shows "cancels on {date}" immediately
  (optimistic) then the durable webhook-synced date takes over `[a]`
- Resume before the scheduled date clears the cancellation (`{"scheduled_change": null}`) `[a/b]`

**Validation & error cases**
- **Mixed change (adds a pricier item AND removes a cheaper one in one request) → 409
  `MIXED_UPGRADE_DOWNGRADE_NOT_ALLOWED`**, blocked client-side before the API call even fires `[a+b]`
- Upgrade/downgrade/cancel/resume/update-payment-method with no real Paddle subscription yet →
  409 `NO_PADDLE_SUBSCRIPTION_YET` `[b]`; for `is_internal` org → 400
  `INTERNAL_ORG_NOT_BILLED` `[b]`
- Second cancel while one is already scheduled → 409 `CANCELLATION_ALREADY_SCHEDULED` (a clean
  409, not Paddle's raw 400) `[b]`; resume with nothing scheduled → 409
  `NO_CANCELLATION_SCHEDULED` `[b]`
- Downgrade while a cancellation is already scheduled → 409 `[b]`; second downgrade attempt while
  one is pending overwrites cleanly, no duplicate `[b]`
- Non-owner on any of these endpoints → 403 `[b]`
- **Webhook signature/integrity, `POST /api/webhooks/paddle` itself**: missing `Paddle-Signature`
  header → 403 `INVALID_WEBHOOK_SIGNATURE` `[b]`; header present but hash doesn't match (tampered
  body or wrong secret) → 403, same error — verify the HMAC is computed over the **raw body
  string**, not a re-serialized JSON object, since re-serialization would silently change the
  signed bytes and make this check trivially bypassable `[b]`; syntactically malformed JSON body
  with a *valid* signature → parse failure → 403, no stack trace leaked to the response `[b]`
- Webhook event type not in the handled set (e.g. `transaction.payment_failed`, deliberately
  unhandled per ADR-0044) → 200, logged and ignored, not treated as an error `[b]`
- Webhook for a `customerId`/org that doesn't resolve to any organisation → 200, logged warning,
  no exception `[b]`
- An update-shaped webhook (`subscription.updated`) arriving for an org with no existing
  `org_subscriptions` row yet (should have gone through the create path first) → 200, logged
  warning, no crash, no row created `[b]`

**Edge cases**
- Boundary: new total exactly equal to old total → classified as the downgrade path (`isUpgrade`
  requires strictly greater), not upgrade `[c]`
- **`removeScheduledCancellation`'s `{"scheduled_change": null}` payload** — regression guard that
  it still uses a null-tolerant map construction (`Collections.singletonMap`, not `Map.of`) since
  this exact bug class was found once before `[c]`
- Redelivered `subscription.created` for a brand-new org (delivery #1 creates the row, delivery
  #2 must take the UPDATE path, not error on a duplicate) `[b]`
- Redelivered `subscription.updated`/`past_due`/`canceled` produces an identical end state on
  both deliveries (every write is UPDATE, not INSERT) `[b]`
- Resuming after the scheduled cancellation date has already passed (subscription actually
  terminal per webhook) is rejected sensibly, not resurrected `[b]`

#### 24. Billing History & Past-Due State

**Happy path**
- Billing history reads live from Paddle (`GET .../transactions`), no local mirror; statuses
  badge-colored `[a]`; org with no Paddle customer yet shows an empty list, not an error `[a/b]`
- PAST_DUE shows an amber banner; subscription management (update method, cancel) stays fully
  reachable during past-due `[a]`

**Validation & error cases**
- Non-owner on billing-history/update-payment-method → 403 `[b]`; `is_internal` org → 400 even on
  reads `[b]`

**Edge cases**
- Transaction with `billedAt: null`/`totalAmount: null` renders a placeholder, not a broken
  date/`NaN` `[a]`
- Past-due org's read-only pages (dashboard, job list) load fully, don't degrade into
  locked-feature UI — that's a separate concept from past-due `[a]`
- PAST_DUE → CANCELED (dunning exhausted) — UI switches from "can still read/manage billing" to
  full lockout only at the correct webhook event, not before `[b]`

#### 25. Super Admin: Pricing, Credit Grants, Customer Feedback

**Happy path — Pricing**
- `super_user` opens `/super-admin/pricing`, inline-edits a tier/add-on price (click, blur/Enter
  commits, Escape reverts) `[a]`; sync creates missing Paddle Products/Prices, archives old ones
  on a price change `[b]`

**Happy path — Credit grants**
- Grant standalone or from a pending feedback submission → ledger entry created; syncing to a
  real subscription attaches a one-time Paddle Discount (not an Adjustment — JOB-180) `[a/b]`
- Grant for an org with no real billing yet still creates the ledger entry, shows an amber
  "sync skipped" warning (`NO_PADDLE_SUBSCRIPTION` reason code) rather than failing outright `[a/b]`
- Org owner/admin views their own balance in Org Settings `[a]`; balance updates on its own via a
  light poll (10s while shown) rather than requiring a manual reload, since it changes
  asynchronously off a Paddle webhook with no local mutation to invalidate against `[a]`

**Happy path — Feedback**
- Any member submits feedback (type auto-populates a markdown template); "My submissions" shows
  status (Pending/Declined/Credit granted) `[a/b]`
- Super admin reviews, declines, or grants credit against a submission `[a/b]`

**Validation & error cases**
- Non-`super_user` on any `/super-admin/*` route or `/api/super-admin/**` endpoint → 403,
  frontend redirects to `/projects` with no hint the console exists `[a/b]`
- Price fields negative/missing → 400; unknown `tierId`/`addonKey` → 404 `[b]`
- **Grant amount below 5 → 400 `Amount must be at least 5`**, blocked both client- and
  server-side `[a/b]`; non-positive or non-integer amount → 400/disabled-submit `[a/b]`; blank/
  overlong reason → 400 `[b]`
- **Genuine Paddle failure during grant sync (not "no subscription," an actual API error) → the
  entire grant transaction rolls back**, 502 `CREDIT_SYNC_FAILED` — ledger must show zero new
  entries, not a partial/orphaned one `[b]`
- `submissionId` belonging to a different org than the one being credited → rejected (ADR-0043's
  explicit constraint) `[b]`; granting against an already-DECLINED/CREDITED submission → 409
  `ALREADY_REVIEWED` `[b]`
- `MEMBER` on `GET .../credits/balance` for their own org → 403, Owner/Admin only `[b]`
- Feedback: blank/overlong title or description → 400 `[b]`; declining an already-reviewed
  submission → 409 `[b]`; `GET /api/feedback/mine` never leaks another user's submissions within
  the same org `[b]`

**Edge cases**
- Price edit committed with the *same* value fires no network call at all `[a]`; non-numeric
  input reverts, no save attempted `[a]`
- Price change on a tier with existing active subscriptions — new Paddle Price created, old one
  archived (not deleted), existing subs keep referencing the old one until manually migrated `[c]`
- **Multiple unconsumed prior credit discounts folded into one combined discount on a new grant**,
  replacing (not stranding) the old discount ID `[b]`
- **Partial redemption**: a transaction total smaller than the attached discount is capped and
  fully consumed by Paddle regardless — the leftover must be re-synced as a *new* discount
  (`carryForwardRemainder`), verified via a fresh ledger row and the old one debited to net zero `[b]`
- **Discount expires unredeemed (90 days)** — no proactive re-sync; only re-synced on the org's
  *next* grant. An org with one fully-expired, never-consumed discount and no new grant keeps
  showing its old balance until the next grant touches it — explicit test locking in this known,
  accepted gap `[b]`
- Credit balance is a computed ledger **sum**, never a mutable column — verify it matches
  `sum(org_credits.amount)` exactly after a grant + partial consumption + carry-forward sequence,
  including negative debit rows `[b]`
- Credits never expire at the ledger level even though the Paddle-side discount does — balance
  must keep showing the full amount even after silent Paddle-side expiry `[b]`
- No notification fires on grant (deliberate V1 gap) — negative-assertion test, no email/toast for
  the credited org's users `[a]`
- Switching feedback type after the user has hand-edited the auto-inserted template preserves
  their edits rather than overwriting them `[a]`

---

### MIL-032 — §26. Golden-Path Simulation: Full Project Lifecycle (pre-merge)

Not a feature-area spec — one continuous narrative, multiple actors, run in `e2e-smoke` on every
PR (see §6 for the full design rationale). Written as a single ordered script, not grouped into
happy/error/edge, since the point is the sequence itself.

**Cast**: Alice (org creator → Owner), Bob (invited → Admin), Carol / Dave / a fifth member
(added to the org via search, then to one new project) — all drawn from a dedicated fixture
identity pool distinct from the standard seeded demo users (§6).

**Script**
1. Alice signs up / logs in as a fresh identity with no org membership, is prompted to create an
   organisation, creates it — the reseed fixture (§6) has already seeded a realistic-shaped active
   `org_subscriptions` row for this org, so no live Paddle checkout happens in the script itself;
   Alice's dashboard/nav reflect a fully unlocked org from this point on, matching what a real
   paying customer would see
2. Alice invites Bob by email; Bob logs in as himself, opens the invite link, accepts — becomes
   Admin
3. Alice or Bob searches for and adds Carol, Dave, and the fifth member as org Members
4. Alice creates a project and adds Bob (Admin) and all three Members as project members
5. Bob defines 2-3 job types and one job template
6. Alice creates two milestones
7. Alice creates three jobs, one per Member, assigning milestone/type/priority — one from the
   template (wildcard resolution exercised for free), two directly
8. **Carol's job (straightforward path)**: Carol logs in, moves it New → In Progress, adds a
   note, attaches a link (service-icon auto-detection exercised for free), moves it to Completed
9. **Dave's job (friction path)**: Dave moves it New → In Progress, blocks it with a reason;
   Bob checks in with a note (different actor than the assignee); Bob adds a `BLOCKED_BY`
   relationship to another job; Dave unblocks it, completes it
10. **Fifth member's job (gated path)**: they move it New → In Progress, then request approval
    instead of completing directly; Alice opens the approval queue, sees it, approves it — job
    flips to Completed, disappears from the queue
11. Throughout steps 8-10, not just after: Alice's Dashboard is checked to reflect the Blocked
    section (during step 9), the Pending Approvals section (during step 10's request), and
    correct status counts after each completion — not asserted only once at the end
12. Role-boundary spot checks woven in as they naturally arise: Carol cannot decide the fifth
    member's pending approval (403, and no such control rendered); no Member can reopen a
    Completed job (Owner/Admin-only, per §7); no Member can delete the project
13. Alice attempts to mark the project Completed while a job is still open (if timing allows) →
    409, correctly blocked; once all three jobs are Completed, marks it Completed → succeeds,
    Completed banner shown

**Definition of done for this spec specifically**
- All 13 steps above pass as one continuous script, not split into independent tests that reset
  state between them
- Uses the dedicated golden-path fixture identity pool (§6), not the standard demo users
- The fixture-seeded subscription (§6) unlocks every addon the script touches — no live Paddle
  call anywhere in this spec
- Runs in `e2e-smoke`, gated the same way as the other smoke checks — no external-service
  dependency, so no retry/backoff needed beyond what any other smoke check already has
- Re-runnable: a second full run against a freshly reseeded stack produces the same result, not
  a stale-state failure from the previous run
