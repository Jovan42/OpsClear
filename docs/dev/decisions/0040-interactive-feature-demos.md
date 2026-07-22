# ADR-0040: Interactive Live-Component Demos on /features

**Status:** Proposed
**Date:** 2026-07-22
**Author:** Jovan Manojlovic

## Context

The `/features` marketing page currently shows static screenshots (or a "No preview yet" placeholder) next to each feature/add-on description. Screenshots go stale the moment the UI changes — one of the findings behind JOB-131 (`/features` and pricing content already drifting out of sync with what actually shipped: "Job templates" mislabeled "Coming soon" despite being purchasable, "API keys" missing from the list entirely).

This ADR replaces selected static previews with small, interactive live components: the real production UI, rendered zoomed out, that expands into a full-screen overlay a visitor can click around in — using real mock data, not a screenshot.

This is also meant to be the *only* place "example data" lives in the product. Rather than seeding new paid accounts with fake example data after signup (considered and rejected — see JOB-139/MIL-022, "Polished empty states," real accounts start genuinely empty), a prospect gets to explore a fully populated, realistic version of the product *before* paying, to figure out which specific add-ons actually matter to their workflow. Once someone signs up and pays, their account starts empty and stays that way.

## Decision

Render real production components (not hand-built lookalikes) inside a scaled-down preview box on every `/features` row. Clicking expands the preview into a full-screen overlay. All data is served by Mock Service Worker (MSW) intercepting the app's existing `fetch` calls — no request ever reaches a real backend, and no changes are needed to the real components, hooks, or API client themselves.

## Product decisions

- **Per-row demos, not one big screen.** Each demo-able `/features` row gets its own small, self-contained interactive component, matching the page's existing per-row layout — not one large combined demo (e.g. a full Job Detail page) covering several add-ons at once.
- **Full scope: every current `/features` card, plus a new Links card.** As of `FeaturesPage.tsx`, the page has 11 cards: `job-tracking`, `blockage` (base/Included), `dashboard`, `approvals`, `notes`, `history`, `milestones`, `relationships`, `templates`, `recurring`, `api-keys` (all add-ons). This ADR's target is all 11 getting the interactive treatment, plus a 12th new card for Links (JOB_LINKS) once that feature ships — not a narrow subset. The base "Included" cards (`job-tracking`, `blockage`) are in scope too, not excluded, since the goal is a consistently interactive page, not just a sales tool for paid add-ons.
- **Implementation lands incrementally, but nothing is deliberately left static.** Building all 12 at once isn't realistic for one PR — see Implementation order below for suggested sequencing — but every card is a target for this ADR, unlike the earlier draft of this decision which held some cards back indefinitely.
- **Persistent demo-mode badge.** "Demo — sample data, nothing here is saved" stays visible for the entire time the overlay is open, not a one-time tooltip — a mocked success toast (e.g. clicking Approve) looks identical to a real one, so the label needs to stay visible throughout, not just at open.
- **Per-click reset.** The demo resets to the same baseline state every time the overlay is opened. No session/localStorage persistence, no "used up" demo state to reason about.
- **One shared mock dataset.** A fictional small business with a few realistic projects/jobs already in varied states (blocked, in progress, completed, with notes/links/relationships already populated), reused across every row that gets the interactive treatment — not bespoke, disconnected data per row.
- **Click-through only for V1.** Reachable by clicking a preview on `/features`; no shareable/deep-linkable demo URL yet. A clean V2 if the mechanism proves valuable, not worth the routing/out-of-context-landing complexity now.

## Technical design

### Database
None.

### API
None. MSW intercepts `fetch` calls before they leave the browser — no request reaches any real endpoint, nothing is persisted anywhere real.

### Backend
None.

### Frontend
- MSW configured for "demo mode," intercepting the same `fetch` calls the existing hooks/API client already make. No changes needed to any of the real components involved, their hooks, or the API client — this is the reason MSW was chosen over a custom injected mock API client, which would have required refactoring those components to accept a swappable data layer and undercut the entire point of reusing the real UI.
- Each demo-able row renders its real component inside a `transform: scale()` preview container on `/features`.
- Clicking expands the container into a fixed-position full-screen overlay via a CSS transition (or the View Transitions API where supported, with a manual transform/opacity fallback).
- Persistent demo-mode badge rendered inside the overlay for its full duration.
- Demo bundle code-split (dynamic import) so it only loads when a visitor actually opens a preview — keeps the public marketing bundle small for visitors who never interact with it.
- Mutations (approve/reject, viewing milestone progress, etc.) resolve against MSW-mocked in-memory state only — visually indistinguishable from real network responses to the app code, but nothing leaves the browser and nothing persists.

