# ADR-0023: User Preferences

**Status:** Accepted
**Date:** 2026-03-18
**Author:** Jovan Manojlovic

## Context

The app already persists theme preference via `usePreferences` and localStorage. However, view settings — default tab, view mode, deadline format, dashboard section visibility, and default project page — are not yet persisted. Every time users open the app, these reset to defaults. Power users who always want a specific tab, view mode, or deadline format have to reconfigure manually every session.

The target users are SME owners and managers — typically on one device — who use the app daily and quickly form habits around their preferred layout.

## Decision

### Storage

**localStorage** — no backend changes needed.

Acceptable because target users (SME owners) typically use one device. Server-side sync across devices is out of scope for now and can be revisited based on demand.

A single `usePreferences` hook reads from and writes to localStorage, providing typed access to all preferences. Consumers never touch localStorage directly.

### Preferences in scope

**Job list**

| Preference | Options | Default |
|------------|---------|---------|
| Default view mode | `GROUPED`, `FLAT` | `GROUPED` when milestones exist, `FLAT` otherwise |
| Milestone accordion state | `EXPANDED`, `COLLAPSED` | `EXPANDED` |
| Default status tab | `ALL`, `NEW`, `IN_PROGRESS`, `BLOCKED`, `COMPLETED` | `ALL` |
| Hide completed from ALL tab | `true`, `false` | `false` |
| Default sort order | `DEADLINE_ASC`, `DEADLINE_DESC`, `PRIORITY_DESC`, `CREATED_DESC` | `DEADLINE_ASC` |

The milestone accordion state is global — the same across all projects.

**Display**

| Preference | Options | Default |
|------------|---------|---------|
| Deadline format | `ABSOLUTE` (15 Apr 2026), `RELATIVE` (in 3 days) | `ABSOLUTE` |

`ABSOLUTE` is the default — it always means the same thing regardless of when you look at it. `RELATIVE` is more scannable for active users.

When format is `RELATIVE` and the deadline has passed, show **"X days overdue"** in red. Consistent with the relative format and color-signals urgency.

**Dashboard**

| Preference | Options | Default |
|------------|---------|---------|
| Show blocked jobs section | `true`, `false` | `true` |
| Show overdue jobs section | `true`, `false` | `true` |
| Show pending approvals section | `true`, `false` | `true` |

When a section is hidden, its data fetch is skipped entirely — not just collapsed. This keeps the dashboard fast for users who don't need all sections.

**Navigation**

| Preference | Options | Default |
|------------|---------|---------|
| Default project page | `DASHBOARD`, `JOBS`, `APPROVALS` | `DASHBOARD` |

Applied when navigating to `/projects/:id` with no deeper path. Global — the same default across all projects. If the chosen section has no data, the app still lands there and shows the empty state.

### Settings location

**User Settings page** (`/settings`) — already exists. A new "Preferences" section is added below the existing profile fields.

### Fallback

If no preference is stored, sensible defaults apply:

| Preference | Default |
|------------|---------|
| View mode | `GROUPED` when milestones exist, `FLAT` otherwise |
| Accordion | `EXPANDED` |
| Status tab | `ALL` |
| Hide completed from ALL tab | `false` |
| Default sort order | `DEADLINE_ASC` |
| Deadline format | `ABSOLUTE` |
| Dashboard sections | all visible |
| Default project page | `DASHBOARD` |

## Alternatives Considered

### Alternative 1: Server-side preferences (user_preferences table)

Store preferences in the database per user.

**Pros:**
- Syncs across devices
- Survives localStorage clears

**Cons:**
- Requires new backend endpoint and DB migration
- Adds latency on page load (must fetch preferences before rendering)
- Overkill for target users who use one device

**Why rejected:** The complexity is not justified for the current user profile. Can be added later if multi-device usage becomes a real need.

### Alternative 2: URL state for view preferences

Encode view mode and tab in the URL query string.

**Pros:**
- Shareable links that preserve view state
- No storage needed

**Cons:**
- Clutters the URL
- Resets on every new navigation
- Not a true "preference" — persists per link, not per user

**Why rejected:** Does not solve the problem. Users want their preference remembered, not encoded in a URL they have to re-share.

## Consequences

### Positive

- Zero backend changes — ships fast
- Every power user immediately benefits on every page
- Improves the "3 clicks max" principle by landing users on their preferred view

### Negative

- Preferences are lost if localStorage is cleared
- No cross-device sync
- Must remember to read preferences in every component that has a preference-driven default

### Neutral

- A `usePreferences` hook centralises all preference reads/writes
- The Settings page gains a new section
- Dashboard fetches become conditional on preference flags

## Implementation Notes

1. Create `usePreferences` hook — typed read/write wrapper over localStorage
2. Apply `defaultViewMode` in `JobListPage`
3. Apply `milestoneAccordionState` in `JobListPage`
4. Apply `defaultStatusTab` in `JobListPage`
5. Apply `hideCompletedFromAll` in `JobListPage` — when `true` and the ALL tab is active, filter out COMPLETED jobs
6. Apply `defaultSortOrder` in `JobListPage` as the initial sort selection
7. Apply `deadlineFormat` wherever deadlines are displayed
8. Apply `dashboard*` flags in `DashboardPage` to conditionally fetch and render sections
9. Apply `defaultProjectPage` in the `/projects/:id` redirect route
10. Add Preferences section to `SettingsPage`

## References

- [ADR-0011: Frontend Architecture](0011-frontend-architecture.md)
- [ADR-0014: Jobs Screens](0014-jobs-screens.md)
- [ADR-0017: Dashboard](0017-dashboard.md)
- [ADR-0018: User Settings](0018-user-settings.md)
