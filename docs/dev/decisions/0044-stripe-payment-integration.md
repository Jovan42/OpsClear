# ADR-0044: Payment Gateway Integration (Stripe)

**Status:** Proposed
**Date:** 2026-07-27
**Author:** Jovan Manojlovic

## Context

The subscription data model and feature gating (PRJ-005) are complete — orgs already select a tier + add-ons, and `@RequiresAddon`/`hasAddon()` enforce access based on that selection. What's missing is a real payment processor: today, subscription state is tracked but no money actually changes hands. This ADR wires up Stripe so subscriptions collect real payments, and gives JOB-160/ADR-0043's credit ledger a mechanism to actually discount an invoice.

This is the highest-stakes piece of work across this whole project so far — real money, webhook-driven async state, and a direct dependency for feature access (a failed payment eventually restricts access). Treated more carefully and deliberately than the polish-phase work that preceded it. There are currently no real paying customers — the app is self-served by its own team — which removes any existing-customer migration pressure and gives room to build and validate this fully in Stripe's test mode before it matters for real.

## Decision

Integrate Stripe Billing (Subscriptions + Prices + Invoices), relying on Stripe's own native mechanisms — retries, proration, cancellation — rather than building custom logic for problems Stripe already solves correctly.

## Product decisions

- **Stripe Billing**, not a custom charge-per-period system on raw Charges/PaymentIntents — proration, retries, and invoicing all come from Stripe for free.
- **Card data never touches OpsClear's backend** — Stripe Elements, embedded directly in the app's own subscription/billing UI (not Stripe Checkout's hosted redirect) to match the rest of the app's design. The backend only ever sees a payment method token/ID.
- **Internal orgs are never billed.** `OrgSubscriptionModel.isInternal` already exists and `RequiresAddonAspect` already skips add-on checks for internal orgs. This ADR extends the same flag: no Stripe Customer/Subscription is ever created for an internal org, and the new `subscription_status` check (below) is skipped for them the same way the add-on check already is — no new mechanism, just reusing what's there.
- **No separate OpsClear-side grace period.** Stripe's own Smart Retries already spreads failed-payment retries over roughly 2–4 weeks before giving up. The entire span Stripe is still retrying is treated as the grace period — a visible "payment past due" state, but no lockout — and access only actually restricts once Stripe reports the terminal outcome (subscription becomes `unpaid`/`canceled` after retries are exhausted). No second, duplicate timer maintained in OpsClear.
- **Past-due orgs get read-only access**, not a full lockout — they can still view their data (avoids "did I lose my work" panic) but can't create/edit until payment recovers or the subscription reaches its terminal state. Subscription/billing management itself stays accessible during past-due, since that's exactly when someone needs to update a payment method or adjust their plan.
- **Proration uses Stripe's default subscription-update behavior**, not custom math. When an org's tier/add-on selection changes mid-cycle, updating the Stripe Subscription's items with `proration_behavior: create_prorations` (Stripe's default) automatically charges a prorated amount for the remainder of the current period and bills the full new amount starting next cycle. No proration logic is built in OpsClear itself.
- **Cancellation uses `cancel_at_period_end`**, Stripe's native option — the org keeps access until the period they already paid for ends, then stops. No partial refund, no custom math. An immediate/refunded cancellation could exist later as a manual super-admin override, but isn't self-service in V1.
- **Build and fully validate in Stripe test mode first.** Separate test API keys, a separate webhook secret, and Stripe's test card numbers (simulating success/failure/3D-Secure) — the entire integration is exercised end-to-end in test mode before live keys are ever introduced. Explicit phase in Implementation order, not an aside.

## Technical design

### Database
- Extend the existing org subscription record with `stripe_customer_id`, `stripe_subscription_id`, and `subscription_status` (`active` / `past_due` / `canceled`), synced from Stripe webhook events — not computed locally. Skipped entirely for orgs where `isInternal` is true.
- No local mirror of invoice/payment history — billing history reads live from Stripe's API instead (see below).

### API
- Webhook endpoint (`POST /api/webhooks/stripe`), verifying Stripe's signature on every request before processing — the endpoint itself is unauthenticated by normal means (Stripe calls it directly); security comes entirely from signature verification.
- Endpoint(s) to initiate/update a subscription (create Stripe Customer + Subscription on first payment method entry; update Subscription items — with Stripe's default proration — when an org's tier/add-on selection changes).
- Endpoint to fetch billing history, proxying to Stripe's list-invoices API rather than a local table.
- Endpoint to cancel (sets `cancel_at_period_end: true` on the Stripe Subscription).

