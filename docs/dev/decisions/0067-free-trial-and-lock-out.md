# ADR-0067: Free Trial and Lock-out

**Status:** Proposed
**Date:** 2026-11-26
**Author:** Jovan

## Context

Every new organisation should get a 30-day free trial with no card: every add-on, small limits,
and a lock-out when the trial ends without a subscription. A no-card trial with everything on is
the easiest way to get a team to "try it with real jobs", and it's what the ads and landing page
lead to ("30 days free, no credit card needed").

Today it's the opposite. A new org gets **no add-ons** (`AddonAccessChecker` allows only internal
orgs or real Paddle billing), so new customers never see the features worth paying for, and
**nothing makes anyone pay**: the setup wall from ADR-0029/0030 was never built, and member and
project caps are only checked when a tier is downgraded. A new org can use the core app free
forever.

Promoted from JOB-419 (Future Consideration, PRJ-002) into PRJ-016 "Go-Live: Email, Operations,
Free Trial & Payments" / MIL-052 Free trial and lock-out. This ADR is JOB-431. It builds on
ADR-0066 (early access waitlist), whose sign-up codes set a per-org trial length.

### Facts from the existing system that shape this design

- **Access rule:** `AddonAccessChecker` is the single add-on rule, used by `@RequiresAddon`
  (`RequiresAddonAspect`) and by services (ADR-0052):
  - internal orgs (`org_subscriptions.is_internal`) get every add-on
  - otherwise the org needs real billing (`hasRealBilling`: a Paddle subscription ID and a status
    other than `CANCELED`) and the add-on in its subscription
  - `PAST_DUE` keeps reads and blocks add-on writes (ADR-0044)

  The frontend mirrors it in `features/org/addonAccess.ts` (`hasAddonAccess`,
  `isAddonUsableForWrites`) with `hasRealPaddleBilling`.
- **Add-ons:** 11 `AddonCode`s (DASHBOARD, APPROVALS, NOTES, JOB_STATUS_HISTORY, MILESTONES,
  JOB_RELATIONSHIPS, API_KEYS, JOB_TEMPLATES, RECURRING_SCHEDULING, JOB_LINKS, JOB_TYPES), gated on
  about 50 endpoints. Core job tracking is ungated.
- **Subscriptions:** `org_subscriptions` has one row per org with a `NOT NULL tier_id`, so a new
  org has **no row** until the owner stages a plan (`PUT /api/organisations/{orgId}/subscription`)
  or Paddle confirms one. Paddle fields and statuses are on the same row (V032–V036).
- **Tiers:** `subscription_tiers` has `max_members` and `max_projects` (NULL = unlimited). The
  smallest tier is 5 members / 3 projects. `SubscriptionService.validateDowngrade` is the only place
  caps are checked. Inviting, accepting an invite, creating a project and reopening a completed
  project (`ProjectService.updateStatus`) don't check anything.
- **Projects:** `ProjectRepository.countActiveByOrgId` counts `ACTIVE`, non-deleted projects.
  `COMPLETED` ones don't count.
- **One org per user:** the app allows one org per user (ADR-0063 amendment), and
  `OrganisationService.getMyOrganisation` finds it by membership. So the backend can resolve "the
  caller's org" from the user alone.
- **Paddle:** checkout and plan changes live under
  `/api/organisations/{orgId}/subscription/paddle/**` (`PaddleSubscriptionController`). The
  frontend shows them in `SubscriptionSection` and `PaddleBillingSection` on the org settings page.
  Paddle `trialing` maps to `ACTIVE` in `PaddleWebhookService`, which only matters for card trials
  (not used here).
- **Setup wall never built:** `LandingPage.tsx:125` has `// TODO MIL-012: branch on subscription
  state → setup wall`.
- **Scheduling and mail:** `@Scheduled` jobs exist (`DeadlineAlertJob`, daily at 06:00 UTC).
  `EmailService` sends only invites today, through Brevo (ADR-0059, ADR-0064) with en and sr
  message files. The user's language is in the server-synced preferences (`locale`, ADR-0061).
- **ADR-0066:** sign-up is closed to new orgs until launch (`OPSCLEAR_SIGNUP_OPEN`). Its job
  JOB-443 redeems a sign-up code (`TRIAL` = `TrialDays(30)`, a 30-day trial in total) at org
  creation and needs a per-org trial end date to write. JOB-443 is blocked by this ADR.
