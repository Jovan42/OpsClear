# ADR-0030: Subscription data model and management

**Status:** Proposed
**Date:** 2026-05-07
**Author:** Jovan Manojlovic

## Context

The landing page pricing calculator (ADR-0029) hardcodes tier and add-on prices in the frontend. Any pricing change requires a code deploy. More importantly, the system has no way to record what a given organisation has actually subscribed to — there is no subscription state, no tier assignment, and no add-on selection stored anywhere.

This milestone introduces the subscription data model. It covers:

- DB tables that store tier definitions, add-on definitions, and per-org subscription records
- Two API endpoints to read and update an org's subscription
- A frontend UI for the org owner to configure their subscription
- Downgrade validation before applying a tier change

Payment processing is explicitly out of scope. Subscriptions are recorded in the DB but no payment gateway is integrated at this stage.

## Decision

### Data model

Four new tables:

**`subscription_tiers`** — master list of available plans, seeded via Flyway:

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `name` | VARCHAR | e.g. `STARTER`, `GROWTH`, `SCALE` |
| `max_members` | INT | member cap for this tier |
| `max_projects` | INT | project cap; `NULL` = unlimited |
| `price_monthly` | INT | price in RSD (monthly billing) |
| `price_annual` | INT | price in RSD (annual billing, per month) |
| `currency` | VARCHAR(3) | ISO 4217 code, e.g. `RSD` |
| `display_order` | INT | sort order for UI display |

**`subscription_addons`** — master list of available add-ons, seeded via Flyway:

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `key` | VARCHAR UNIQUE | e.g. `DASHBOARD`, `APPROVALS`, `NOTES` |
| `name` | VARCHAR | display name |
| `price_monthly` | INT | price in RSD |
| `price_annual` | INT | price in RSD (annual, per month) |
| `display_order` | INT | sort order |

**`org_subscriptions`** — one row per organisation:

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `org_id` | UUID FK UNIQUE | one subscription per org |
| `tier_id` | UUID FK | references `subscription_tiers` |
| `billing_cycle` | VARCHAR | `MONTHLY` or `ANNUAL` |
| `is_internal` | BOOLEAN | if `true`, all tier limits and enforcement are bypassed; set via SQL only, no API surface |
| `created_at` | TIMESTAMP | |
| `updated_at` | TIMESTAMP | |

**`org_subscription_addons`** — join table of active add-ons per org:

| Column | Type | Notes |
|---|---|---|
| `org_subscription_id` | UUID FK | references `org_subscriptions` |
| `addon_id` | UUID FK | references `subscription_addons` |
| PK | composite `(org_subscription_id, addon_id)` | |

Pricing lives in the DB so values can be updated via a Flyway migration without touching application code.

### API

Two endpoints, both scoped to the authenticated org:

```
GET  /api/organisations/{orgId}/subscription
PUT  /api/organisations/{orgId}/subscription
```

`GET` — returns the current subscription (tier, billing cycle, active add-ons, computed monthly total). If the org has no subscription record yet, returns `404`.

`PUT` — owner-only. Accepts the desired tier ID, billing cycle, and list of add-on IDs. Runs downgrade validation before persisting. Returns the updated subscription.

Request body for `PUT`:
```json
{
  "tierId": "<uuid>",
  "billingCycle": "MONTHLY | ANNUAL",
  "addonIds": ["<uuid>", ...]
}
```

### Downgrade validation

Before applying a `PUT`, the service checks:

- Active member count ≤ new tier's `max_members`
- Active project count ≤ new tier's `max_projects` (skip if `NULL`)

If either check fails, return `422 Unprocessable Entity` with a descriptive error message. The owner must remove members or archive projects before downgrading.

Orgs with `is_internal = true` skip all validation. `is_internal` can only be set directly in the DB — there is no API to set it. This flag is used for the owner's own org and demo accounts to allow unrestricted testing without affecting business logic.

### New org behaviour

New organisations start with no subscription record (`404` from the GET endpoint). The subscription setup wall (which gates access to the app until a subscription is configured) is implemented separately in MIL-013. This milestone only provides the data model and API — no enforcement logic ships here.

### Frontend

A new **Subscription** section is added to `OrgSettingsPage.tsx`. It replaces the hardcoded pricing calculator from the landing page with live data fetched from the API.

The section contains:

- Current tier display with member/project limits
- Billing cycle toggle (MONTHLY / ANNUAL) with live price update
- Add-on toggle grid — same 2-column card layout as the landing page calculator, but driven by data from `GET /api/organisations/{orgId}/subscription/addons` (or included in the GET subscription response)
- Running monthly total
- **Save** button — calls `PUT`, shows inline success or validation error

The landing page calculator remains hardcoded for now. The subscription settings UI is the only place that reads/writes live subscription data.

## Alternatives Considered

### Alternative 1: Hardcode tiers and prices in application config (YAML)

Store tier definitions in `application.yml` rather than DB tables.

**Pros:**
- No DB migration needed for price changes (just redeploy with updated config)
- Simpler — no seed data to maintain

**Cons:**
- Prices and limits cannot be queried or joined against at the DB level
- Downgrade validation requires in-memory comparisons against config values
- Config bloat as the add-on list grows

**Why rejected:** DB tables give a single source of truth that both the API and future reporting can query. Price changes via Flyway migration are an acceptable trade-off.

### Alternative 2: Store subscription as a JSON column on `organisations`

Embed the entire subscription (tier, billing cycle, add-ons) as a JSONB column on the existing `organisations` table.

**Pros:**
- No new tables; schema stays flat
- Flexible — easy to add fields without migrations

**Cons:**
- Cannot join or filter on subscription fields at the DB level
- Downgrade validation must deserialise JSON before comparing
- Harder to evolve (renaming a key in JSON is a data migration with no type safety)

**Why rejected:** Normalised tables are the correct approach for data that needs to be queried, validated, and eventually reported on.

### Alternative 3: Include payment integration in this milestone

Integrate Stripe or a similar gateway alongside the data model.

**Why rejected:** Payment integration is a large, distinct concern. The data model can be validated and iterated on independently. Payment processing will be introduced in a later milestone.

## Consequences

### Positive

- Pricing can be updated via a DB migration without a code deploy
- The system now knows what each org has subscribed to, enabling future enforcement and reporting
- Downgrade validation prevents orgs from ending up in an invalid state (more members than their tier allows)

### Negative

- Four new tables and a Flyway seed migration add schema complexity
- The landing page calculator and the org settings subscription UI are temporarily out of sync (one hardcoded, one live) until the landing page is updated in a future milestone

### Neutral

- No payment processing — subscriptions are recorded but not charged
- The subscription setup wall (MIL-013) depends on this milestone; it cannot ship without the `org_subscriptions` table existing

## Implementation Notes

1. Write Flyway migration: create four tables, seed `subscription_tiers` and `subscription_addons` with initial values from the monetization spec
2. Add JPA entities and repositories for all four tables
3. Implement `SubscriptionService`: `getSubscription`, `upsertSubscription` (with downgrade validation)
4. Add `SubscriptionController` with `GET` and `PUT` endpoints; `PUT` restricted to `OWNER` role
5. Add a Subscription section to `OrgSettingsPage.tsx`: fetch on mount, render tier + add-on cards, wire Save button to `PUT`
6. Integration tests: GET returns 404 for new org; PUT persists correctly; PUT rejects downgrade when limits exceeded

## References

- ADR-0026: Organisation layer (org structure this subscription attaches to)
- ADR-0029: Public landing page (hardcoded pricing calculator this milestone will eventually replace)
