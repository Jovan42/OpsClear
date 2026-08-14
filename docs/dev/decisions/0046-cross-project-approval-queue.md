# ADR-0046: Cross-Project Pending Approval Queue

**Status:** Proposed
**Date:** 2026-08-14
**Author:** Jovan Manojlovic

## Context

Owners and admins who manage multiple projects must currently check each project individually to find pending approvals — there's no single "what needs my attention right now" view across all projects. Deferred from an earlier phase, which scoped approvals to per-project queries only for MVP.

This ADR also formalizes a small menu/page restructuring alongside ADR-0045 (org-wide project directory): both features answer the same underlying question — "what's happening across my org that I might not otherwise see" — so rather than adding two more sections to the already-growing Organisation settings page, they share one new dedicated page.

## Decision

Add a single aggregate view of pending approvals across every project where the caller is Owner or Admin, sorted oldest-first, as the second section of the new **"Overview"** page (alongside ADR-0045's Project Directory), reachable via its own `UserMenu` entry.

## Product decisions

- A single view aggregating pending approvals across every project where the caller is Owner or Admin, sorted oldest-first (longest waiting surfaces first).
- **Not the dashboard's primary approvals data source** — Dashboard is per-project; this is explicitly cross-project and Owner/Admin-only. Kept as its own new surface rather than repurposing the existing per-project dashboard widget.
- Lives on the same aggregate `ApprovalService` that already handles per-project approvals — a new method, not a new service or folded into `DashboardService`.
- **Clicking an approval navigates to the underlying job** — matches how every other list in this app behaves.
- **Frontend-only pagination if needed, no backend pagination.** The backend keeps returning the full sorted list from one query; the frontend slices it for display if the list is long. Cheap to add, avoids backend pagination-parameter complexity for a list that's unlikely to be long given typical org sizes here.

### Menu/page restructuring (shared with ADR-0045)

- New `UserMenu` entry, **"Overview"**, opening a page with two sections: **Project Directory** (ADR-0045) and **Pending Approvals** (this ADR).
- `UserMenu` items are now visually grouped: `Organisation`, `Feedback`, `Overview` together (org-facing), separated by a divider from `Account settings` (renamed from `Settings`, personal/account-facing) and `Sign out` — mirroring the divider already used to set `Sign out` apart.
- This keeps the menu itself from accumulating unrelated entries indefinitely, and gives org-wide oversight features room to grow on their own page instead of competing for space inside Organisation settings.

## Technical design

### Database
None — reads existing `approvals`/`jobs`/`projects`/`project_members` data.

### API
- `GET /api/approvals/pending` — returns all pending approvals across every project where the caller is Owner or Admin, sorted oldest-first.

### Backend
- `ApprovalService` gains `findPendingAcrossOrgs(callerId)` — a single JOIN (`approvals → jobs → projects → project_members`) rather than N per-project calls, per the performance concern already raised in the original FC.

### Frontend
- `UserMenu`: rename `Settings` → `Account settings`; add `Overview` entry; group items with a divider (`Organisation` / `Feedback` / `Overview` above, `Account settings` below, `Sign out` last).
- New `Overview` page/route with two sections: `Project Directory` (ADR-0045) and `Pending Approvals` (this ADR) — the approvals section lists each pending approval, oldest first, each row linking to its job.
- Frontend-side slicing/pagination if the fetched list is long (no backend pagination).

### Constraints & edge cases
- Must filter correctly to only projects where the caller holds Owner/Admin role — same membership-role check already used for per-project approval actions.
- Single JOIN query must perform acceptably as project/job counts grow — worth an index check on the join path if it doesn't already exist.
- The `Overview` page must degrade sensibly if the caller has zero cross-project approvals and zero directory entries beyond their own projects — an empty-state per section (per ADR-0041's shared `EmptyState` component), not a broken/blank page.

## Alternatives considered

### N per-project API calls instead of one aggregate query

Rejected — the original FC already flagged this as a performance concern; a single JOIN scales better and is simpler for the frontend to consume as one request.

### Make this the dashboard's primary approvals widget

Rejected — conflates a per-project view (Dashboard) with an explicitly cross-project, role-restricted one; kept separate to avoid confusing what each surface actually shows.

### Fold into DashboardService

Rejected — `ApprovalService` already owns approval-domain logic; adding a cross-project query there is a natural extension, not a reason to create or borrow a different service.

### Two separate menu entries/pages instead of one shared "Overview" page

Considered — a direct "Approvals" menu item is arguably more discoverable in the moment than a section within a combined page. Rejected in favor of one shared page: both features are individually small (a table each), thematically the same underlying question, and adding two new top-level menu entries works against the actual motivation for this restructuring (keeping the menu from growing unbounded as more org-wide oversight features are added later).

### Backend-side pagination

Considered for the eventual case of a very active org with many pending approvals. Rejected for V1 — frontend-side slicing of the single-query result is simpler and sufficient given typical org sizes (5–50 people) in this product's target market; revisit only if it proves genuinely necessary.

## Consequences

### Positive
- Owners/Admins get a real "what needs my attention" view instead of checking every project individually
- Single JOIN avoids the N-calls performance problem the original FC anticipated
- Shared "Overview" page keeps the menu lean while giving both oversight features real page space, rather than an ever-growing Organisation settings page or an ever-growing flat menu

### Negative
- Another Owner/Admin-only surface, though small (one read endpoint + one page section)
- `UserMenu` restructuring is itself a small user-facing change (renamed item, new grouping) riding along with a backend feature — worth calling out explicitly in the PR so it isn't mistaken for scope creep

### Neutral
- Existing per-project approval queue/dashboard widget is unchanged — this is additive, not a replacement
- ADR-0045 is amended (not superseded) to reflect this same page structure, since its own implementation hadn't started yet

## Implementation order
1. Backend: `ApprovalService.findPendingAcrossOrgs`, single-JOIN query, `GET /api/approvals/pending`
2. Frontend: `UserMenu` restructuring (rename, grouping, new `Overview` entry) + `Overview` page shell with both sections (Project Directory from ADR-0045, Pending Approvals from this ADR)

## References

- ADR-0045: Org-Wide Project Directory for Owner/Admin (`docs/dev/decisions/0045-org-wide-project-directory.md`) — amended alongside this ADR to share the same "Overview" page
- ADR-0041: Polished Empty States Across the App (`docs/dev/decisions/0041-polished-empty-states.md`) — shared `EmptyState` component reused for this page's empty cases
- JOB-009 (Future Consideration, promoted to PRJ-010/MIL-028): original scoping notes this ADR implements
