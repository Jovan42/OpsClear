# ADR-0044: Payment Gateway Integration (Paddle)

**Status:** Proposed
**Date:** 2026-08-10
**Author:** Jovan Manojlovic

## Context

The subscription data model and feature gating (PRJ-005) are complete — orgs already select a tier + add-ons, and `@RequiresAddon`/`hasAddon()` enforce access based on that selection. What's missing is a real payment processor: today, subscription state is tracked but no money actually changes hands. This ADR wires up a payment processor so subscriptions collect real payments, and gives JOB-160/ADR-0043's credit ledger a mechanism to actually discount an invoice.

This ADR originally targeted Stripe directly. **Stripe does not support Serbia** as a seller/merchant country (confirmed against Stripe's published supported-countries list) — the registered business itself must be in a supported jurisdiction, not just the payout bank account, so a direct Stripe merchant account isn't available without foreign incorporation. Rather than incorporate abroad (Stripe Atlas) purely to unblock this, the decision is to integrate through a **Merchant of Record (MoR)** instead: the MoR becomes the legal seller, we're a vendor to them, and MoR country requirements for vendors are materially broader than Stripe's merchant requirements. **Paddle** is the chosen MoR (see "Alternatives considered" for Lemon Squeezy/FastSpring).

This is still the highest-stakes piece of work across this whole project so far — real money, webhook-driven async state, and a direct dependency for feature access (a failed payment eventually restricts access). Treated more carefully and deliberately than the polish-phase work that preceded it. There are currently no real paying customers — the app is self-served by its own team — which removes any existing-customer migration pressure and gives room to build and validate this fully in Paddle's sandbox before it matters for real.

**Resolved (JOB-171):** Paddle's seller/vendor onboarding accepted a Serbia-registered business and Serbia as a payout destination — sandbox API keys and webhook secret are live and everything below has been built and validated against them. Live keys are a separate, deliberate step (JOB-180), not a side effect of any of this work.

## Decision

Integrate Paddle Billing (Subscriptions + Prices + Transactions), relying on Paddle's own native mechanisms — retries/dunning, proration, tax, cancellation — rather than building custom logic for problems Paddle already solves correctly as an MoR.

## Product decisions

- **Paddle Billing**, not a custom charge-per-period system — proration, retries, invoicing, *and* global tax compliance all come from Paddle for free. Tax handling in particular is a bigger win than the original Stripe-only plan: Paddle handles VAT/sales tax as the merchant of record, whereas raw Stripe would have required OpsClear to separately register for and remit tax in every jurisdiction sold into (or opt into Stripe Tax as an extra product).
- **Card data never touches OpsClear's backend** — Paddle.js, not a full hosted-page redirect. Shipped as **inline** checkout (`displayMode: 'inline'`), not Paddle's default overlay — the overlay has no setting to fully hide the per-item quantity stepper even when a Price's quantity is locked to 1, and inline is the only way to remove that UI element entirely (confirmed against Paddle's docs; required building a small custom item-list summary above the embedded frame, since inline mode doesn't render Paddle's own line-item list the way overlay does). The backend only ever sees a payment method reference, never raw card data.
- **Internal orgs are never billed.** `OrgSubscriptionModel.isInternal` already exists and `RequiresAddonAspect` already skips add-on checks for internal orgs. This ADR extends the same flag: no Paddle Customer/Subscription is ever created for an internal org, and the new `subscription_status` check (below) is skipped for them the same way the add-on check already is — no new mechanism, just reusing what's there.
- **No separate OpsClear-side grace period.** Paddle's own dunning/retry logic (via its "Retain" tooling) spreads failed-payment retries over a window before giving up, the same shape as Stripe's Smart Retries. The entire span Paddle is still retrying is treated as the grace period — a visible "payment past due" state, but no lockout — and access only actually restricts once Paddle reports the terminal outcome (subscription reaches `past_due` exhausted → `canceled`/`paused`). Exact status names/transitions to confirm against Paddle's current subscription status model during implementation. No second, duplicate timer maintained in OpsClear.
- **Past-due orgs get read-only access**, not a full lockout — they can still view their data (avoids "did I lose my work" panic) but can't create/edit until payment recovers or the subscription reaches its terminal state. Subscription/billing management itself stays accessible during past-due, since that's exactly when someone needs to update a payment method or adjust their plan.
- **Mid-period changes: upgrade now, downgrade at renewal — access decided locally, not by Paddle's item state.** Implemented and verified live against sandbox (not the original assumption): calling Paddle's subscription-items-update endpoint with *any* proration mode — including `full_next_billing_period` — changes the subscription's `items` in Paddle's own system **immediately**; only the *billing* is deferred, never the item/access state itself. So Paddle's own item list can't be used to "hold back" a downgrade until renewal. The resolution: this app's feature access has always been driven by its own local `tier_id`/`org_subscription_addons`, completely independent of Paddle's live item list — so:
  - **Upgrade** (net price increase): call Paddle with `prorated_immediately`, charged and access-granted right away — Paddle computes the prorated charge, no custom math in OpsClear.
  - **Downgrade** (net price decrease or unchanged): call Paddle with `full_next_billing_period` (zero immediate billing impact, confirmed live), but the local `tier_id`/add-ons are **not** touched yet — the desired change is parked as `pending_tier_id`/`pending_addon_ids` (+ `paddle_pending_downgrade_effective_at`, sourced from Paddle's own `next_billed_at`) and only promoted to active once the webhook confirms the billing period has actually rolled over. A second downgrade attempt simply overwrites the pending one (Paddle raises no conflict for this); the customer can also cancel a pending downgrade outright (reverting Paddle's items back to the active set via `do_not_bill` — zero billing impact) before it takes effect.
  - **Mixed changes are blocked**, not supported: a single request that both adds something pricier and removes something cheaper is rejected (409) rather than applied — letting it through would net out to an "upgrade" that immediately drops the removed item even though it's already paid for this period. The customer saves the increase and the decrease as two separate changes instead.
