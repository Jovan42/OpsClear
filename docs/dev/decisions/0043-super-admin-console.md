# ADR-0043: Super Admin Console (Pricing Configuration + Customer Credits)

**Status:** Proposed
**Date:** 2026-07-27
**Author:** Jovan Manojlovic

## Context

Two distinct capabilities restricted to OpsClear's own super user (not any customer-facing role, not org `OWNER`): editing subscription pricing without a code deployment, and a customer credit system fed by a structured in-app feedback form plus discretionary grants.

The subscription data model (PRJ-005) already stores tier and add-on prices in `subscription_tiers`/`subscription_addons`, but changing them today requires a code deployment. Separately, there's no structured way for customers to submit bug reports or feature suggestions, and no mechanism to reward useful ones — feedback currently has no incentive and no home in the product.

## Decision

Build a restricted admin console, gated by a `super_user` flag, covering pricing editing and a full feedback-to-credit loop: an in-app submission form, a review workflow, and an append-only per-org credit ledger.

## Product decisions

### Pricing configuration
- Table view of the current tier matrix and add-on prices, inline editable.
- Price changes take effect immediately for new subscriptions; existing subscriptions are grandfathered or re-evaluated per business decision at the time — not automated, a manual call each time it comes up.

### Customer feedback (in-app)
- Reachable from the account/user menu — a **"Feedback"** item (not just "Submit feedback") opens a hub with two views, not straight into a form: **My submissions** (this user's own submission history — type, title, status, date, whether a credit was granted) and **New submission** (the actual form).
- New-submission form: intro copy at the top explaining what the feedback program is for and that useful submissions may earn credits toward a future payment. Below that, a two-column layout — **left**: a type selector (Bug / Feature / Other, "Other" as the catch-all default) with Title and Description fields underneath; **right**: guidance text that changes based on the selected type (Bug: steps to reproduce, expected vs. actual behavior; Feature: what problem it solves and how you'd expect it to work; Other: open-ended).
- Submission just records the entry — it does not promise or compute a credit amount at submission time. Value is assessed on review.
- **No notification when a credit is granted, for V1** — deliberately silent; the customer finds out by checking their credit balance or their submission history, not via an email/in-app alert.

### Customer credits
- Super admin reviews submitted feedback and, if it's worth rewarding, grants a credit — using the **same generic grant mechanism** used for discretionary credits, just optionally linked back to the submission it came from. Not two separate systems.
- Super admin can grant credits to any org at their own discretion, with no submission required at all (goodwill, referrals, anything not worth building a dedicated flow for).
- Credits are tracked in a per-organisation, append-only ledger — never a single mutable balance column, so every grant has a clear origin and reason.
- **Credits never expire in this first iteration** — simpler to build and explain; revisit only if unbounded accumulation becomes a real problem.
- **Credit balance is visible to the customer** — shown in org settings, near the existing Subscription section (billing-adjacent), checkable anytime. Visible to Owner/Admin only, matching how subscription management is already restricted.
- The ledger and admin-side granting UI are in scope here; actually applying a credit against a real invoice depends on JOB-078/JOB-161 (Stripe) — resolved there via Stripe Customer Balance. This ADR exposes a queryable per-org credit balance for that to consume.

## Technical design

### Database
- `super_user` flag on the user record, gating this entire console.
- `feedback_submissions`: `id`, `org_id`, `submitted_by`, `type` (`BUG` / `FEATURE` / `OTHER`), `title`, `description`, `status` (`PENDING` / `REVIEWED`), `created_at`.
- `org_credits`: `id`, `org_id`, `amount`, `reason` (free text), `submission_id` (nullable FK to `feedback_submissions` — set when a grant originates from a review, null for pure discretionary grants), `granted_by`, `created_at`. Append-only.

### API
- Customer-facing: `POST /api/feedback` (submit), `GET /api/feedback/mine` (this user's submission history), `GET /api/organisations/{orgId}/credits/balance` (Owner/Admin only).
- Admin-only (gated by `super_user`): list/review submissions, grant a credit (with optional `submission_id`), edit tier/addon prices, view an org's credit ledger/balance.

### Backend
- `SuperAdminPricingService`: CRUD on tier/addon prices.
- `FeedbackService`: intake submissions, list a user's own submissions, mark reviewed.
- `CreditService`: one `grant(orgId, amount, reason, submissionId?)` method covering both submission-linked and purely discretionary grants; computes an org's current balance as the ledger sum, exposed both to the customer-facing balance endpoint and for JOB-161 to consume.

### Frontend
- `UserMenu` gains a **"Feedback"** item opening the two-view hub (My submissions / New submission) described above; type selection in the form drives the right-hand guidance text dynamically.
- Org settings: credit balance shown near the Subscription section, Owner/Admin only.
- Super admin console: restricted route (`super_user`-gated, not reachable via normal org/project nav) with a pricing table, a submissions review list, and a credit-grant action (works with or without a linked submission).

### Constraints & edge cases
- Real gaming/spam risk on submissions — review stays a manual judgment call, not automatic scoring; this is ongoing operational work that scales with submission volume.
- `super_user` is a distinct, narrowly-held flag, not conflated with org `OWNER` — it crosses org boundaries entirely.
- The unified grant mechanism must not let a `submission_id` reference a submission belonging to a different org than the one being credited.

## Alternatives considered

### Separate systems for submission-linked credits vs. discretionary credits

Considered, treating them as distinct workflows. Rejected — one `grant()` method with an optional submission link covers both without duplicating ledger/balance logic.

### Route submissions through an existing support/contact channel instead of an in-app form

Considered for simplicity. Rejected — the in-app form with type-specific guidance is a better submission experience and keeps the whole loop (submit → review → credit) inside the product rather than split across tools.

### Automatic credit scoring by type/severity

Rejected for V1 — human judgment on a young product with modest submission volume beats a premature formal rubric.

### Credits expire after N months

Rejected for the first iteration — simpler to build and explain; revisit only if unbounded accumulation becomes a real problem.

### Notify the customer when a credit is granted

Considered (email or in-app alert). Rejected for V1 — deliberately silent; the customer checks their balance/submission history instead. Revisit if silence turns out to undermine the incentive in practice.

## Consequences

### Positive
- Structured feedback loop with type-specific guidance should produce more useful submissions than an open-ended contact form
- One unified grant mechanism keeps the ledger simple regardless of where a credit originated
- Append-only ledger gives a clean, auditable interface for JOB-161 to build on
- Customer-visible balance makes the incentive real rather than a purely internal bookkeeping exercise

### Negative
- Manual review is ongoing operational work, not a one-time build
- No expiration means balances can grow unbounded; revisit if that becomes a real concern
- No notification on grant means a customer has to actively check to discover they earned something — may reduce the incentive's visibility until the balance display is well-known

### Neutral
- Credits have no billing effect until JOB-161 ships — this ADR builds the ledger, submission form, and admin UI ahead of that
- Pricing configuration and credits are bundled into one console purely for access-control convenience (both `super_user`-only), not because they're technically coupled

## Implementation order
1. `super_user` flag + console access gating
2. Pricing configuration: table view + inline editing
3. `feedback_submissions` table + `FeedbackService` + `UserMenu` "Feedback" hub (My submissions + New submission form, type-driven guidance)
4. `org_credits` ledger + unified `CreditService.grant()`
5. Customer-facing credit balance in org settings (near Subscription)
6. Admin console UI: submissions review list, credit-grant action, per-org ledger view
7. (Depends on JOB-161) Wire the computed credit balance into actual invoice discounting

## References

- JOB-081 (Future Consideration, promoted to PRJ-009/MIL-025): original scoping notes this ADR implements
- JOB-160 (PRJ-009 / MIL-025): implementation ticket this ADR is based on
- JOB-161 (PRJ-009 / MIL-026): payment gateway integration (Stripe) — will consume this ADR's credit balance via Stripe Customer Balance, once its own ADR is written
