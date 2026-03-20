# ADR-0024: Milestone Management UX

**Status:** Accepted
**Date:** 2026-03-20
**Author:** Jovan Manojlovic

## Context

Two UX gaps were identified after the milestone feature shipped:

1. **Milestone editing is awkward** — inline editing in Project Settings does not work well for three fields (name, description, deadline). The inline pattern was designed for single-field edits and feels cramped with multiple fields.
2. **No progress visibility** — there is no quick way to see how complete a milestone is without manually scanning the job list. Owners and managers need progress at a glance.

## Decision

### Dedicated Milestones page

Replace the milestones section in Project Settings with a dedicated `/projects/:id/milestones` page — a first-class nav item alongside Dashboard, Jobs, and Approvals.

Each milestone row shows:
- Name and description
- Deadline
- Progress bar + fraction (e.g. ████░░ 4/6)
- Edit and Delete actions
- "View jobs →" link

### View jobs

Clicking "View jobs →" navigates to the Jobs page pre-filtered to that milestone (`?milestone=<id>`). Jobs live in one place — the Jobs page. The Milestones page stays focused on planning only, no job list embedded.

### Milestone editing

**Modal edit** — a dialog with name, description, and deadline fields. Opened from the Edit button on each milestone row. A dedicated page is overkill for three fields.

New milestone creation also uses a modal, consistent with the existing New Job flow.

### Progress bar

Progress is defined as completed jobs out of total jobs in the milestone.

- Only `COMPLETED` status counts as done
- Format: visual bar + either fraction (e.g. `4/6`) or percentage (e.g. `67%`) — user-configurable via a preference in Settings
- Default: fraction — communicates both done and total at a glance
- Shown on milestone rows in the Milestones page
- Also shown on milestone group headers in the Job List (ADR-0021)
- Milestones with zero jobs show no bar

Progress is computed on the frontend from job data already loaded — no new backend endpoint needed.

### Project Settings

Milestone management moves out of Project Settings entirely once the dedicated page exists. The milestones section is removed from `/projects/:id/settings`.

## Alternatives Considered

### Alternative 1: Keep milestones in Project Settings, add progress inline

Extend the existing inline-edit pattern with a progress indicator.

**Pros:**
- No new page or nav item

**Cons:**
- Inline-edit with three fields is awkward and does not scale
- Project Settings mixes configuration (members, roles) with operational planning (milestones) — wrong abstraction

**Why rejected:** Milestones are operational content, not project configuration. They deserve first-class navigation.

### Alternative 2: Dedicated milestone detail page

Each milestone gets its own page with full edit form and embedded job list.

**Pros:**
- More room for future milestone-level features

**Cons:**
- Overkill for three fields
- Jobs already have a dedicated page with better filtering and sorting
- Duplicates job list functionality

**Why rejected:** A modal covers the edit case cleanly. Jobs belong on the Jobs page.

### Alternative 3: Show percentage instead of fraction

Display progress as a percentage (e.g. 67%) instead of a fraction (4/6).

**Pros:**
- Familiar format

**Cons:**
- Percentage hides the total — "67%" is less informative than "4/6" when you need to know how many jobs remain
- Fraction communicates both done and total at a glance

**Why not default:** Fraction is more informative for operational tracking. Both are valid — user can switch via a preference in Settings.

## Consequences

### Positive

- Milestones become first-class — owners can manage and track them without going into settings
- Progress is visible at a glance on both the Milestones page and the Job List
- Modal edit is consistent with the existing New Job pattern
- Project Settings becomes cleaner — focused on configuration only

### Negative

- A new nav item increases the nav bar width — needs testing on small screens
- Progress computed on the frontend means it reflects whatever job data is currently loaded (stale if cache is old)

### Neutral

- The Jobs page gains a `?milestone=` query param filter entry point
- Milestone group headers in the Job List gain a progress bar

## Implementation Notes

1. Add `/projects/:id/milestones` route and nav item
2. `MilestonesPage` — list all milestones for the project with progress, edit, delete, view jobs
3. Edit milestone modal — name, description, deadline fields, PUT endpoint
4. Progress bar component — reusable, takes `completed` and `total` props
5. Add `milestoneProgressFormat`: `FRACTION` | `PERCENTAGE` preference (default: `FRACTION`) to `usePreferences` and Settings page
6. Wire progress bar into `MilestonesPage` rows (compute from job data)
7. Wire progress bar into `GroupSection` headers in `JobListPage`
8. Remove milestones section from `ProjectSettingsPage`
9. Add `?milestone=<id>` filter support as entry point from "View jobs →" link

## References

- [ADR-0013: Projects Screens](0013-projects-screens.md)
- [ADR-0014: Jobs Screens](0014-jobs-screens.md)
- [ADR-0018: User Settings](0018-user-settings.md)
- [ADR-0021: Milestone Grouping](0021-milestone-grouping.md)