### Constraints & edge cases
- Components must not assume a logged-in user/auth context — must run standalone on the public, unauthenticated marketing site.
- Mock dataset needs enough breadth to make all 12 cards look real without mocking the entire backend surface — in practice this is manageable because most cards read from the same underlying entities (a handful of mock jobs, already populated with notes, links, relationships, status history, and a couple of milestones, covers `job-tracking`, `blockage`, `notes`, `history`, `relationships`, `milestones`, and `links` at once). `api-keys` and `templates`/`recurring` need their own smaller, self-contained mock slices since they're more standalone management screens.
- Demo state must fully reset every time the overlay is opened — no bleed-through between opens or between different visitors in the same browser session.
- The demo-mode badge must stay visible/legible for the entire time the overlay is open, not just at the moment it opens.

## Alternatives considered

### Hand-built fake components mimicking the real UI

Rejected — would drift out of sync with the real product over time, exactly the staleness problem static screenshots already have (see JOB-131). The whole point of this ADR is a demo that structurally can't drift, because it's the real UI, not a copy of it.

### Custom injected mock API client (no `fetch()` call constructed at all)

Considered, to make "no real request" even more literally true than MSW's browser-level interception. Rejected in favor of MSW: building a swappable data layer means refactoring the real hooks/components to accept it, which undercuts the entire reason for reusing real components — zero changes needed to them. MSW already satisfies the actual safety requirement (no request reaches a real server, nothing is written anywhere real) without that cost.

### One big single-screen demo (e.g. a full Job Detail page)

Considered initially. Rejected in favor of small per-row demos matching the existing `/features` layout — lets each add-on be evaluated in isolation rather than bundling several into one large, harder-to-parse demo, and requires less surrounding mock data to feel coherent per screen.

### Shareable/deep-linkable demo URL for V1

Deferred to a possible V2. Adds routing and out-of-context-landing complexity (what does a visitor see if they land directly on a demo without the surrounding `/features` copy explaining what they're looking at?) not worth solving before the core mechanism is proven valuable.

## Consequences

### Positive

- A demo that structurally can't go stale the way screenshots do — it's the real production UI, just fed mock data
- Zero changes required to any of the real components, hooks, or API client involved — MSW interception means every card reuses production code unmodified
- Lets prospects self-select which add-ons matter to them before paying, rather than guessing from a description
- Demo bundle is code-split, so visitors who never open a preview pay no bundle-size cost

### Negative

- MSW is a new frontend dependency, scoped to demo mode
- Meaningfully more engineering than a screenshot — mock dataset design, overlay mechanics, code-splitting, and now across 12 cards rather than a handful — appropriately reflected in being its own ADR rather than folded into general polish work
- Full 12-card scope means this ADR's implementation is likely to span more than one PR/job, even though the mechanism itself (overlay/scale/badge) is built once and reused

### Neutral

- Click-through only for V1 — no shareable URL, revisit as a V2 if valuable
- All 12 cards (11 current + Links) are in scope for this ADR, even though implementation lands incrementally — see Implementation order

## Implementation order

1. MSW setup for demo mode + the shared mock dataset (one fictional org/projects/jobs, varied states covering job tracking, blockage, notes, history, relationships, milestones, and links in one coherent dataset)
2. Overlay/scale/badge mechanism built once, against the first card (Approvals — clearest self-contained interaction loop), then reused as-is by every subsequent card
3. Remaining cards using the shared dataset: `milestones`, `job-tracking`, `blockage`, `notes`, `history`, `relationships` — each mostly reuses the same mock data, adding little marginal cost per card
4. Cards needing their own smaller mock slice: `dashboard`, `templates`, `recurring`, `api-keys`
5. `links` card added once JOB_LINKS ships as a real, purchasable add-on
6. Manual verification across all cards: no request reaches a real backend, badge stays visible throughout, state resets correctly on every open

## References

- JOB-131 (Maintenance / Tech Debt): pricing/features/demo data out of sync with shipped addons — the staleness problem this ADR structurally avoids
- JOB-138 (Future Consideration, promoted to PRJ-008/MIL-023): original scoping notes this ADR implements
- JOB-139 (PRJ-008/MIL-022): "Polished empty states across the app" — the companion piece for what a real (non-demo) account looks like once someone actually pays