- **Paddle live:** the live Paddle setup is JOB-415 / MIL-053 (JOB-432). It must be done before the
  first trials end, 30 days after launch, or locked orgs can't pay.
- **Migrations:** latest on `main` is V044. ADR-0066's JOB-440 takes V045, so this milestone uses
  the next free number (V046 or later).

## Decision

1. **Every new org starts a trial** at creation: `organisations.trial_ends_at = now + 30 days`
   (`opsclear.trial.days`). A sign-up code can set a different length (ADR-0066, JOB-443).
2. **One access state per org**, derived in one place (`OrgAccessService`):

   | State | When |
   |---|---|
   | `INTERNAL` | `org_subscriptions.is_internal` |
   | `PAID` | real Paddle billing (`hasRealBilling`), including `PAST_DUE` |
   | `TRIAL` | `trial_ends_at` in the future |
   | `LOCKED` | anything else: trial over, or a cancelled subscription has run out |

   Evaluated in that order, so a confirmed Paddle subscription ends the trial at once.
3. **A trial org gets every add-on.** `AddonAccessChecker` treats `TRIAL` like internal for
   add-ons, reads and writes, and the frontend's `addonAccess.ts` mirrors it.
4. **Trial limits: 5 members and 3 active projects**, the same as the cheapest paid tier, so
   subscribing to it needs no clean-up.
5. **Member and project caps are enforced for trial and paid orgs alike**, with one check: on
   invite (members + pending invites), on invite accept, on project create and on reopening a
   completed project. Over the cap: blocked with an upgrade message.
6. **A locked org sees a paywall and loses nothing.** Every `/api` call from a locked org returns
   402 `OrgLocked`, except an allowlist (profile, preferences, the org itself, subscription and
   Paddle checkout, catalog, health). The frontend shows a paywall: owners subscribe through Paddle,
   others are told to ask the owner. After paying, everything is back.
7. **Two emails to the owner:** "Your trial ends in 3 days" and "Your trial has ended", sent by a
   daily job through Brevo, in the owner's language.
8. **A countdown banner:** owners see it for the whole trial, admins and members only in the
   last 3 days.
9. **Existing orgs get a fresh trial from launch day.** Non-internal orgs without real billing get
   a trial at deploy time. On launch day it's reset to launch + 30 days (runbook step).
10. **Trial length and caps are backend properties** (`opsclear.trial.days`,
    `opsclear.trial.max-members`, `opsclear.trial.max-projects`). Changing them affects new trials
    only. Existing trials keep their end date.

## Product decisions

| Topic | Decision |
|---|---|
| Who gets a trial | Every new org, at creation. No card. One trial per org. |
| Length | 30 days (decided 2026-11-25), a setting. Can be shortened later (for example to 14) once payments run. Sign-up codes can set a per-org length (ADR-0066). |
| During the trial | Every add-on. Max 5 members (including the owner) and 3 active projects. Completed projects don't count. |
| Over a cap | Blocked: "Your trial allows 5 members. Subscribe to add more." / "Your plan allows 3 active projects. Upgrade or complete a project." Owners get a Subscribe / Upgrade button; others "Ask your owner". |
| Paid tiers | Same caps check, reading the tier's `max_members` / `max_projects`. Paying orgs over their tier today keep what they have; only new invites and projects are blocked. |
| Pending invites | Count toward the member cap. Accepting also checks, in case the cap shrank. |
| Subscribing during the trial | Allowed at any time from org settings or the banner. The trial ends when Paddle confirms the subscription. Unused trial days are not carried over. |
| Trial ends, no subscription | Locked. Every app page shows the paywall. Owner: plan picker and Paddle checkout. Admins and members: "Your organisation's trial has ended. Ask <owner name> to subscribe." Profile, preferences and logout still work. No data is deleted, ever. |
| Cancelled subscription | When Paddle's period ends and the status becomes `CANCELED`, the org is locked the same way. |
| Banner | Owner: "Trial: N days left · Subscribe", the whole trial. Admins and members: "Trial ends in N days" in the last 3 days. Hidden when paid or internal. |
| Emails | To the owner: 3 days before the end, and on the day it ends ("your data is safe, subscribe to get back in"). en and sr, by the owner's language preference, default en. No "trial started" email. |
| Existing orgs | Non-internal orgs without real billing get a 30-day trial. On launch day it's reset to launch + 30 days. Internal and paying orgs are unaffected. |
| Abuse | Someone can create new orgs for repeated trials (one org per user, but new accounts are cheap). Accepted for now. Email verification (ADR-0059) and the per-org caps limit the value. |
| Data export when locked | Not in this milestone. |

