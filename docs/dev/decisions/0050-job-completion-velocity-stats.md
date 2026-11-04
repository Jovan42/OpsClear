# ADR-0050: Job Completion Velocity Stats

**Status:** Proposed
**Date:** 2026-11-04
**Author:** Jovan Manojlovic

## Context

The Dashboard (ADR-0017) answers a point-in-time question: what is the status of our work right now. Nothing answers the trend question: how many jobs did we finish this week compared to last week, how long jobs actually take, how much time is lost to blocking, and how often work lands after its deadline.

All the raw data already exists. Every status change is recorded in `job_status_history` (ADR-0028), including the creation entry (`null → NEW`), so completion times, working time and blocked time can be derived without new tables.

Promoted from JOB-236 (Future Consideration, PRJ-002) into PRJ-012 / MIL-034. This ADR is JOB-245.

### Facts from the existing system that shape this design

- **Members only see jobs assigned to them.** `JobService.list()` scopes MEMBERs to their own jobs, and the Dashboard inherits that (ADR-0017 §5). Stats must follow the same rule.
- **Only status changes are recorded.** Deadline changes and reassignments are not in any history table, so the current `deadline` and current `assigned_to` are the only values available.
- **Completed jobs can be reopened** (`COMPLETED → IN_PROGRESS`, Owner/Admin only), so a job can have more than one Completed transition.
- **The Dashboard already uses Recharts** (ADR-0017 §6). JOB-245 noted the donut as custom-built. It isn't, but the conclusion holds: no new charting dependency is needed.

## Decision

Add a per-project **Stats** page next to the Dashboard, backed by one read-only endpoint that computes completion velocity metrics at request time with a plain SQL query over `jobs` and `job_status_history`. Gated by the existing `DASHBOARD` add-on.

## Product decisions

### Scope: per project only

- New tab in the project nav, right after Dashboard: `/projects/:projectFriendlyId/stats`.
- Stats cover a single project. **No org-wide or cross-project stats**: not in V1, and not planned as a follow-up. Owners with several projects check each project's Stats tab.
- This resolves JOB-245's open question "filterable by member and/or project": the project is fixed by the page, so the only filter is member.

### Who sees what

| Role | Sees |
|---|---|
| Owner / Admin | Team totals by default, plus an opt-in **By member** breakdown and drill-down into one member |
| Member | **Only their own stats**: the same metrics computed over jobs assigned to them. No team totals, no other members. |

- Team totals stay the default view for Owner/Admin. The per-member breakdown is one click away, not the first thing rendered, so the page doesn't read as a leaderboard (settled in JOB-245).
- The Member rule mirrors the existing job-visibility rule. A Member never sees numbers derived from jobs they can't open.

### Time range

- Always bucketed by **week** (ISO weeks, Monday start).
- Range presets: **last 4 / 12 / 26 weeks**, default 12. The current week is included as the last (partial) bucket.
- Weeks are computed in the **viewer's timezone**, sent by the browser, so "this week" matches the viewer's calendar.

### Metrics

All metrics are computed over one cohort: **jobs completed in the period**. A job belongs to the week of its completion.

| Metric | Definition |
|---|---|
| **Completed** | Number of jobs in the cohort. |
| **Avg. total time** (lead time) | Created → Completed. What the client experiences, including time waiting in New. |
| **Avg. working time** (cycle time) | First move to In Progress → Completed. Excludes backlog waiting time. |
| **Avg. blocked time** | Per job, the sum of all Blocked intervals before completion, averaged over the cohort. |
| **Completed late** | % of cohort jobs **that have a deadline** where completion happened after the deadline. Jobs without a deadline are excluded from this rate (shown as "—" if none have one). |

Both time metrics are shown side by side (decided): total time answers "how long does the client wait", working time answers "how long does the work take".

### Counting rules

