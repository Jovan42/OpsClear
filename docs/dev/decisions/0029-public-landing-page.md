# ADR-0029: Public landing page

**Status:** Proposed
**Date:** 2026-04-24
**Author:** Jovan Manojlovic

## Context

OpsClear currently redirects all unauthenticated visitors directly to Keycloak. There is no marketing presence — no page explaining what the product is, who it is for, or what it costs.

This milestone adds a public-facing landing page at the root URL so that new visitors can understand the product and self-serve into registration without first encountering a raw auth flow.

## Decision

Add a public `/` route to the existing React app. No backend changes are required.

### Routing logic

| Visitor state | Destination |
|---|---|
| Unauthenticated | Landing page (`/`) |
| Authenticated, no subscription | Subscription setup wall (implemented in the next milestone) |
| Authenticated, has subscription | `/dashboard` |

The `/` route must be excluded from the Keycloak redirect interceptor in the React auth setup. The authenticated redirect must handle both subscription states so routing is not broken when the subscription wall ships.

### Page structure

#### 1. Hero

- Tagline + one-line explanation of who OpsClear is for
- Primary CTA button → Keycloak registration

#### 2. Problem statement

- The "status call" problem in 2–3 sentences
- Target audience: SME owners and ops managers

#### 3. Core features grid

- Cards: Job tracking, Blockage visibility, Approvals, Notes, Dashboard
- Each card has a "See how it works →" trigger that opens a tabbed modal
- Modal tabs: one per feature, each showing a screenshot + short description
- Screenshots must be captured from the live app before this section is implemented

#### 4. Add-ons overview

- Same add-on cards as the pricing calculator (see section 5)
- Each card has a "See how it works →" trigger → modal with screenshot + description
- "Job templates" and "Recurring scheduling" shown as greyed-out "Coming soon" (non-interactive)
- Milestones card includes "Need more? Contact us" → `mailto:jovan0042@gmail.com`

#### 5. Pricing calculator

- **Base plan sliders:** Team members (1–50), Active projects (1–25)
- Tier matrix drives the base price (values from the monetization spec)
- **Add-ons section:** checkboxes with name, tagline, and price per month
- **Annual billing toggle:**
  - Label changes to "Monthly total (annual)"
  - Displayed price = monthly total × (10/12), rounded to nearest whole RSD
  - "Save X RSD/yr" badge = monthly total × 2
- Running total updates live as sliders and checkboxes change
- Pricing data is hardcoded in the frontend for this milestone; the subscription data model is introduced in the next milestone

#### 6. CTA footer

- "Ready to get clarity? Start now" → Keycloak registration

## Alternatives Considered

### Alternative 1: Separate static site

A standalone static marketing site (e.g. plain HTML/CSS or a separate Vite app) deployed independently.

**Pros:**
- Completely decoupled from the app build
- Can use a CDN without affecting the app server

**Cons:**
- Second deployment to maintain at this stage of the product
- Duplicates Keycloak registration links and pricing data across two repos
- No shared component library with the app

**Why rejected:** Unnecessary operational overhead for a product at this scale. A single deployment is simpler and the shared React component library is a meaningful advantage for the pricing calculator.

### Alternative 2: Dedicated page per add-on

Individual detail pages for each add-on instead of modals triggered from the pricing calculator.

**Pros:**
- Each add-on gets its own URL (shareable, indexable)

**Cons:**
- Breaks pricing context — the user must navigate away from the calculator to understand what they are paying for
- More routes to maintain

**Why rejected:** The modal keeps the user in the calculator flow, which is the primary conversion surface. Shareable add-on pages are not a current requirement.

## Consequences

### Positive

- New visitors can understand the product and register without encountering Keycloak directly
- The pricing calculator gives prospects a concrete monthly cost before they sign up
- No backend work required — ships as a pure frontend change

### Negative

- Screenshots of the live app must be captured and committed before the feature cards section can be built; this is a manual prerequisite
- Pricing data is hardcoded — any pricing change requires a frontend deploy until the subscription data model is introduced

### Neutral

- The Keycloak redirect interceptor in the React auth setup must be updated to exclude `/`
- Authenticated redirect logic must account for two states (no subscription vs. has subscription) even though the subscription wall itself ships in the next milestone

## Implementation Notes

1. Update React Router config: add public `/` route, exclude from auth guard
2. Update authenticated redirect: check subscription state → setup wall or `/dashboard`
3. Implement page sections top-to-bottom: Hero → Problem → Features → Add-ons → Pricing → CTA
4. Capture screenshots from the live app before implementing the features grid and add-ons sections
5. Pricing calculator: hardcode tier matrix and add-on prices from the monetization spec; annual toggle calculated client-side

## References

- ADR-0011: Frontend architecture
- ADR-0012: Auth UI approach (Keycloak redirect interceptor)
- ADR-0026: Organisation layer (subscription model — referenced for routing logic)
