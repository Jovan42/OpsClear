# ADR-0068: Payments Go-Live

**Status:** Proposed
**Date:** 2026-11-26
**Author:** Jovan

## Context

OpsClear launches with a free trial and **no live payments**. On 2026-11-26 the developer decided
to open paid plans only once the first trial customers show it's worth it. Until then, trial
lock-out is off (`OPSCLEAR_LOCKOUT_ENABLED=false`, ADR-0067 amendment in JOB-445), and banners and
emails say "contact us" instead of "Subscribe".

This ADR plans the switch now, while the decisions are fresh, so that turning payments on later is
a set of ready, undated jobs and a runbook, not a new design. It covers going from Paddle sandbox to
live, clearing sandbox billing data from prod, giving expired trials a fair grace period, and
turning lock-out on.

The legal pages were split out of this milestone on 2026-11-26 into JOB-450 (go-live, dated),
because launch needs Terms and Privacy even without payments. Paddle's domain review needs them, so
this milestone depends on JOB-450.

Promoted from JOB-426 (Future Consideration, PRJ-002) into PRJ-016 "Go-Live: Email, Operations,
Free Trial & Payments" / MIL-053 Payments and legal go-live. It replaces the Maintenance chore
JOB-415 (Paddle live). This ADR is JOB-432.

### Facts from the existing system that shape this design

- **Paddle integration is complete in sandbox** (ADR-0044):
  - checkout, plan changes, cancel and resume, transactions and the payment method update under
    `/api/organisations/{orgId}/subscription/paddle/**`
  - webhooks at `/api/webhooks/paddle` (`PaddleWebhookService`, signature from
    `PADDLE_WEBHOOK_SECRET`)
  - credits as Paddle discounts (`CreditService`)
- **Sandbox is hard-coded in the frontend:** `frontend/src/features/org/paddleCheckout.ts:15`
  calls `initializePaddle({ environment: 'sandbox', token: VITE_PADDLE_CLIENT_TOKEN })`. The token
  is a build arg (`docker-compose.prod.yml`, `frontend/Dockerfile`).
- **The backend is configurable:** `paddle.base-url=${PADDLE_BASE_URL:https://sandbox-api.paddle.com}`,
  plus `PADDLE_API_KEY` and `PADDLE_WEBHOOK_SECRET`.
- **The catalog is synced from our database:**
  - `POST /api/super-admin/pricing/sync` (`SuperAdminPricingService.syncCatalogToPaddle`) creates
    Paddle products and prices from `subscription_tiers` and `subscription_addons`
  - it stores `paddle_product_id`, `paddle_price_id_monthly` and `paddle_price_id_annual` on each
    row (V033)
  - 35 tiers (5–50 members × 3, 5, 10, 20 or unlimited projects) and 11 add-ons, all in EUR (V033:
    Paddle has no RSD). Prices are edited on the super-admin Pricing page.
- **Sandbox IDs live in prod data:**
  - `org_subscriptions.paddle_subscription_id`, `subscription_status` and the pending and
    scheduled fields (V032–V036)
  - `organisations.paddle_customer_id` (V037)
  - `OrgSubscriptionRepository.hasRealBilling` counts any `paddle_subscription_id` with a status
    other than `CANCELED` as paid, so a sandbox subscription would count as `PAID` under ADR-0067
    forever, and its webhooks never arrive from the live account.
- **ADR-0067:**
  - org states `INTERNAL` → `PAID` → `TRIAL` → `LOCKED`
  - `organisations.trial_ends_at` plus the ending and ended emails (JOB-447)
  - lock-out with a 402 and the paywall (JOB-445, JOB-448)
  - the lock-out switch and the "contact us" texts while it's off (JOB-445, JOB-447, JOB-448)
- **Public catalog:** `GET /api/subscriptions/catalog` (`SubscriptionCatalogController`,
  `CatalogResponse`) is public and allowlisted during lock-out.
- **Migrations:** ADR-0066 takes V045 and ADR-0067 the next one. This milestone uses the next free
  number after those.