- **Cancellation uses Paddle's end-of-period scheduled cancellation** (its equivalent of Stripe's `cancel_at_period_end`) — the org keeps access until the period they already paid for ends, then stops. No partial refund, no custom math. The scheduled cancellation date is also mirrored locally (`paddle_scheduled_cancellation_at`, synced from the webhook) so the UI can show the real end date and reject a second cancel attempt with a clean 409 — Paddle itself only returns a raw, unfriendly 400 for that case. An immediate/refunded cancellation could exist later as a manual super-admin override, but isn't self-service in V1.
- **No free plan — access is gated on real payment, never on the staged selection alone.** Every tier and add-on in the catalog has a real, non-zero price. The tier/add-on picker (pre-existing from PRJ-005) still lets an org compose a plan before paying — Paddle checkout needs that selection to know what to charge for — but nothing is persisted to `org_subscriptions` until Paddle actually confirms the payment via webhook, and `hasAddon()`/feature access require the org to have a real, active Paddle subscription (or `isInternal`), not just a staged selection. Without this, an org could pick any paid tier/add-ons and use them for free indefinitely by simply never completing checkout — found via live testing after initial implementation, not anticipated in the original design.
- **Build and fully validate in Paddle's sandbox environment first.** Separate sandbox API keys, a separate webhook secret, and Paddle's test card numbers (simulating success/failure/3D-Secure) — the entire integration is exercised end-to-end in sandbox before live keys are ever introduced. Explicit phase in Implementation order, not an aside.

## Technical design

### Database
- `paddle_customer_id` lives on `organisations`, not the org subscription record — `org_subscriptions.tier_id` is `NOT NULL` (every plan has a real price, per the no-free-plan decision above), so that row can only ever be created once a real payment is confirmed; the Paddle customer has to exist earlier, while the org is still picking a plan, and `organisations` is the one thing guaranteed to exist for every org regardless of billing state.
- The org subscription record carries `paddle_subscription_id` and `subscription_status` (`ACTIVE` / `PAST_DUE` / `CANCELED`), synced from Paddle webhook events — not computed locally. It's created for the first time by the webhook itself (see Backend, below), not staged locally beforehand. Skipped entirely for orgs where `isInternal` is true.
- Additional fields added during implementation, all webhook-synced: `paddle_scheduled_cancellation_at`, `paddle_current_period_starts_at` (used to detect a period rollover), `pending_tier_id`/`pending_addon_ids` + `paddle_pending_downgrade_effective_at` (deferred downgrade tracking, see Product decisions).
- No local mirror of invoice/transaction history — billing history reads live from Paddle's API instead (see below).