## Technical design

### Database

`V0NN__org_trial.sql` (next free number after ADR-0066's V045):

```sql
ALTER TABLE organisations
    ADD COLUMN trial_ends_at              TIMESTAMPTZ,
    ADD COLUMN trial_ending_email_sent_at TIMESTAMPTZ,
    ADD COLUMN trial_ended_email_sent_at  TIMESTAMPTZ;

-- Existing orgs: a fresh trial, reset to launch + 30 days on launch day (DEPLOY.md runbook).
UPDATE organisations o
SET trial_ends_at = now() + interval '30 days'
WHERE o.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM org_subscriptions s
      WHERE s.org_id = o.id
        AND (s.is_internal
             OR (s.paddle_subscription_id IS NOT NULL
                 AND s.subscription_status IS NOT NULL
                 AND s.subscription_status <> 'CANCELED')));

CREATE INDEX idx_organisations_trial_ends_at ON organisations (trial_ends_at)
    WHERE trial_ends_at IS NOT NULL;
```

- `trial_ends_at` NULL means "never had a trial". Such an org is `LOCKED` unless it's internal or
  paid.
- The two `*_email_sent_at` columns make the daily email job idempotent.
- Check whether `organisations` has `deleted_at`, and match the real column names, before writing
  the backfill.
- Regenerate jOOQ.

### API

| Method | Path | Change |
|---|---|---|
| `GET` | `/api/organisations/mine`, `/api/organisations/{id}` | `OrganisationResponse` gains `access: { state, trialEndsAt, maxMembers, maxProjects, memberCount, pendingInviteCount, activeProjectCount }`. `maxProjects` null = unlimited. |
| any | `/api/**` (not allowlisted) | `402 Payment Required` `{ "error": "OrgLocked", "message": "Your organisation's trial has ended" }` when the caller's org is `LOCKED` (JWT or API key). |
| `POST` | `/api/organisations/{orgId}/invites` | new `403` `{ "error": "PlanLimitReached", "message": "…allows 5 members…" }` |
| `POST` | `/api/invites/{token}/accept` | same `403` when the org is full |
| `POST` | `/api/projects` | same `403` when active projects are at the cap |
| `PATCH` | project status `COMPLETED → ACTIVE` | same `403` |

**Lock-out allowlist:**
- `/api/users` and `/api/user/preferences`
- `/api/organisations/mine` and `GET /api/organisations/{id}`
- `/api/organisations/{orgId}/subscription/**`, including Paddle
- `/api/organisations/{orgId}/credits/**`
- `/api/subscriptions/catalog`, `/api/health`, `/api/webhooks/**`
- `/api/invites/{token}` (GET), `/api/public/**`, `/api/super-admin/**`

### Backend

- **Properties:** `opsclear.trial.days=${OPSCLEAR_TRIAL_DAYS:30}`,
  `opsclear.trial.max-members=${OPSCLEAR_TRIAL_MAX_MEMBERS:5}`,
  `opsclear.trial.max-projects=${OPSCLEAR_TRIAL_MAX_PROJECTS:3}`, bound to a `TrialProperties`
  record.
- **Trial at creation:** `OrganisationService.create` sets
  `trial_ends_at = now + trialProperties.days()`. JOB-443 (ADR-0066) then overrides it from a
  redeemed code in the same transaction.
- **`OrgAccessService`:**
  - `state(orgId)` returns `INTERNAL`, `PAID`, `TRIAL` or `LOCKED`, in that order, reusing
    `OrgSubscriptionRepository.isInternal` / `hasRealBilling` and the org's `trial_ends_at`
  - `limits(orgId)`:
    - internal: unlimited
    - paid: the subscription's tier
    - trial: the trial properties
    - locked: the trial properties, which don't matter since writes are blocked anyway
  - `requireMemberSlot(orgId)` and `requireProjectSlot(orgId)` throw
    `PlanLimitReachedException` (new, mapped to 403 `PlanLimitReached` in
    `GlobalExceptionHandler`)
- **`AddonAccessChecker`:**
  - `isAddonUsableForWrites`: internal **or trial** → true
  - a new `hasAddonAccess(orgId, addon)` for reads: internal, trial, or paid with the add-on
  - `RequiresAddonAspect` uses these instead of its own checks, so `@RequiresAddon` endpoints open
    during the trial
  - the Javadoc gets the trial rule
- **Caps wiring:**
  - `OrgInviteService.create` and `resend`: members + pending invites < `maxMembers`
  - `acceptInvite`: members < `maxMembers`
  - `ProjectService.create`, and `updateStatus` to `ACTIVE`: active projects < `maxProjects`
    when it's not null
  - `SubscriptionService.validateDowngrade` keeps its own checks
- **Lock-out:**
  - `OrgLockInterceptor` (`HandlerInterceptor`, registered in `WebConfig` for `/api/**` minus
    the allowlist) resolves the caller's org (`findByMember`, cached per request) and throws
    `OrgLockedException` (402 `OrgLocked`) when the state is `LOCKED`
  - API-key requests resolve the key's org
  - users without an org pass, since onboarding needs that
  - the SSE stream (`/api/notifications/stream`) is not allowlisted, so a locked client stops
    reconnecting after a 402
- **Trial emails:** `TrialEmailJob`, `@Scheduled(cron = "0 0 7 * * *", zone = "UTC")`:
  - "ending soon": state `TRIAL`, `trial_ends_at` within 3 days, `trial_ending_email_sent_at` null
  - "ended": `trial_ends_at` passed, state `LOCKED`, `trial_ended_email_sent_at` null, and
    `trial_ends_at` within the last 7 days (no mail for very old trials)
  - for each org, emails every owner in their `locale` preference (default en), then sets the
    column
  - `EmailService` gets `sendTrialEnding` and `sendTrialEnded`, using the shared layout with the
    support footer from ADR-0064 and a link to `/org/settings` (billing)
  - texts in `mail/messages_{en,sr}.properties`
- **Compose / `.env.prod.example`:** the three `OPSCLEAR_TRIAL_*` vars with defaults.
- **DEPLOY.md "Launch day"** (next to ADR-0066's step): reset existing trials,
  `UPDATE organisations SET trial_ends_at = now() + interval '30 days', trial_ending_email_sent_at = NULL, trial_ended_email_sent_at = NULL WHERE trial_ends_at IS NOT NULL AND <not internal, not paid>;`,
  and check that Paddle live (JOB-415) is done before day 27.
- **Tests:**
  - `OrgAccessService`: every state and their order
  - limits for internal, paid (limited and unlimited tiers) and trial
  - invite, resend, accept, project create and reopen at the cap: 403; under the cap: OK
  - add-on endpoints open for a trial org and closed when locked
  - the interceptor: 402 for a locked org on a gated and an ungated endpoint, the allowlist
    passes, an API key of a locked org gets 402, a user without an org passes
  - the email job: sends once per state, the right language, no mail for internal or paid orgs or
    trials over 7 days old
  - migration backfill: integration test on seeded orgs

### Frontend

- **Types and access:**
  - `OrganisationResponse.access` in `types`
  - `addonAccess.ts`: `state === 'TRIAL'` gives access like internal, for reads and writes
  - a hook `useOrgAccess()` from `OrgContext`
- **Paywall:**
  - `OrgRequiredRoute` (or the app layout) renders `PaywallPage` when `access.state === 'LOCKED'`,
    for every app route except `/settings` (personal) and logout
  - **Owner:** "Your trial has ended. Your data is safe. Choose a plan to continue." with the
    existing plan picker and Paddle checkout (`SubscriptionSection` / `PaddleBillingSection`,
    reused, not copied)
  - **Others:** "Ask <owner name> to subscribe" and Log out
- **402 handling:** the API client invalidates the org query on any 402 `OrgLocked`, so the
  paywall appears without a reload.
- **Trial banner:** in the app layout. Owner: "Trial: N days left · Subscribe" (links to
  `/org/settings` billing) for the whole trial. Others: "Trial ends in N days" in the last 3 days.
  Days are calculated from `trialEndsAt`, with dates through the shared formatter (ADR-0061).
- **Limits:**
  - the invite form and the new project modal show usage ("3 of 5 members", "2 of 3 active
    projects") from `access`
  - a 403 `PlanLimitReached` shows its message, with Subscribe / Upgrade for owners and "Ask your
    owner" for others
  - reopening a completed project at the cap shows the same message
- **Org settings billing section:** shows "Free trial, ends <date>" during the trial, above the
  plan picker.
- **Landing:** remove the `TODO MIL-012`. An authenticated user goes to the app as today, and the
  paywall covers the locked case.
- en and sr for every text. Phone width.
- **E2E (E2E.md):**
  - a trial org (seeded or created) sees the banner and an add-on page
  - at the cap, the invite shows the limit message
  - a locked org (trial end set in the past through a test helper or seed) sees the paywall; an
    owner sees the plan picker and a member sees "Ask your owner"
  - the seed may need a locked org and a trial org (`scripts/seed.sh`)

### Constraints & edge cases

- **Paddle must be live before day 30 after launch** (JOB-415, MIL-053), or locked owners can't
  pay. Launch-day runbook checks it.
- **Paddle `PAST_DUE`:** counts as `PAID` (not locked). Add-on writes stay blocked as today
  (ADR-0044).
- **Webhook delay:** after checkout, the org stays `TRIAL` or `LOCKED` until Paddle's webhook
  confirms. The paywall polls the org (as the billing section already does after checkout) and
  lifts when the state changes.
- **Clock:** "N days left" rounds up to whole days in the user's time zone. The backend compares
  `trial_ends_at` with `now()` in UTC.
- **Shortening the trial later:** changing `opsclear.trial.days` affects only new orgs.
- **Super users:** no exemption from lock-out for their own org (internal orgs are never locked).
  `/api/super-admin/**` is allowlisted.
- **Orgs over a cap today** (paid or trial): nothing is removed. They can't add more until under
  the cap.
- **Invite accept at the cap:** the invited person sees "This organisation is full. Ask the owner
  to upgrade." The invite stays valid.
- **API keys:** a locked org's integrations stop with 402, which the error tells them.
- **Data:** lock-out never deletes. Deleting long-locked orgs is a separate, later decision
  (retention in the Privacy Policy, MIL-053).

## Alternatives considered

### A trial row in `org_subscriptions` (decided against)

It keeps billing state in one table, but it needs a fake tier and mixes app-made rows into a table
that Paddle webhooks own and that `hasRealBilling` reads. A column on `organisations` is clearer.

### A Paddle card trial (decided against)

Paddle can run a trial on a subscription with a card on file. It converts better per trial, but
asking for a card up front cuts sign-ups, and the posts promise "no credit card needed".

### 3 members and 1 project (decided against)

The first proposal. One project is a thin trial, and the cheapest tier would give more than the
trial, which is the wrong way round.

### Read-only after the trial (decided against)

Friendlier, but every write path needs a check, and a read-only org can be used for reference
indefinitely without paying.

### Paywall with data export (decided against for now)

Good faith for portability, but there's no export today. It can come later, with the retention
decision.

### Annotation per controller for the lock-out (decided against)

Explicit, but every new endpoint has to remember it. An interceptor with a short allowlist fails
closed.

### Trial caps only, paid caps later (decided against)

The check is the same code. Leaving paid caps open means a paying org can outgrow its tier
unnoticed.

### Carry unused trial days over (decided against)

It needs a Paddle trial period on the subscription and the card-trial path. Ending the trial on
payment is simple and common.

### Treat existing orgs as expired, or decide each by hand (decided against)

Locking testers out on launch day is harsh, and deciding by hand doesn't scale to the next deploy.
A fresh trial from launch day is fair and is one SQL statement.

## Consequences

### Positive

- New customers see the whole product for 30 days, without a card.
- There's finally a reason to pay, and the ADR-0029 setup wall is built (as the paywall).
- Caps hold for trial and paid orgs, so tiers mean something.
- Owners are warned twice before the lock-out, and nothing is lost by it.
- One place (`OrgAccessService`) answers "what can this org do", in the backend and mirrored in the
  frontend.

### Negative

- A locked org's integrations (API keys) stop. That's intended, but support will hear about it.
- Repeat trials by new accounts are possible.
- Launch day gets two manual steps (reopen sign-up, reset trials), and Paddle live becomes a hard
  deadline.
- The interceptor adds an org lookup to every authenticated request. It's one indexed query, and
  can be cached per request.

### Neutral

- `PAST_DUE` behaviour is unchanged.
- Paid tiers' prices and Paddle checkout are unchanged.

## Implementation order

Each job is one layer (JOBS.md). Compose, `.env.prod.example`, the seed and DEPLOY.md are neutral
and go with the backend job that needs them.

1. **DB: Trial columns on organisations.** `trial_ends_at` and the two email timestamps, the
   backfill for existing orgs, the index, jOOQ. Blocked by ADR-0066's JOB-440 (migration number
   order).
2. **Backend 1: Trial access state and lock-out.** `TrialProperties`, the trial at creation,
   `OrgAccessService` (state, limits), `AddonAccessChecker` and `RequiresAddonAspect` with the
   trial, `access` in `OrganisationResponse`, `OrgLockInterceptor` with the allowlist and 402,
   compose and `.env.prod.example`, the DEPLOY.md launch-day reset, seed orgs in trial and locked
   states, tests. Blocked by 1. **Unblocks ADR-0066's JOB-443.**
3. **Backend 2: Member and project caps.** `requireMemberSlot` / `requireProjectSlot`,
   `PlanLimitReachedException`, wired into invite, resend, accept, project create and reopen, for
   trial and paid orgs, tests. Blocked by 2.
4. **Backend 3: Trial ending and ended emails.** `TrialEmailJob`, the two `EmailService` methods
   and templates (en, sr), the sent-at columns, tests. Blocked by 2.
5. **Frontend 1: Trial banner and paywall.** `useOrgAccess`, the `addonAccess.ts` trial rule, the
   banner, `PaywallPage` (owner checkout, member message), 402 handling, the billing section's trial
   line, the landing TODO, en and sr, E2E. Blocked by 2.
6. **Frontend 2: Plan limit messages and usage.** Usage in the invite form and the new project
   modal, `PlanLimitReached` handling with Subscribe / Upgrade or "Ask your owner", reopen at the
   cap, en and sr, E2E. Blocked by 3 and 5.

Outside this milestone: JOB-443 (ADR-0066, redeem sign-up codes) moves from blocked by JOB-431 to
blocked by job 2 here. Paddle live (JOB-415 / MIL-053) must be done within 30 days of launch.

## Amendment (2026-11-26): lock-out switch

Payments are deferred (Paddle live, JOB-432, has no date), so the lock-out ships behind a switch,
`opsclear.lockout.enabled=${OPSCLEAR_LOCKOUT_ENABLED:false}` (implemented in JOB-445):

- **While false (the default):** `OrgLockInterceptor` never blocks. `OrgAccessService.state()` still
  reports `LOCKED` for an expired trial (or an org that never had one), but `AddonAccessChecker`
  treats `LOCKED` like `TRIAL`, so add-on access stays open.
- **`OrgAccessResponse` gains `lockoutEnabled`**, so the frontend shows "contact us" instead of the
  paywall for a `LOCKED` org while the switch is off.
- **Turn it on when Paddle is live**: set `OPSCLEAR_LOCKOUT_ENABLED=true` and restart the backend.
  Then Decision 6 applies as written: 402 `OrgLocked` outside the allowlist.
- **Launch-day reset** (DEPLOY.md): matches every non-internal, unpaid org, not only orgs with a
  `trial_ends_at`, so orgs created after V046 and before JOB-445 (NULL) get a trial too. DEPLOY.md
  also has a one-off statement for those orgs right after JOB-445 deploys.

## References

- JOB-419 (Future Consideration), JOB-431 (this ADR), PRJ-016 / MIL-052
- ADR-0029 Public Landing Page (setup wall), ADR-0030 Subscription Data Model, ADR-0031 Feature
  Gating, ADR-0044 Paddle Payment Integration (`PAST_DUE`), ADR-0052 (completion gate uses
  `AddonAccessChecker`), ADR-0059 Email Verification and Transactional Email, ADR-0061 Additional
  User Preferences (`locale`, date formatter), ADR-0063 Manual Test Scenarios (one org per user),
  ADR-0064 Domain Email and Support Contact (footer), ADR-0066 Early Access Waitlist (sign-up codes,
  closed sign-up, JOB-443)
- JOB-415 / JOB-432 (Paddle live, MIL-053)
- `backend/src/main/java/com/opsclear/service/AddonAccessChecker.java`,
  `backend/src/main/java/com/opsclear/aop/RequiresAddonAspect.java`,
  `backend/src/main/java/com/opsclear/service/SubscriptionService.java`,
  `backend/src/main/java/com/opsclear/service/OrganisationService.java`,
  `backend/src/main/java/com/opsclear/service/OrgInviteService.java`,
  `backend/src/main/java/com/opsclear/service/ProjectService.java`,
  `backend/src/main/java/com/opsclear/repository/OrgSubscriptionRepository.java`,
  `backend/src/main/resources/db/migration/V016__subscription_schema.sql`,
  `frontend/src/features/org/addonAccess.ts`, `frontend/src/features/org/SubscriptionSection.tsx`,
  `frontend/src/features/org/PaddleBillingSection.tsx`, `frontend/src/components/OrgRequiredRoute.tsx`,
  `frontend/src/features/landing/LandingPage.tsx`