- **Completion time = the job's last `→ COMPLETED` transition**, and the job counts only if it is **still Completed now**. A reopened job that is still open drops out of the cohort; once re-completed, it counts once, in the week of its final completion.
- **Working time** runs from the **first** `→ IN_PROGRESS` transition ever to the last completion, so time spent after a reopen is included.
- **Attribution = current assignee** (`jobs.assigned_to`). If a job was reassigned, earlier assignees get no credit; this is a known limitation because reassignments aren't recorded. Unassigned completed jobs count in team totals only.
- **"Late"** uses the same comparison as the Dashboard's overdue list (`deadline < completedAt`, as instants), so the two pages never disagree about what late means. Because deadlines are stored as midnight UTC, a job completed during its deadline day counts as late. That matches today's Dashboard behaviour, and changing it is out of scope here.
- Soft-deleted jobs are excluded. Jobs in a completed project still count (the page is read-only).

### What the page shows

- **Range selector** (4 / 12 / 26 weeks).
- **Summary tiles** for the whole range: Completed, Avg. total time, Avg. working time, Avg. blocked time, Completed late %.
- **Weekly chart**: bars for jobs completed per week. Empty weeks show as zero, not gaps.
- **Owner/Admin only**: a **Team / By member** toggle. *By member* shows a table with one row per member (same five metrics). Clicking a row filters the whole page to that member, with a clear "Showing: <name> · back to team" indicator.
- **Member**: the same tiles and chart titled "Your stats". No toggle.
- **Empty state** (ADR-0041 `EmptyState`) when nothing was completed in the range.

## Technical design

### Database

None. No migration, no new table, no view required. A DB view may optionally wrap the query for readability, but it is not needed for V1. Existing index `idx_job_status_history_job_id` covers the per-job history lookups, and the query is bounded to one project.

### API

```
GET /api/projects/{projectId}/stats?weeks=12&tz=Europe/Belgrade&memberId={uuid}
```

| Param | Rules |
|---|---|
| `weeks` | `4`, `12` or `26`. Default `12`. Anything else → 400. |
| `tz` | IANA zone ID (validated with `ZoneId.of`). Default `UTC`. Invalid → 400. |
| `memberId` | Optional. Owner/Admin: filter to that member. Member: may be omitted or equal to themselves; any other value → 403. |

Response (durations in **seconds**, formatted by the frontend as ADR-0028 already does for history durations):

```json
{
  "weeks": 12,
  "timezone": "Europe/Belgrade",
  "memberId": null,
  "summary": {
    "completedCount": 34,
    "avgLeadTimeSeconds": 412800,
    "avgCycleTimeSeconds": 201600,
    "avgBlockedSeconds": 28800,
    "completedWithDeadline": 20,
    "completedLate": 5,
    "lateRate": 0.25
  },
  "buckets": [
    {
      "weekStart": "2026-08-17",
      "completedCount": 3,
      "avgLeadTimeSeconds": 388800,
      "avgCycleTimeSeconds": 190800,
      "avgBlockedSeconds": 0,
      "completedWithDeadline": 2,
      "completedLate": 1
    }
  ],
  "members": [
    {
      "userId": "…",
      "name": "Alice Example",
      "completedCount": 12,
      "avgLeadTimeSeconds": 360000,
      "avgCycleTimeSeconds": 180000,
      "avgBlockedSeconds": 7200,
      "completedWithDeadline": 8,
      "completedLate": 1,
      "lateRate": 0.125
    }
  ]
}
```

- `buckets` always has exactly `weeks` entries, oldest first, with empty weeks as zeros and `null` averages.
- `lateRate` and the averages are `null` when their denominator is zero.
- `members` is returned only for Owner/Admin and only when `memberId` is absent. It's `null` for Members and for filtered requests. It lists all **current project members** (including those with zero completions), plus any former member who is still the assignee of a cohort job.

### Backend