### API
- Webhook endpoint (`POST /api/webhooks/paddle`), verifying Paddle's webhook signature on every request before processing — the endpoint itself is unauthenticated by normal means (Paddle calls it directly); security comes entirely from signature verification.
- Endpoint(s) to initiate/update a subscription (create Paddle Customer + Subscription on first payment method entry; update Subscription items — with Paddle's default proration — when an org's tier/add-on selection changes).
- Endpoint to fetch billing history, proxying to Paddle's transaction-listing API rather than a local table.
- Endpoint to cancel (schedules the Paddle Subscription to cancel at the end of the current billing period).

### Backend
- `PaddleWebhookService`: signature verification, idempotent event handling — every write is a plain field overwrite (update, not insert, once the row exists), so redelivery of the same event is naturally idempotent by construction. Confirmed event set actually handled: `subscription.created`, `subscription.activated`, `subscription.updated`, `subscription.canceled`, `subscription.past_due`. `transaction.payment_failed` is deliberately **not** acted on — it carries no field distinguishing an individual retry from the terminal failure after retries are exhausted, so `subscription.past_due`/`subscription.canceled` are the sole source of truth for restricting access, matching "no separate OpsClear-side grace period" above.
  - **Creates the org's very first `org_subscriptions` row itself**, not just updates an existing one — resolving Paddle's own confirmed item price ids (from the webhook payload) back to the tier/add-on catalog. This is never trusted from the client; it's the direct consequence of the no-free-plan decision above.
- `PaddleSubscriptionService`: creates/updates the Paddle Customer (on `organisations`) and Subscription to mirror an org's tier + add-on selection, skipping entirely when `isInternal`. Paddle Prices, like Stripe Prices, are effectively immutable once transacted against — when a price changes via the super admin console (ADR-0043), this service creates a *new* Paddle Price and archives the old one rather than editing in place. Existing subscriptions keep referencing the old (archived but still valid) Price until explicitly migrated, matching the "grandfathered or re-evaluated per business decision" language already established for pricing changes.
- `CreditService` (from ADR-0043) gains a hook: granting a credit calls `PaddleClient.createCreditAdjustment` against the org's most recently completed transaction, using the id from `details.line_items[]` — **fixed (JOB-180)**: the code originally read `.id()` off the top-level `items[]` array, which Paddle echoes straight back from the request with no id of its own, so that field was always null; a real grant against any transaction crashed with an NPE inside `Map.of(...)`. Also fixed: the skip path (no Paddle customer, no completed transaction, or no line items) now returns a stable reason code (`CreditService.PaddleSyncSkippedReason`) surfaced on the grant response and shown as a warning banner in the super admin console's grant modal, instead of only a backend log line. **Still an unresolved, known gap**: Paddle Billing has **no** equivalent to Stripe's Customer Balance (no prospective/future-invoice discount primitive at all) — an Adjustment is the only mechanism, and it only ever refunds/credits a *past* transaction, never discounts an upcoming one as ADR-0043 originally promised. The credit unit/currency (RSD vs. the EUR Paddle actually charges in) is also still undefined. Both need a real design decision, tracked as follow-up work.
- Feature-gating layer extended to also verify `subscription_status`: full access when active, read-only when past-due, and skipped entirely for internal orgs — implemented as one more condition in the existing `RequiresAddonAspect`/`@RequiresAddon` mechanism (which already existed and already ran on every gated endpoint pre-Paddle), not a new mechanism. **Fixed (JOB-180)**: the aspect now also requires `OrgSubscriptionRepository.hasRealBilling()` (mirrors the frontend's `hasRealPaddleBilling()` — real Paddle subscription id present, status not null/CANCELED) before it even looks at the staged `org_subscription_addons` selection, closing the gap where an API call bypassing the UI could reach a staged-but-unpaid add-on.

### Frontend
- Paddle's embedded checkout widget in the subscription/billing flow for entering and updating payment details.
- Billing history section in org settings, fetching from the backend's Paddle-proxying endpoint.
- A visible "payment past due" state, distinct from a hard lockout — communicates read-only status and prompts a payment method update.
- Subscription management (tier/add-on changes, cancellation) reachable and functional even while past-due.

### Constraints & edge cases
- Webhook processing must be idempotent — Paddle, like Stripe, can redeliver the same event more than once.
- Webhook signature verification is mandatory on every request — an unverified webhook endpoint is a real attack surface (anyone could POST fake "payment succeeded" events).
- `isInternal` must be checked before any Paddle Customer/Subscription creation attempt, not just before the status gate — an internal org should never appear in Paddle at all.
- Paddle's MoR fee (materially higher than Stripe's raw processing fee — roughly 5%+ vs. Stripe's ~2.9%+30¢) is an accepted, ongoing cost of this path, not a one-time integration cost. Pricing/margin decisions made elsewhere should account for this.

## Alternatives considered

### Stripe directly

Rejected — Stripe does not support Serbia as a merchant/seller country. The registered business must be in a supported jurisdiction; the bank account location alone isn't sufficient.

### Stripe Atlas (incorporate a US entity to unlock a standard Stripe account)

Considered. Rejected for now — solves the country problem while keeping the original Stripe-based design, but creates a real ongoing US legal/compliance burden (registered agent, federal tax filing, franchise tax) for a company with no US operations, disproportionate to where this project is right now. Could be revisited later if Paddle's MoR fee becomes the bigger cost as volume grows.

### Lemon Squeezy (MoR)

Considered as the primary MoR candidate alongside Paddle. Kept as the fallback if Paddle's seller application doesn't accept Serbia, or if Paddle's onboarding proves to be a blocker in practice. Simpler onboarding and API than Paddle, but two concerns pushed Paddle ahead as the primary choice: Lemon Squeezy was acquired by Stripe in 2024, creating multi-year roadmap uncertainty (possible eventual folding into Stripe's own tooling); and its feature set is thinner for OpsClear's specific tier + multiple-add-on + proration subscription shape, which Paddle's multi-item subscription model matches more directly.