## Decision

1. **Sell the current catalog**, prices reviewed before the switch. The 35 tiers and 11 add-ons,
   EUR only, Paddle as merchant of record. No new plan model.
2. **One Paddle environment setting, owned by the backend.**
   `paddle.environment=${PADDLE_ENVIRONMENT:sandbox}` (`sandbox` or `production`). The public
   catalog returns it with the client token, and the frontend initialises Paddle.js from that at
   runtime. The hard-coded `'sandbox'` and the build-time token go away, so frontend and backend can
   never disagree, and switching needs no frontend rebuild.
3. **Sandbox billing data is cleared at the switch.** The catalog's Paddle IDs, every org
   subscription's Paddle fields and `organisations.paddle_customer_id` are cleared. Internal orgs are
   untouched. Orgs that "paid" in sandbox fall back to the trial rules.
4. **Expired trials get a 14-day grace period and an email** when paid plans open: every
   non-internal, unpaid org gets `trial_ends_at = now + 14 days` and the email "Paid plans are live.
   Subscribe within 14 days to keep going." After that, ADR-0067's ending and ended emails and
   lock-out apply.
5. **The switch is two super-admin actions**, both allowed only when
   `paddle.environment=production`, idempotent, and recorded with who and when:
   - **Reset sandbox data** (decision 3)
   - **Announce paid plans** (decision 4)

   Lock-out itself is `OPSCLEAR_LOCKOUT_ENABLED=true`, set after the announcement.
6. **A real purchase is tested end to end** before the announcement: checkout, webhook, add-on
   unlocked, then refunded.
7. **The jobs are created undated** (priority LOW). The developer dates them when deciding to open
   payments.

## Product decisions

| Topic | Decision |
|---|---|
| When | Not scheduled. When the first trial customers show paid plans are worth it. |
| What's sold | Current catalog: tiers by members × projects, plus add-ons, monthly or annual (annual ≈ 10 months). EUR. Prices reviewed on the super-admin Pricing page before syncing to live. |
| Taxes and invoices | Paddle, as merchant of record, handles VAT, invoices and receipts. No change. |
| Orgs that "paid" in sandbox | Treated as unpaid after the reset: they get the 14-day grace like everyone else and subscribe for real. |
| Expired trials at the switch | 14-day grace from the announcement, plus an email to owners. |
| Active trials at the switch | Also moved to now + 14 days if their trial would end sooner. A trial that ends later keeps its date. |
| Announcement email | To every owner of a non-internal, unpaid org: "Paid plans are live. Subscribe within 14 days to keep going." Plan and pricing link, support footer (ADR-0064), en and sr by the owner's language. |
| After the grace | Normal ADR-0067 flow: the ending-soon email 3 days before, the ended email, then the paywall (lock-out on). |
| Banner and email texts | Switch from "contact us" to "Subscribe" automatically once lock-out is on (the condition built in JOB-447 and JOB-448). |
| Refund policy | As in the legal pages (JOB-450): no refunds for paid periods except where the law or Paddle's terms require. |
| Test purchase | The developer buys the cheapest tier monthly for a dedicated non-internal test org with a real card, checks the add-ons unlock and the webhook lands, then refunds it in the Paddle dashboard. |

## Technical design

### Database

`V0NN__billing_go_live.sql` (next free number after ADR-0066 and ADR-0067):

```sql
CREATE TABLE billing_go_live_steps (
    step     VARCHAR(30) PRIMARY KEY,          -- 'RESET_SANDBOX', 'ANNOUNCE'
    done_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    done_by  UUID        NOT NULL REFERENCES users (id),
    details  JSONB                             -- counts: orgs reset, orgs given grace, emails queued
);

ALTER TABLE organisations
    ADD COLUMN paid_plans_email_sent_at TIMESTAMPTZ;
```

