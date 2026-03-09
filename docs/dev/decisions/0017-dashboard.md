# ADR-0017: Dashboard

**Status:** Accepted
**Date:** 2026-03-09
**Author:** Jovan Manojlovic

## Context

OpsClear's core promise is: *"What's the truth about our work TODAY?"* The job list page
answers this partially — it shows all jobs and allows filtering — but it doesn't surface
the most operationally important signals at a glance: what is blocked, what is overdue,
what is waiting for a decision.

Module 8.1 adds a dedicated dashboard per project that answers this question immediately
on entry, without requiring the user to filter or scroll.

The key decisions are: scope (per-project vs cross-project), data aggregation strategy
(dedicated endpoint vs frontend assembly from existing endpoints), `DashboardService`
implementation pattern (service-to-service vs repositories directly), what to show,
whether to include a chart, MEMBER vs OWNER/ADMIN visibility, and the frontend layout.

---

## Decision

### 1. Scope — per-project dashboard

The dashboard is scoped to a single project and lives at
`/projects/:projectId/dashboard`. It is the first screen shown when navigating into a
project (replacing the direct jump to the job list).

**Why not cross-project:** A cross-project view (all blocked jobs across all the user's
projects) is more powerful for a multi-project owner, but requires aggregating across
an arbitrary number of projects. The per-project view is simpler, consistent with the
existing project-scoped architecture, and still fully answers the operational question
for the active project. Cross-project aggregation is tracked separately (#131) as a
future feature.

### 2. Data aggregation — single dedicated endpoint

A dedicated `GET /api/projects/:projectId/dashboard` endpoint returns everything the
dashboard needs in one request. A `DashboardService` queries the database and returns
a single `DashboardResponse`.

**Why not assemble from existing endpoints on the frontend:**

- The job list endpoint (`GET /jobs`) returns full job records for all jobs in the
  project — the dashboard only needs a subset of fields for a subset of jobs. Fetching
  the full list and filtering client-side wastes bandwidth on larger projects.
- A single round-trip gives the dashboard a fast, reliable load with no waterfall.

### 3. Dashboard response shape

```
GET /api/projects/:projectId/dashboard
→ DashboardResponse {
    summary: {
      total: int,
      newCount: int,
      inProgressCount: int,
      blockedCount: int,
      completedCount: int,
      overdueCount: int,
      pendingApprovalsCount: int
    },
    blockedJobs:       JobSummary[],      // status = BLOCKED, oldest blocked_at first
    overdueJobs:       JobSummary[],      // deadline < now && status != COMPLETED, deadline asc
    pendingApprovals:  ApprovalResponse[] // reuses existing DTO, shown to OWNER/ADMIN only
  }

JobSummary {
  id, title, client, assignedTo, assignedToName,
  deadline, status, blockedReason, blockedAt, blockedBy
}
```

`JobSummary` is a new lightweight DTO — a subset of `JobResponse`. It omits
`description`, `createdBy`, `createdAt`, `updatedAt`, `blockedReasonId` to keep
the payload compact.

**Counts in `summary`:** computed in `DashboardService` by grouping the job list by
status in Java — the list is already fetched to build `blockedJobs` and `overdueJobs`,
so there is no extra query cost.

**`overdueCount`** = jobs where `deadline < now` and `status != COMPLETED` (including
BLOCKED jobs — a blocked job with a missed deadline is doubly urgent).

**`pendingApprovalsCount`** = `pendingApprovals.size()` — derived from the list,
not a separate count query.

### 4. DashboardService — service-to-service calls as a deliberate exception

The established pattern in this codebase is controller → service → repository; services
do not call other services. `DashboardService` is an explicit, documented exception to
this rule.

**Rationale:** The dashboard is a read-only aggregation across multiple domains (jobs,
approvals). The MEMBER visibility scoping logic (`if role == MEMBER → filter by
assignedTo`) and the soft-delete filtering (`WHERE deleted_at IS NULL`) already live
in `JobService.list()` and `ApprovalService.listPendingByProject()`. Bypassing those
services to go directly to repositories would require duplicating that logic in
`DashboardService` — a worse trade-off than the service-to-service call.

`DashboardService` calls **only read methods** on other services and introduces no
circular dependencies:

```
DashboardService.get(projectId, callerId):
  1. requireProjectMember(projectId, callerId)  // single access check here
  2. jobs    = jobService.list(projectId, callerId)           // respects MEMBER visibility
  3. pending = approvalService.listPendingByProject(projectId, callerId)
  4. Derive summary counts from jobs list (Java stream)
  5. Filter blockedJobs  = jobs where status == BLOCKED, sort by blockedAt asc
  6. Filter overdueJobs  = jobs where deadline < now && status != COMPLETED, sort by deadline asc
  7. Return DashboardResponse
```

This pattern must not be generalised — `DashboardService` is the only service
permitted to call other services. Any future cross-domain reads should follow the
same pattern: one aggregation service, documented as an exception.

### 5. MEMBER visibility

`jobService.list()` already applies MEMBER scoping (MEMBERs only see their assigned
jobs). `DashboardService` inherits this automatically. A MEMBER's dashboard shows:

- Summary counts scoped to their assigned jobs only
- Blocked jobs: only their assigned blocked jobs
- Overdue jobs: only their assigned overdue jobs
- Pending approvals section: **hidden** for MEMBERs (they cannot decide approvals;
  the section is only operationally useful to OWNER/ADMIN)

### 6. Status distribution chart

The dashboard includes a **donut chart** showing the proportion of jobs by status
(NEW / IN_PROGRESS / BLOCKED / COMPLETED). It sits alongside the summary count cards
and gives an immediate visual read on project health.

**Why a donut chart, not a bar or line chart:**
- A donut encodes proportion — exactly the question "what fraction of our work is
  blocked/complete?" — better than a bar chart, which implies comparison over a
  dimension.
- A line/trend chart (jobs completed over time) would require time-series data not
  present in the current schema and implies a burndown mindset that conflicts with
  OpsClear's "truth today" philosophy.

**Chart library: Recharts** — lightweight (~100 kB gzip), React-native API, no
canvas/WebGL dependency. Sufficient for a single donut chart; avoids bringing in a
heavier library (Chart.js, D3) for one use case.

**Chart data** is derived entirely from `summary` counts already in `DashboardResponse`
— no additional API data needed.

**Empty state:** the chart is hidden if `summary.total === 0` (no jobs yet).

### 7. Frontend layout

The dashboard replaces the job list as the project landing page. The job list remains
accessible via the "Jobs" nav link.

```
┌────────────────────────────────────────────────────────────┐
│  ┌──────────────────────────┐  ┌─────────────────────────┐ │
│  │  Status donut chart      │  │  Summary cards          │ │
│  │                          │  │  ┌──────┐ ┌──────────┐  │ │
│  │    ●NEW  ●IN_PROGRESS    │  │  │NEW  2│ │IN PROG. 4│  │ │
│  │    ●BLOCKED  ●COMPLETED  │  │  └──────┘ └──────────┘  │ │
│  │                          │  │  ┌──────┐ ┌──────────┐  │ │
│  └──────────────────────────┘  │  │BLKD 2│ │DONE     4│  │ │
│                                │  └──────┘ └──────────┘  │ │
│                                │  ┌───────┐ ┌──────────┐ │ │
│                                │  │OVERD 1│ │APPROVALS3│ │ │
│                                │  └───────┘ └──────────┘ │ │
│                                └─────────────────────────┘ │
├────────────────────────────────────────────────────────────┤
│  Blocked  (2)                                              │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Install transformer · Jane Doe · blocked 5d ago      │ │
│  │ "Waiting for client to provide site access"     [→]  │ │
│  └──────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────┤
│  Overdue  (1)                                              │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Replace HVAC · Alex Smith · due Mar 1           [→]  │ │
│  └──────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────┤
│  Pending Approvals  (3)                        [→ Queue]   │
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Need to purchase transformer — €800                  │ │
│  │ Install transformer · Jane Doe · Mar 3               │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

- The **donut chart + summary cards** sit side by side in a two-column row at the top.
- Each **summary card** is clickable — navigates to the job list pre-filtered to that status.
- Each **blocked / overdue row** has a `[→]` link to the job detail page.
- The **Pending Approvals** section shows up to 5 items with a `[→ Queue]` link to
  the full approval queue. Hidden for MEMBERs.
- Sections with zero items are **hidden entirely** — an empty dashboard shows only the
  chart and summary cards.

### 8. Dashboard as project landing page

Navigating to `/projects/:projectId` redirects to `/projects/:projectId/dashboard`.
The "Jobs" nav link navigates to `/projects/:projectId/jobs` as before. A "Dashboard"
link is added to the project nav alongside Jobs / Approvals / Settings.

Router:
```
{ path: 'projects/:projectId', element: <Navigate to="dashboard" replace /> }
{ path: 'projects/:projectId/dashboard', element: <DashboardPage /> }
```

### 9. TanStack Query key and refresh

| Key | Usage |
|-----|-------|
| `['dashboard', projectId]` | Full dashboard response |

- **`staleTime`: 30 seconds** — operational data; short enough to catch co-worker
  updates, long enough to avoid hammering the API on quick navigations away and back.
- **Refetch on window focus**: enabled (default TanStack Query behaviour).
- Mutations that change job status, block/unblock, decide approvals invalidate
  `['dashboard', projectId]` alongside their existing query keys.

### 10. Feature folder structure

```
features/dashboard/
├── DashboardPage.tsx
└── useDashboard.ts

api/
└── dashboard.ts       # dashboardApi.get(projectId)

types/index.ts         # DashboardResponse, JobSummary added
```

---

## Alternatives Considered

### Alternative 1: Frontend assembles dashboard from existing endpoints

The frontend calls `GET /jobs` and `GET /approvals/pending` separately and assembles
the dashboard in the browser.

**Pros:** Zero backend work; reuses cached TanStack Query data.

**Cons:** Fetches full `JobResponse` records when only `JobSummary` fields are needed.
Two separate requests with no coordination.

**Why rejected:** A dedicated endpoint pays off immediately in payload size and
round-trip count. The backend addition is small.

### Alternative 2: DashboardService uses repositories directly

`DashboardService` injects `JobRepository` and `ApprovalRepository` directly,
bypassing the service layer entirely.

**Pros:** Consistent with the controller → service → repository pattern; avoids
service-to-service calls; could write a single optimised jOOQ query.

**Cons:** Requires duplicating MEMBER visibility scoping (`if role == MEMBER → filter
by assignedTo`) and soft-delete filtering that already live in `JobService` and
`ApprovalService`. Duplication is a worse trade-off than the controlled exception.

**Why rejected:** The MEMBER scoping logic is not trivial boilerplate — it involves
a role lookup and a conditional query branch. Duplicating it creates a second place
to update when the visibility rules change. The service-to-service call is documented
as a deliberate, bounded exception.

### Alternative 3: Cross-project dashboard as the app home screen

Global dashboard across all projects after login.

**Pros:** Single view of the entire operational picture.

**Cons:** Requires querying across N projects. UX for drill-down adds complexity.

**Why rejected for MVP:** Tracked in #131. Per-project is consistent with all existing
screens.

### Alternative 4: Rich charts (bar, trend line, burndown)

Additional charts showing jobs completed over time, time-in-status distributions,
approval turnaround rates.

**Pros:** Deeper operational insight.

**Cons:** Requires time-series data not present in the current schema. Implies a
project-management / analytics mindset that contradicts OpsClear's "truth today,
not trends" philosophy.

**Why rejected:** A single donut chart answers the relevant question (what is the
current status breakdown?) without adding schema complexity or analytical overhead.
Trend charts are a future consideration once the data model matures.

### Alternative 5: Real-time dashboard via SSE

Push updates to the client on every mutation.

**Pros:** Always current.

**Cons:** Significant infrastructure. Tracked in #80 (future).

**Why rejected:** 30s staleTime + refetch-on-focus is sufficient for 5–50 users.

---

## Consequences

### Positive

- Single endpoint gives the dashboard a fast, waterfall-free load
- Service-to-service delegation is bounded, documented, and avoids logic duplication
- Donut chart gives instant visual health signal with zero additional API data
- Sections hidden when empty keep the dashboard clean for healthy projects
- 30s staleTime + refetch-on-focus keeps data fresh without polling

### Negative

- `DashboardService` breaks the service layering rule — must be clearly documented
  so it is not used as a precedent for ad-hoc service coupling elsewhere
- Mutations across jobs, approvals, and status changes must all invalidate
  `['dashboard', projectId]` — a convention that must be enforced consistently
- Adding Recharts adds ~100 kB to the bundle (gzip); acceptable for one chart

### Neutral

- `JobSummary` DTO is a small mapping addition; `pendingApprovals` reuses
  `ApprovalResponse` — no second new DTO needed
- MEMBER scoping is inherited from existing service methods — no new access-control
  logic in `DashboardService`

---

## References

- [ADR-0005: Roles and Permissions Model](./0005-roles-and-permissions.md)
- [ADR-0007: Job Model and Status Flow](./0007-job-model-and-status-flow.md)
- [ADR-0010: Approval Model](./0010-approval-model.md)
- [ADR-0011: Frontend Architecture](./0011-frontend-architecture.md)
- [ADR-0013: Projects Screens](./0013-projects-screens.md)
- [ADR-0014: Jobs Screens](./0014-jobs-screens.md)
- [ADR-0016: Approvals Screens](./0016-approvals-screens.md)
- [#131: Cross-project pending approval queue](https://github.com/Jovan42/OpsClear/issues/131)
