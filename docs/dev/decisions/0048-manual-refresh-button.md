# ADR-0048: Manual Refresh Button

**Status:** Proposed
**Date:** 2026-08-14
**Author:** Jovan Manojlovic

## Context

TanStack Query already refetches on window focus, covering the common case of tabbing away and back. It doesn't cover a user staring at an open tab the whole time while a teammate edits the same job or dashboard elsewhere — the screen goes silently stale with no signal and no way to force a refresh short of a full page reload.

## Decision

Add an explicit, user-initiated refresh control with a "Last updated Xm ago" label to Job Detail, Dashboard, Job List, and the per-project Approvals queue, rather than automatic polling.

> **Amended 2026-08-21, after implementation:** original V1 scope was Job Detail and Dashboard only, with Job List explicitly excluded (see rejected reasoning below, kept for context). Extended during JOB-194 to also cover Job List and the Approvals queue, on direct request — same reusable control, no design change, just two more call sites.

## Product decisions

- **Scope: Job Detail, Dashboard, Job List, and Approvals.** Originally scoped to Job Detail and Dashboard only for V1, with Job List deliberately excluded on the reasoning that it already refetches more naturally on navigation/filter changes than the other two pages (see Alternatives). That reasoning is still true, but the control was added there anyway (and to Approvals, which has the same "sit on one screen" staleness risk as Dashboard/Job Detail) since the component is a trivial reuse once built — no separate design decision, just consistency across every page that shows live team data.
- **Visual treatment:** a small "Last updated Xm ago" label next to a refresh icon — more informative than a bare icon, since it tells the user whether the data might already be stale before they even click.
- **Loading state while refetching:** the button shows a brief spinner/disabled state while the request is in flight, returning to normal once it resolves — prevents a user clicking again or assuming it's broken during a momentarily slow refetch.
- **No explicit change-confirmation** after refresh — the re-rendered page with fresh data is self-evident; adding a "3 things changed" summary would be over-engineering for what's meant to be a simple escape hatch.
- Explicit, user-initiated action, not automatic polling — avoids UI shifting under someone's cursor mid-read/mid-edit, avoids added backend load from every open tab polling.

## Technical design

### Database
None.

### API
None — purely a frontend affordance on top of existing data-fetching.

### Backend
None.

### Frontend
- Shared `RefreshButton` component (icon + "Updated Xm ago" label), reused as-is across all four pages — a dumb presentational control that takes `lastUpdated`/`isFetching`/`onRefresh` as props and ticks its own internal 30s timer to advance the label without needing fresh data. Each page wires it to its own query's `dataUpdatedAt`/`isFetching` (TanStack Query already exposes both — no new state needed) and its own `refetch`/`invalidateQueries` call.
- Dashboard, Job List, Approvals: single query per page, so the button calls that query's own `refetch()` directly.
- Job Detail: several queries share the page (job itself, notes, approvals, history — job status history is a separate fetch; relationships are embedded fields on the job object itself, refreshed for free). Refresh calls `queryClient.invalidateQueries({ queryKey: ['jobs', projectId, jobId] })`, which prefix-matches all of them in one click rather than calling four separate `refetch()`s.

### Constraints & edge cases
- Must not clobber in-progress edit state — refetching must not blow away an open inline-edit form's unsaved input.
- Refresh must not fire on top of an addon-gated query that's disabled — respects the same `enabled`/`hasAddon()` guards fixed in JOB-181 (confirmed merged, `b2f2eb8`, PR #356). This ADR is built on top of that fix, not a reason to re-litigate it.

## Alternatives considered

### Automatic polling instead of manual refresh

Rejected — adds backend load from every open tab polling and can shift the UI under a user's cursor mid-read/mid-edit; explicitly out of scope per the original FC, revisit only if manual refresh proves insufficient.

### Icon-only button, no "last updated" label

Considered for a simpler visual. Rejected — the label adds real information (is this actually stale?) for negligible extra complexity.

### Confirmation of what changed after refresh

Rejected — over-engineering for a simple escape-hatch action; the re-render already shows the result.

### No loading state on the button itself

Considered, since refetches are usually fast. Rejected — a momentarily slow refetch with no feedback risks a user clicking again or assuming the button is broken; a spinner/disabled state costs little and removes that ambiguity.

## Consequences

### Positive
- Cheap, contained fix for the "silently stale while staring at the tab" gap TanStack Query's focus-refetch doesn't cover
- "Last updated" label gives useful context even before a user clicks refresh
- Loading state avoids double-click/broken-button confusion during a slow refetch

### Negative
- Still just an escape hatch, not a guarantee — a user has to notice and act on it; full real-time correctness would need push-based updates (WebSocket/SSE), explicitly not being built here

### Neutral
- Job List was intentionally excluded from the original V1 scope, then added anyway during implementation (see amendment) — not a reversal of the reasoning, just a low-cost consistency extension once the shared component existed

## Implementation order
1. `RefreshButton` component, with `isFetching`-driven loading state
2. Wire into Job Detail page (`invalidateQueries` on the `['jobs', projectId, jobId]` prefix)
3. Wire into Dashboard page
4. Wire into Job List page (scope expansion, see amendment above)
5. Wire into Approvals page (scope expansion, see amendment above)

## References

- JOB-181 (Maintenance, completed): addon-gated query fix this ADR's refresh behavior must respect
- JOB-132 (Future Consideration, promoted to PRJ-010/MIL-030): original scoping notes this ADR implements
- JOB-194 (PRJ-010, "Manual refresh button on high-churn collaborative pages"): implementation, including the scope amendment to Job List and Approvals