### FastSpring (MoR)

Considered. Rejected as primary — historically stronger for one-off digital goods/enterprise licensing than recurring B2B SaaS subscriptions specifically, less commonly used for this exact shape of product compared to Paddle.

### A Serbian/regional payment gateway instead of any MoR

Considered. Rejected — these are built for one-off card payments, not SaaS billing; would require hand-building the entire subscription lifecycle (renewal scheduling, dunning, invoicing, proration) that Paddle provides natively, a disproportionate engineering lift for a small team. Also weaker international checkout UX/tax handling if OpsClear ever sells outside Serbia/the Balkans.

### Custom retry/dunning logic built in OpsClear

Rejected — Paddle's own dunning tooling already solves this well; re-implementing retry scheduling risks drifting out of sync and adds real engineering surface for something already solved.

### Custom proration math for mid-cycle upgrades

Rejected — this is exactly what Paddle's default subscription-update proration behavior already computes; building equivalent math in OpsClear would be redundant and a likely source of bugs.

### Mirroring invoices/transactions into a local table for billing history

Rejected — Paddle already stores this durably; a local mirror is one more thing to keep in sync for no real benefit, and if Paddle is unreachable, payment processing is down anyway.

### Custom charge-per-period logic instead of Paddle Billing's subscription primitives

Rejected — unnecessary risk to rebuild scheduling/proration/retries on lower-level primitives when Paddle Billing exists specifically for recurring SaaS billing.

### Immediate/refunded cancellation as a self-service option in V1

Considered. Rejected for now — end-of-period cancellation covers the standard case with zero custom refund logic; immediate cancellation with a refund is a real but separate feature, left as a manual super-admin action if it's ever needed.

### Fully hosted checkout redirect instead of an embedded widget

Considered for faster integration (less UI work). Rejected — redirecting away from the app for payment doesn't match how the rest of OpsClear is built; an embedded checkout keeps the experience consistent even though it's more integration work.

## Consequences

### Positive
- Subscriptions actually collect real payments — the missing piece that makes the whole monetization system (PRJ-005, PRJ-008, ADR-0043) financially real, not just access-control theater
- Unblocks billing entirely despite Stripe's country restriction, without requiring foreign incorporation
- Paddle's MoR model absorbs global tax compliance automatically — a problem the original Stripe-only design would have had to solve separately (Stripe Tax or manual registration)
- Relying on Paddle's native retry, proration, and cancellation behavior keeps OpsClear's own code surface small and avoids re-solving problems Paddle already handles correctly
- Internal-org exemption reuses an existing, already-tested mechanism (`isInternal`) rather than introducing a parallel one
- No free-riding on the catalog: since real access requires a webhook-confirmed payment (not just a staged tier/add-on selection), the monetization model is actually enforced, not just tracked

