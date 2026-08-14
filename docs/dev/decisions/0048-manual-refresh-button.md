# ADR-0048: Manual Refresh Button

**Status:** Proposed
**Date:** 2026-08-14
**Author:** Jovan Manojlovic

## Context

TanStack Query already refetches on window focus, covering the common case of tabbing away and back. It doesn't cover a user staring at an open tab the whole time while a teammate edits the same job or dashboard elsewhere — the screen goes silently stale with no signal and no way to force a refresh short of a full page reload.

## Decision

Add an explicit, user-initiated refresh control with a "Last updated Xm ago" label to Job Detail and Dashboard, rather than automatic polling.

## Product decisions

- **Scope: Job detail and Dashboard for V1.** Job list isn't included — it already refetches more naturally on navigation/filter changes.
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
- Refresh control + "Last updated Xm ago" label in the header of Job Detail and Dashboard pages, with a loading/disabled state driven by the query's own `isFetching` state (TanStack Query already exposes this — no new state needed).
- Triggers `queryClient.invalidateQueries()` / `refetch()` for that page's existing queries.
- "Last updated" timestamp tracked from the query's own `dataUpdatedAt` (also already exposed).

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
- Job list intentionally excluded from V1 scope

## Implementation order
1. Refresh control + "Last updated Xm ago" label component, with `isFetching`-driven loading state
2. Wire into Job Detail page
3. Wire into Dashboard page

## References

- JOB-181 (Maintenance, completed): addon-gated query fix this ADR's refresh behavior must respect
- JOB-132 (Future Consideration, promoted to PRJ-010/MIL-030): original scoping notes this ADR implements