### Backend
- `StripeWebhookService`: signature verification, idempotent event handling (Stripe can redeliver the same event — processing must be safe to run twice). Handles at minimum: `invoice.payment_succeeded`, `invoice.payment_failed` (only the terminal failure after Stripe's retries are exhausted, not every individual retry attempt), `customer.subscription.deleted`, `customer.subscription.updated`.
- `StripeSubscriptionService`: creates/updates the Stripe Customer and Subscription to mirror an org's tier + add-on selection, skipping entirely when `isInternal`. **Stripe Prices are immutable once created** — when a price changes via the super admin console (ADR-0043), this service creates a *new* Stripe Price and archives the old one rather than editing in place. Existing subscriptions keep referencing the old (archived but still valid) Price until explicitly migrated, matching the "grandfathered or re-evaluated per business decision" language already established for pricing changes.
- `CreditService` (from ADR-0043) gains a hook: granting a credit also posts a balance transaction to the org's Stripe Customer, so it's automatically applied against the next invoice.
- Feature-gating layer extended to also verify `subscription_status`: full access when `active`, read-only when `past_due`, and skipped entirely for internal orgs — same shape as the existing `hasAddon()`/`isInternal` check in `RequiresAddonAspect`.

### Frontend
- Stripe Elements embedded in the subscription/billing flow for entering and updating payment details.
- Billing history section in org settings, fetching from the backend's Stripe-proxying endpoint.
- A visible "payment past due" state, distinct from a hard lockout — communicates read-only status and prompts a payment method update.
- Subscription management (tier/add-on changes, cancellation) reachable and functional even while past-due.

### Constraints & edge cases
- Webhook processing must be idempotent — Stripe explicitly documents that the same event can be delivered more than once.
- Webhook signature verification is mandatory on every request — an unverified webhook endpoint is a real attack surface (anyone could POST fake "payment succeeded" events).
- `isInternal` must be checked before any Stripe Customer/Subscription creation attempt, not just before the status gate — an internal org should never appear in Stripe at all.

## Alternatives considered

### Custom retry/dunning logic built in OpsClear

Rejected — Stripe's Smart Retries already solves this well; re-implementing retry scheduling risks drifting out of sync and adds real engineering surface for something already solved.

### Custom proration math for mid-cycle upgrades

Rejected — this is exactly what Stripe's default subscription-update proration behavior already computes; building equivalent math in OpsClear would be redundant and a likely source of bugs (edge cases around partial days, timezone handling, etc. that Stripe has already solved).

### Mirroring invoices into a local table for billing history

Rejected — Stripe already stores this durably; a local mirror is one more thing to keep in sync for no real benefit, and if Stripe is unreachable, payment processing is down anyway.

### Custom charge-per-period logic on raw Charges/PaymentIntents instead of Stripe Billing

Rejected — Stripe Billing exists specifically for recurring SaaS billing and handles proration, scheduling, and retries correctly out of the box; unnecessary risk to rebuild on lower-level primitives.

### Immediate/refunded cancellation as a self-service option in V1

Considered. Rejected for now — `cancel_at_period_end` covers the standard case with zero custom refund logic; immediate cancellation with a refund is a real but separate feature, left as a manual super-admin action if it's ever needed.

### Stripe Checkout instead of Stripe Elements

Considered for faster integration (fully hosted, less UI work). Rejected — redirecting away from the app for payment doesn't match how the rest of OpsClear is built; Elements keeps the experience consistent even though it's more integration work.

## Consequences

### Positive
- Subscriptions actually collect real payments — the missing piece that makes the whole monetization system (PRJ-005, PRJ-008, ADR-0043) financially real, not just access-control theater
- Relying on Stripe's native retry, proration, and cancellation behavior keeps OpsClear's own code surface small and avoids re-solving problems Stripe already handles correctly
- Internal-org exemption reuses an existing, already-tested mechanism (`isInternal`) rather than introducing a parallel one
- Credit ledger (ADR-0043) gets a concrete mechanism (Customer Balance) to actually take effect

### Negative
- Real operational risk — a bug in webhook handling or subscription-status sync could incorrectly restrict paying customers or fail to restrict non-paying ones; needs careful testing in Stripe test mode before going live, more than any prior work in this project
- Webhook endpoint is a genuine new attack surface requiring signature verification discipline

### Neutral
- No local invoice history table — billing history depends on Stripe's API being reachable, an accepted tradeoff for not maintaining a duplicate record store
- Price changes create new Stripe Price objects rather than editing in place — a Stripe platform constraint, not a design choice
- Since there are no existing paying customers today, there's no migration/backfill plan to make — billing simply starts applying once this ships

## Implementation order
1. Stripe test-mode setup (test API keys, test webhook secret) — all subsequent steps built and validated here before live keys are ever introduced
2. Stripe Customer + Subscription creation/sync (`StripeSubscriptionService`), mirroring an org's current tier + add-on selection, skipping `isInternal` orgs entirely
3. Webhook endpoint + signature verification + idempotent event handling for the core event set
4. `subscription_status` field + feature-gating layer extended to check it (full access / read-only / skip-for-internal)
5. Price-change handling (archive-and-recreate Stripe Prices) wired to the super admin console (ADR-0043)
6. Credit ledger → Stripe Customer Balance hook (ADR-0043 integration)
7. Frontend: Stripe Elements payment flow, billing history UI, past-due state messaging, cancellation flow
8. Full end-to-end validation in test mode (successful payment, failed payment + retries + terminal lockout, upgrade proration, cancellation) before switching to live keys

## Definition of done
- [ ] ADR document written in `docs/dev/decisions/`
- [ ] Reviewed and merged
- [ ] Future Consideration job closed
- [ ] Implementation jobs created with BLOCKED_BY relationships set

## References

- ADR-0043: Super Admin Console (`docs/dev/decisions/0043-super-admin-console.md`) — credit ledger this ADR wires into Stripe Customer Balance
- JOB-078 (Future Consideration, promoted to PRJ-009/MIL-026): original scoping notes this ADR implements
- JOB-161 (PRJ-009 / MIL-026): implementation ticket this ADR is based on