- **`StatsController`**: `@RequiresAddon(AddonCode.DASHBOARD)` (ADR-0031), same as `DashboardController`. Extracts the caller from the JWT, validates params with `@Valid`/explicit checks.
- **New `StatsService`** (`@Transactional(readOnly = true)`): verifies project membership, applies role rules (forces `memberId = caller` for MEMBER, 403 on a mismatch), calls the repository and assembles the response. It does **not** extend `DashboardService`, which ADR-0017 marks as the only allowed service-to-service exception. `StatsService` reads through its own repository instead.
- **New `JobStatsRepository`** (jOOQ). One query for the project, built from CTEs:
  1. `history`: rows for the project's non-deleted jobs, with `LEAD(changed_at) OVER (PARTITION BY job_id ORDER BY changed_at)` as each interval's end.
  2. `per_job`: per job: last `→ COMPLETED` time, first `→ IN_PROGRESS` time, sum of `BLOCKED` interval lengths ending at or before completion, plus `jobs.created_at`, `deadline` and `assigned_to`.
  3. `cohort`: jobs currently `COMPLETED` whose last completion falls inside the range (range start = Monday 00:00 of the oldest week in `tz`), optionally filtered by `assigned_to = memberId`.
  4. Week key: `date_trunc('week', completed_at AT TIME ZONE 'UTC' AT TIME ZONE :tz)`.
  - The service aggregates per week, per member and in total in Java from the per-job rows (at SME scale a project's completed jobs in 26 weeks is a small set), then fills missing weeks with zeros.
- No caching, no materialized view, no rollup table (settled in JOB-245; revisit only if real usage shows the plain query is too slow).
- The `JOB_STATUS_HISTORY` add-on is **not** required. That add-on gates viewing a job's history timeline; history rows are recorded for every job regardless, and Stats only exposes aggregates.

### Frontend

- **Route**: `projects/:projectFriendlyId/stats` → `features/stats/StatsPage.tsx`, data via `useProjectStats.ts` (TanStack Query, key includes `weeks`, `tz`, `memberId`).
- **Nav**: add a `stats` item in `useProjectNavItems.ts` right after `dashboard`, gated by `hasAddon('DASHBOARD')`, with a `stats-locked` variant matching `dashboard-locked` when the add-on is missing.
- `tz` comes from `Intl.DateTimeFormat().resolvedOptions().timeZone`.
- **Chart**: Recharts `BarChart`, reusing the Dashboard's chart styling. No new dependency.
- **Durations**: formatted with the existing `formatDuration` helper (`features/jobs/utils/statusHistoryUtils.ts`, used by the job status history timeline), lifted to a shared location if needed.
- **i18n**: new keys in `approvalsDashboardSettingsLanding.json` for both `en` and `sr`.
- **E2E**: per E2E.md, sized to the feature: Owner sees team tiles and the chart, toggles By member and drills into one member and back. Member sees only "Your stats". The locked tab shows when `DASHBOARD` is missing.

### Constraints & edge cases

- **Project with no completions in range** → tiles show 0 / "—", chart shows empty weeks, `EmptyState` message.
- **Reopened and still open** → excluded from the cohort until re-completed.
- **Job completed, then soft-deleted** → excluded.
- **Assignee removed from the project** → their completed jobs still count in team totals. They appear in `members` only while they are the assignee of a cohort job.
- **DST changes** → handled by bucketing in the viewer's IANA zone in Postgres rather than with fixed UTC offsets.
- **Member passes another user's `memberId`** → 403, consistent with job visibility.

## Alternatives considered

### Org-wide / cross-project stats (e.g. a section on `/overview`)
Rejected. Everything except `/overview` is project-scoped. An org-wide per-member view visible only to the owner is the most leaderboard-like version of this feature. It also raises unresolved aggregation questions (people on several projects, projects with different deadline habits). Not planned as a follow-up.

### Extend `DashboardService` / the dashboard endpoint
Rejected. The Dashboard is already dense, and JOB-245 settled on a dedicated page. ADR-0017 also makes `DashboardService` the single allowed exception to the no-service-to-service-calls rule, so piling more into it is the wrong direction.

### Compute on the frontend from per-job history (ADR-0028 pattern)
Rejected. That works for a single job's timeline but would need one history request per job for aggregates. One server-side query is simpler and far cheaper.

### Materialized view or incremental rollup table
Rejected (settled in JOB-245). No evidence of a performance problem at SME scale; adds refresh logic and a DB job for no measurable gain.

### A new `VELOCITY_STATS` add-on
Rejected for V1. Stats are a natural extension of the Dashboard. A new add-on needs a migration (add-on row + pricing), which adds a DB job and pricing work that the milestone doesn't need. **Open to change in review** if Stats should be sold separately.

### Attribute completions to whoever clicked Completed (`changed_by`)
Rejected. An Owner/Admin closing a job on someone's behalf would take the credit. The current assignee matches what the job shows.

### Count every Completed transition
Rejected. A reopened and re-completed job would be counted twice, inflating throughput.

### "Ever overdue" or "currently overdue" as the overdue metric
Rejected. "Ever overdue" can't be computed reliably because deadline changes aren't recorded. "Currently overdue" is a snapshot the Dashboard already shows. "Completed late" can be computed exactly and is a trend.

### Custom date range instead of week presets
Rejected for V1. More UI and edge cases (bucket size selection, very long ranges) for little extra value over 4/12/26 weeks.

### Median instead of average
Considered, since averages are sensitive to one very long job. Kept average for V1 because it's what JOB-245 specifies and is easier to explain. Revisit if outliers make the numbers misleading in practice.

## Consequences

### Positive
- Owners/Admins get the trend view the Dashboard can't give: throughput, how long work takes, blocking cost, deadline reliability.
- No schema change, no new dependency, no new add-on, so the milestone is just two implementation jobs.
- Member visibility stays consistent across the job list, the Dashboard and Stats.

### Negative
- Attribution ignores reassignment history, and "late" ignores deadline changes. Both are limits of what's recorded today. Fixing them would need new history tables (separate Future Consideration if it matters).
- "Late" inherits the Dashboard's midnight-UTC deadline comparison, so a job finished on its deadline day counts as late.
- Averages can be skewed by a few very long jobs.

### Neutral
- Adds a second page gated by `DASHBOARD`. The add-on's description on the pricing page may need a line mentioning Stats.
- The API checklist below applies (new endpoint).

## Implementation order

Two jobs in MIL-034, no DB job:

1. **Backend: project velocity stats endpoint**: `StatsController`, `StatsService`, `JobStatsRepository`, `GET /api/projects/{projectId}/stats`. Unit tests for `StatsService` (role rules, param validation, week filling, null averages). Integration tests via `ApiPaths` + mock JWT covering: metric definitions on seeded history (lead/cycle/blocked/late), reopened jobs, unassigned jobs, Member scoping and the 403 on another `memberId`, invalid `weeks`/`tz` → 400, missing `DASHBOARD` add-on.
2. **Frontend: project Stats page**: route, nav item (+ locked variant), `StatsPage`, `useProjectStats`, tiles, weekly chart, Team/By member toggle and drill-down, Member "Your stats" view, empty state, en/sr strings, E2E coverage, manual test checklist. **Blocked by** job 1.

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`) with `GET /api/projects/{projectId}/stats`
- [ ] Add example requests (default, `weeks=4`, `memberId` filter)
- [ ] Update environment variables if needed
- [ ] Test the flow manually before marking complete

## References

- ADR-0017: Dashboard (per-project scope, MEMBER visibility, Recharts, `DashboardService` exception)
- ADR-0028: Job status change history (data source, creation entry, duration formatting on the frontend)
- ADR-0031: Feature gating (`@RequiresAddon`, `DASHBOARD` add-on)
- ADR-0041: Polished empty states (`EmptyState`)
- JOB-236 (Future Consideration, PRJ-002): original idea
- JOB-245 (PRJ-012 / MIL-034): this ADR's job and its settled decisions
