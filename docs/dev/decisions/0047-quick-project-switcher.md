# ADR-0047: Quick Project Switcher

**Status:** Proposed
**Date:** 2026-08-14
**Author:** Jovan Manojlovic

## Context

Anyone managing more than one or two projects currently has to go back to the `/projects` list to switch context, even though the current project's name already sits in the header. Shipping in the same phase as ADR-0045 (org-wide project directory), which this ADR consumes for its Owner/Admin scope.

## Decision

Turn the existing `ProjectBreadcrumb` (next to the logo in the header) into a dropdown trigger listing other projects, sorted by recency, with the current project excluded.

## Product decisions

- The existing `ProjectBreadcrumb` becomes a dropdown trigger — small chevron, click opens a list of other projects.
- **List scope:** active projects only, not `COMPLETED` — speed for the handful of projects someone's actively bouncing between; completed projects stay reachable via the full `/projects` page's tabs.
- **For Members:** only projects they're a member of. **For Owner/Admin:** the switcher includes the org-wide project list from ADR-0045's directory endpoint (projects they're not a member of too), not just their own — reusing that data rather than duplicating a second "all projects" query. (The endpoint itself is unaffected by ADR-0045's later placement amendment — only where the standalone directory page renders changed, not this API contract.)
- **Sort order: most-recently-visited first**, not alphabetical — matches how quick-switchers are generally expected to work. Tracked via `localStorage` (`lastVisitedProjects: {projectId: timestamp}`), same pattern as existing preferences (ADR-0018/0023) — no backend needed.
- **`lastVisitedProjects` capped at the 10 most recent entries** — pruned on write so it never grows unbounded across months of use. Cheap to add, avoids indefinite `localStorage` bloat for no benefit past a small working set.
- **The currently-open project is excluded from its own switcher list** — selecting the project you're already looking at is a no-op, so showing it would just be noise in a list meant to be fast to scan.
- **Page-type preserved on switch** — selecting a project from, say, Project A's Milestones page lands on Project B's Milestones page (substituting the project ID in the current route), not always the default/dashboard page. Falls back to the default page if the target project doesn't have the current page's feature/addon active.
- No search/filter box for V1 — a plain scrollable list is enough given typical project counts; add later if it proves necessary.

## Technical design

### Database
None.

### API
None new for Members (reuses the existing project-list data already loaded). For Owner/Admin, reuses ADR-0045's directory endpoint (`GET /api/organisations/{orgId}/projects/directory`).

### Backend
None beyond what ADR-0045 already provides.

### Frontend
- `ProjectBreadcrumb` becomes a dropdown trigger (small chevron), opening a project list.
- `lastVisitedProjects` tracked in `localStorage`, updated on every project page load, capped to the 10 most recent entries (oldest pruned on write); switcher list sorted by this, falling back to alphabetical for projects with no recorded visit.
- The current project's ID is filtered out of the rendered list regardless of its recency position.
- Route-substitution logic: on selecting a project, swap the project ID segment in the current route and re-navigate, falling back to the project's default page if the resulting route isn't valid for the target project (e.g. an addon-gated page the target project doesn't have active).

### Constraints & edge cases
- Route-substitution must degrade gracefully — never navigate to a broken/locked page in the target project.
- Owner/Admin's expanded (org-wide) list must respect ADR-0045's own access restrictions — no additional bypass introduced here.
- `lastVisitedProjects` pruning must not drop the entry being written this visit — cap applies to the other 9, not by evicting the current page's own project.

## Alternatives considered

### Alphabetical sort

Rejected — recency is more useful for a fast-switch tool aimed at the projects someone's actively working across; alphabetical suits browsing, not quick-switching.

### Always land on the default/dashboard page after switching

Considered for simplicity. Rejected — loses context for no real complexity savings; route substitution is straightforward given React Router already exposes the current route pattern.

### Search/filter box in the dropdown for V1

Rejected — typical project counts here don't justify it yet; a plain list is simpler and can gain search later if needed.

### Unbounded `lastVisitedProjects` (no cap)

Considered, since `localStorage` is cheap and the data is small per entry. Rejected — capping at 10 costs almost nothing to implement and avoids indefinite growth with no corresponding benefit (nobody needs their 200th-most-recently-visited project in a "quick" switcher).

### Show the current project in its own list (e.g. marked/disabled)

Considered, for a sense of "you are here." Rejected — the list is meant to be scanned fast; excluding a guaranteed no-op entry keeps it shorter and avoids a special disabled-row UI state for no real benefit.

## Consequences

### Positive
- Removes the most common navigation friction for anyone working across multiple projects
- Reuses ADR-0045's directory data for the Owner/Admin case rather than building a second "all projects" query
- Bounded `localStorage` usage, no unbounded growth over time

### Negative
- Route-substitution logic adds a small amount of frontend complexity (validating whether the target route is valid for the destination project)

### Neutral
- No backend changes for the Member case — purely a frontend UI + `localStorage` feature
- Like other `localStorage`-based preferences in this app, recency tracking doesn't follow a user across devices/browsers — consistent with the existing precedent (ADR-0018/0023), not a new limitation introduced here

## Implementation order
1. `lastVisitedProjects` tracking in `localStorage`, capped at 10, current project excluded from rendering
2. Dropdown UI on `ProjectBreadcrumb` (active projects only, recency-sorted)
3. Route-substitution logic with fallback to default page
4. Owner/Admin expanded list, consuming ADR-0045's directory endpoint

## References

- ADR-0045: Org-Wide Project Directory for Owner/Admin (`docs/dev/decisions/0045-org-wide-project-directory.md`) — directory endpoint this ADR consumes for Owner/Admin scope
- ADR-0018: User Settings (`docs/dev/decisions/0018-user-settings.md`) — `localStorage`-only preference storage precedent
- ADR-0023: User Preferences (`docs/dev/decisions/0023-user-preferences.md`) — same precedent, extended
- JOB-148 (Future Consideration, promoted to PRJ-010/MIL-029): original scoping notes this ADR implements