### Negative
- Materially higher per-transaction fee than raw Stripe (~5%+ vs. ~2.9%+30¢) — an ongoing margin cost, not a one-time integration cost
- Real operational risk — a bug in webhook handling or subscription-status sync could incorrectly restrict paying customers or fail to restrict non-paying ones; needs careful testing in Paddle's sandbox before going live, more than any prior work in this project
- Webhook endpoint is a genuine new attack surface requiring signature verification discipline
- Less checkout UI customization than raw Stripe Elements would have allowed — an accepted tradeoff for MoR's tax/compliance handling
- **Credit ledger (ADR-0043) does not fully work yet against Paddle** — the NPE-on-grant bug and the silent no-op signal were fixed in JOB-180, but the core promise is still broken: Paddle has no future-invoice discount primitive at all, so credits can't "discount the org's next payment" as ADR-0043 promised — the only mechanism (Adjustments against a past transaction) is a retroactive partial-refund, not a prospective discount. Credit currency/unit (RSD vs. the EUR Paddle actually charges) is also still undefined. Tracked as follow-up work, needs a real design decision.

### Neutral
- No local invoice/transaction history table — billing history depends on Paddle's API being reachable, an accepted tradeoff for not maintaining a duplicate record store
- Price changes create new Paddle Price objects rather than editing in place — a platform constraint shared with Stripe, not a design choice
- Since there are no existing paying customers today, there's no migration/backfill plan to make — billing simply starts applying once this ships

## Implementation order
1. Paddle sandbox setup (seller account application — **confirm Serbia is accepted as seller/payout country here, first**, sandbox API keys, sandbox webhook secret) — all subsequent steps built and validated here before live keys are ever introduced
2. Paddle Customer + Subscription creation/sync (`PaddleSubscriptionService`), mirroring an org's current tier + add-on selection, skipping `isInternal` orgs entirely
3. Webhook endpoint + signature verification + idempotent event handling for the core event set
4. `subscription_status` field + feature-gating layer extended to check it (full access / read-only / skip-for-internal)
5. Price-change handling (archive-and-recreate Paddle Prices) wired to the super admin console (ADR-0043)
6. Credit ledger → Paddle credit/adjustment hook (ADR-0043 integration) — resolving the open Customer-Balance-equivalent question first
7. Frontend: embedded checkout payment flow, billing history UI, past-due state messaging, cancellation flow
8. Full end-to-end validation in sandbox (successful payment, failed payment + retries + terminal lockout, upgrade proration, cancellation) before switching to live keys

Additional work found via live testing after the above shipped, not anticipated in the original scoping: scheduled-cancellation tracking to prevent a double-cancel raw-400 (JOB-197), the mid-period upgrade-now/downgrade-at-renewal redesign above (JOB-198), switching checkout from overlay to inline (JOB-196), billing history UI + app-wide past-due banner (JOB-179), and closing the no-free-plan gap (JOB-200). Within JOB-180 itself: the `RequiresAddonAspect` real-billing gap and the credit-adjustment NPE/silent-no-op bugs are now fixed; full sandbox validation (step 8), the still-open credit-ledger design gap noted under Consequences, and confirming the deployed webhook destination remain open.

## Definition of done
- [ ] ADR document written in `docs/dev/decisions/`
- [ ] Reviewed and merged
- [ ] Future Consideration job closed
- [ ] Implementation jobs created with BLOCKED_BY relationships set

## References

- ADR-0043: Super Admin Console (`docs/dev/decisions/0043-super-admin-console.md`) — credit ledger this ADR wires into a Paddle-side credit mechanism
- JOB-078 (Future Consideration, promoted to PRJ-009/MIL-026): original scoping notes this ADR implements
- JOB-161 (PRJ-009 / MIL-026): implementation ticket this ADR is based on
- Stripe's published supported-countries list (does not include Serbia) — the fact that triggered this ADR's pivot from the original Stripe-based design