- A row in `billing_go_live_steps` makes each action run once.
- `paid_plans_email_sent_at` makes the announcement email idempotent per org (sent by a job, like
  JOB-447's).
- Regenerate jOOQ.

### API

| Method | Path | Auth | Change |
|---|---|---|---|
| `GET` | `/api/subscriptions/catalog` | none | `CatalogResponse` gains `paddle: { environment: "sandbox" \| "production", clientToken }` |
| `GET` | `/api/super-admin/billing/go-live` | super user | `200 { environment, lockoutEnabled, steps: [{ step, doneAt, doneBy, details }] }` |
| `POST` | `/api/super-admin/billing/go-live/reset-sandbox` | super user | `200 { orgsReset, subscriptionsCleared, catalogRowsCleared }`. `409` if already done. `403` unless `paddle.environment=production`. |
| `POST` | `/api/super-admin/billing/go-live/announce` | super user | `200 { orgsGivenGrace, emailsQueued }`. `409` if already done. `403` unless production. `409` unless reset-sandbox is done and the catalog has live price IDs. |

### Backend

- **Properties:** `paddle.environment=${PADDLE_ENVIRONMENT:sandbox}` and
  `paddle.client-token=${PADDLE_CLIENT_TOKEN:}`. Validate `environment` at startup (`sandbox` or
  `production`). Log a warning when `production` is paired with the sandbox base URL, or `sandbox`
  with the live one.
- **`SubscriptionCatalogService.getCatalog`:** adds the `paddle` block. The client token is public
  by design (Paddle.js).
- **`BillingGoLiveService`:**
  - **`resetSandbox(callerId)`**, in one transaction:
    - clear `paddle_product_id`, `paddle_price_id_monthly` and `paddle_price_id_annual` on tiers
      and add-ons
    - for non-internal `org_subscriptions`, clear `paddle_subscription_id`, `subscription_status`,
      `paddle_scheduled_cancellation_at`, `pending_tier_id`, `paddle_current_period_starts_at` and
      `paddle_pending_downgrade_effective_at`
    - clear `organisations.paddle_customer_id` for non-internal orgs
    - clear credit-related Paddle discount IDs if any are stored (check `CreditService`'s columns)
    - insert the `RESET_SANDBOX` step
  - **`announce(callerId)`**, in one transaction:
    - for every non-internal org without real billing, set
      `trial_ends_at = GREATEST(trial_ends_at, now() + 14 days)` (a NULL counts as now) and reset
      `trial_ending_email_sent_at` and `trial_ended_email_sent_at`
    - insert the `ANNOUNCE` step with counts
  - Both are guarded by environment and by step order, and are `@RequiresSuperUser` through the
    controller.
- **`PaidPlansEmailJob`**, or an extension of JOB-447's `TrialEmailJob`: after `ANNOUNCE`, emails
  owners of orgs with `paid_plans_email_sent_at` null and state `TRIAL`, in their `locale`, through
  `EmailService.sendPaidPlansLive`, then sets the column. It runs hourly until none are left, so a
  big list doesn't block the request.
- **Compose / `.env.prod.example`:**
  - `PADDLE_ENVIRONMENT` and `PADDLE_CLIENT_TOKEN` go to `backend`
  - the frontend build arg `VITE_PADDLE_CLIENT_TOKEN` is removed once the frontend reads it from
    the catalog
- **Tests (integration):**
  - the catalog returns the environment and token
  - reset clears exactly the listed fields, keeps internal orgs, and runs once (`409` the second
    time)
  - both actions return `403` in sandbox
  - announce needs reset and live price IDs
  - the grace rule: expired → now + 14, active ending sooner → now + 14, later end kept, internal
    and paid untouched
  - the email job sends once per org in the owner's language
  - a non-super user gets `403`

### Frontend

- **`paddleCheckout.ts`:** `initializePaddle({ environment, token })` with both from the catalog
  query (`useSubscriptionCatalog`, already used by the plan picker). No checkout until the catalog
  has loaded. Remove `VITE_PADDLE_CLIENT_TOKEN` from the Dockerfile, compose and `vite-env.d.ts`.
- **Super-admin "Billing go-live" tab** (`SuperAdminNav`):
  - shows the environment, `lockoutEnabled` and each step's status (done at, by, counts)
  - two buttons, **Reset sandbox data** and **Announce paid plans**, each disabled unless
    allowed, with a confirm dialog that explains what it changes
  - a checklist from the runbook, with links to the Pricing page and its sync
- **Texts:** no work. The "Subscribe" vs "contact us" switch follows `access.lockoutEnabled`
  (JOB-447, JOB-448).
- en and sr. E2E: the super-admin tab renders with the steps (stubbed API), and Paddle checkout
  initialises from the catalog values (Vitest with a mocked `initializePaddle`).

### Go-live runbook (DEPLOY.md, Chore)

1. Prerequisites:
   - JOB-450 merged, so the legal pages are live with real company details
   - `support@opsclear.app` works (ADR-0064)
   - ADR-0067 and ADR-0066 jobs done
2. **Paddle live account:** business and payout details, domain review for `opsclear.app` (Terms,
   Privacy and Refunds URLs, support email), and the seller's support email set (ADR-0064).
3. **Live credentials:** API key, client-side token, webhook destination
   `https://opsclear.app/api/webhooks/paddle` with the subscription and transaction events (same
   list as sandbox), and its secret.
4. `.env.prod`:
   - `PADDLE_ENVIRONMENT=production`
   - `PADDLE_BASE_URL=https://api.paddle.com`
   - the live `PADDLE_API_KEY`, `PADDLE_CLIENT_TOKEN` and `PADDLE_WEBHOOK_SECRET`
   - keep `OPSCLEAR_LOCKOUT_ENABLED=false`

   Then `up -d backend`.
5. Super-admin → Billing go-live → **Reset sandbox data**.
6. Super-admin → Pricing: review prices → **Sync to Paddle**. Check the products in the live
   dashboard.
7. **Test purchase:** a dedicated non-internal test org buys the cheapest tier monthly with a real
   card. Check the webhook in the Paddle dashboard, the org shows `PAID`, and the add-ons work. Then
   refund it in Paddle and check the org returns to the trial rules once the subscription is
   cancelled.
8. Super-admin → **Announce paid plans**. Check emails go out (Brevo logs).
9. `OPSCLEAR_LOCKOUT_ENABLED=true`, then `up -d backend`. Banners and emails now say "Subscribe".
10. Record the date in DEPLOY.md, and mark ADR-0044, ADR-0067 and this ADR Implemented.

### Constraints & edge cases

- **Order matters:** reset before syncing (sync skips rows that already have IDs), test before
  announcing, announce before lock-out (otherwise expired trials lock before getting their grace).
  The API enforces reset → announce. The runbook covers the rest.
- **Webhooks from sandbox:** after the switch the live secret rejects any stray sandbox webhook
  (signature mismatch). Expected, and logged.
- **Credits:** Paddle discounts made in sandbox for credits don't exist in live. Credit balances in
  our database stay and attach to the new live subscription when it's created
  (`CreditService.syncCreditToPaddle`). The reset clears stored sandbox discount IDs.
- **Grace for paid-then-cancelled sandbox orgs:** they're unpaid after the reset, so they get the
  grace like everyone else.
- **Rollback:** setting `PADDLE_ENVIRONMENT=sandbox` again works for checkout, but the reset can't
  be undone (sandbox IDs are gone). That's acceptable, since sandbox data has no value after the
  switch.
- **Lock-out off for a long time:** nothing in this ADR depends on how long payments wait. Trials
  keep expiring into the "contact us" state (ADR-0067 amendment).
- **Ads and the Privacy Policy:** unaffected. They're JOB-450.

## Alternatives considered

### Simplify to a few bundled plans (decided against)

Three plans with add-ons included would be easier to sell, but they change the subscription model,
the picker and the Paddle sync. The current catalog is built and synced. Revisit after real
customers show what they buy.

### Frontend build arg for the Paddle environment (decided against)

`VITE_PADDLE_ENVIRONMENT` next to the token is simple, but frontend and backend are set separately
and need a rebuild. A runtime value from the backend can't drift.

### SQL script and a manual email for the switch (decided against)

Fewer endpoints, but more manual steps on the droplet and a hand-made mailing, for a one-time
operation where mistakes hit paying customers. Two guarded, recorded, idempotent actions are safer.

### Reset only the catalog (decided against)

Keeping orgs' sandbox subscriptions leaves them `PAID` forever with no live webhooks. Clearing them
is the only consistent state.

### Lock immediately, or a 7-day grace (decided against)

Teams that have used OpsClear for weeks while lock-out was off deserve notice. 14 days covers a
billing cycle's worth of decision time.

### Leave payments unplanned until needed (decided against)

The decisions are clear now. Undated jobs and a runbook make opening payments a scheduling decision,
not a design one.

## Consequences

### Positive

- Opening paid plans is a runbook: the jobs, the order, the guards and the emails are decided.
- One environment setting switches Paddle on both sides, with no rebuild.
- Customers who've used the trial get notice and a fair window.
- Sandbox leftovers can't masquerade as paid subscriptions.

### Negative

- Revenue waits until the developer decides. Meanwhile trials end in "contact us".
- The super-admin area grows by a tab used once.
- The reset is irreversible (by design).

### Neutral

- Taxes, invoices and refunds stay with Paddle as merchant of record.
- ADR-0044's flows are unchanged. Only the environment and the data move.

## Implementation order

Each job is one layer (JOBS.md). **All are created undated, priority LOW**, and dated when the
developer decides to open payments. Neutral paths (compose, `.env.prod.example`, DEPLOY.md) go with
the job that needs them.

1. **DB: Billing go-live steps.** `billing_go_live_steps` and
   `organisations.paid_plans_email_sent_at`, jOOQ. Blocked by ADR-0067's DB job (JOB-444, migration
   order).
2. **Backend: Paddle environment setting and billing go-live actions.** `paddle.environment` and
   `paddle.client-token`, the catalog's `paddle` block, `BillingGoLiveService` (reset, announce),
   the super-admin endpoints, the paid-plans email and job (en, sr), compose and
   `.env.prod.example`, tests. Blocked by 1 and by ADR-0067's JOB-445 and JOB-447.
3. **Frontend: Paddle environment from the catalog and the billing go-live tab.** Paddle.js
   initialised from the catalog, the build-arg token removed, the super-admin tab with both actions
   and the checklist, en and sr, tests. Blocked by 2.
4. **Chore: Paddle live account and go-live runbook.** The DEPLOY.md runbook above, then the manual
   steps: live account and domain review, live keys and webhook, env, reset, sync, test purchase and
   refund, announce, lock-out on. Blocked by 3 and by JOB-450 (legal pages live).

## References

- JOB-426 (Future Consideration), JOB-432 (this ADR), PRJ-016 / MIL-053. Replaces JOB-415.
- JOB-450 (legal pages for go-live, split out of this milestone on 2026-11-26)
- ADR-0044 Paddle Payment Integration, ADR-0030 Subscription Data Model, ADR-0064 Domain Email and
  Support Contact, ADR-0066 Early Access Waitlist, ADR-0067 Free Trial and Lock-out (and its
  lock-out switch amendment in JOB-445)
- `frontend/src/features/org/paddleCheckout.ts`,
  `backend/src/main/java/com/opsclear/service/SuperAdminPricingService.java`,
  `backend/src/main/java/com/opsclear/service/SubscriptionCatalogService.java`,
  `backend/src/main/java/com/opsclear/repository/OrgSubscriptionRepository.java`,
  `backend/src/main/java/com/opsclear/service/CreditService.java`,
  `backend/src/main/resources/db/migration/V032__paddle_subscription_fields.sql` to `V037__paddle_customer_id_on_organisations.sql`
