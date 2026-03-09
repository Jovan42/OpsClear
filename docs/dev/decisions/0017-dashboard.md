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
(dedicated endpoint vs frontend assembly from existing endpoints), what to show, MEMBER
vs OWNER/ADMIN visibility, and the frontend layout.

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
dashboard needs in one request. A `DashboardService` queries the database using jOOQ
and returns a single `DashboardResponse`.

**Why not assemble from existing endpoints on the frontend:**

- The job list endpoint (`GET /jobs`) returns full job records for all jobs in the
  project — the dashboard only needs a subset of fields for a subset of jobs. Fetching
  the full list and filtering client-side wastes bandwidth on larger projects.
- The pending approvals endpoint (`GET /approvals/pending`) is already scoped to the
  project — it can be reused directly inside `DashboardService` or called separately.
  For simplicity, `DashboardService` calls existing service methods internally rather
  than duplicating queries.
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
    blockedJobs:  JobSummary[],   // status = BLOCKED, oldest blocked_at first
    overdueJobs:  JobSummary[],   // deadline < now, status != COMPLETED, deadline asc
    pendingApprovals: ApprovalResponse[]  // reuses existing ApprovalResponse DTO
  }

JobSummary {
  id, title, client, assignedTo, assignedToName,
  deadline, status, blockedReason, blockedAt, blockedBy
}
```

`JobSummary` is a new lightweight DTO — a subset of `JobResponse`. It omits
`description`, `createdBy`, `createdAt`, `updatedAt`, `blockedReasonId` to keep
the payload compact.

**Counts in `summary`:** computed in `DashboardService` by grouping the full job list
by status in Java (not a separate SQL `GROUP BY` query) — the full list is already
fetched to build `blockedJobs` and `overdueJobs`, so there is no extra query cost.

**`overdueCount`** = jobs where `deadline < now` and `status != COMPLETED` (including
BLOCKED jobs — a blocked job with a missed deadline is doubly urgent).

**`pendingApprovalsCount`** = `pendingApprovals.size()` — derived from the list,
not a separate count query.

### 4. DashboardService implementation

`DashboardService` reuses existing service methods to avoid duplicating repository logic:

```
DashboardService.get(projectId, callerId):
  1. jobs = jobService.list(projectId, callerId)          // respects MEMBER visibility
  2. pending = approvalService.listPendingByProject(...)   // existing method
  3. Derive summary counts from jobs list (Java stream)
  4. Filter blockedJobs  = jobs where status == BLOCKED, sort by blockedAt asc
  5. Filter overdueJobs  = jobs where deadline < now && status != COMPLETED, sort by deadline asc
  6. Return DashboardResponse
```

No new repository methods needed for MVP — the dashboard is assembled from data already
fetched by existing service calls.

### 5. MEMBER visibility

`jobService.list()` already applies MEMBER scoping (MEMBERs only see their assigned
jobs). `DashboardService` inherits this automatically by calling through the service
layer. A MEMBER's dashboard shows:

- Summary counts scoped to their assigned jobs only
- Blocked jobs: only their assigned blocked jobs
- Overdue jobs: only their assigned overdue jobs
- Pending approvals: all pending in the project (MEMBERs can see approvals — they
  may have requested them; they just cannot decide them)

### 6. Frontend layout

The dashboard replaces the job list as the project landing page. The job list is still
accessible via the "Jobs" nav link.

```
┌────────────────────────────────────────────────────────────┐
│  Summary cards                                             │
│  ┌──────┐ ┌─────────────┐ ┌─────────┐ ┌───────────────┐  │
│  │NEW  2│ │IN PROGRESS 4│ │BLOCKED 2│ │COMPLETED    4 │  │
│  └──────┘ └─────────────┘ └─────────┘ └───────────────┘  │
│  ┌──────────────┐ ┌──────────────────┐                    │
│  │OVERDUE      1│ │PENDING APPROVALS3│                    │
│  └──────────────┘ └──────────────────┘                    │
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
│  Pending Approvals  (3)                             [→ Queue]│
│  ┌──────────────────────────────────────────────────────┐ │
│  │ Need to purchase transformer — €800                  │ │
│  │ Install transformer · Jane Doe · Mar 3               │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

- Each **summary card** is clickable — clicking a status card navigates to the job list
  pre-filtered to that status.
- Each **blocked / overdue row** has a `[→]` link to the job detail page.
- The **Pending Approvals** section shows up to 5 items with a `[→ Queue]` link to
  the full approval queue. Only shown to OWNER/ADMIN.
- Sections with zero items are **hidden entirely** — an empty dashboard shows only the
  summary cards.

### 7. Dashboard as project landing page

Navigating to `/projects/:projectId` (or clicking a project card) redirects to
`/projects/:projectId/dashboard`. The "Jobs" nav link navigates to
`/projects/:projectId/jobs` as before.

The router redirect:
```
{ path: 'projects/:projectId', element: <Navigate to="dashboard" replace /> }
```

### 8. TanStack Query key and refresh

| Key | Usage |
|-----|-------|
| `['dashboard', projectId]` | Dashboard data |

- **`staleTime`: 30 seconds** — dashboard is operational data; users expect near-realtime
  accuracy. 30s is short enough to catch updates from co-workers, long enough to avoid
  hammering the API on quick navigations away and back.
- **Refetch on window focus**: enabled (default TanStack Query behaviour) — switching
  back to the tab refreshes the dashboard.
- Mutations that change job status, block/unblock, decide approvals, or add a note
  **invalidate `['dashboard', projectId]`** alongside their existing invalidations.

### 9. Feature folder structure

```
features/dashboard/
├── DashboardPage.tsx
└── useDashboard.ts

api/
└── dashboard.ts          # dashboardApi.get(projectId)

types/index.ts            # DashboardResponse, JobSummary added
```

---

## Alternatives Considered

### Alternative 1: Frontend assembles dashboard from existing endpoints

The frontend calls `GET /jobs`, `GET /approvals/pending`, derives counts and filters
in the browser. No backend changes needed.

**Pros:** Zero backend work; reuses cached TanStack Query data.

**Cons:** Fetches full job records (including description, audit timestamps) when only
a subset is needed. Two separate requests with no coordination. As projects grow,
the over-fetch becomes meaningful.

**Why rejected:** A dedicated endpoint is a small backend addition that pays off
immediately in payload size and round-trip count. The `DashboardService` delegates
to existing services so there is almost no new logic to write.

### Alternative 2: Cross-project dashboard as the app home screen

After login, show a global dashboard across all the user's projects: total blocked,
total overdue, total pending approvals.

**Pros:** Single view of the entire operational picture; no need to navigate into a
project to see the health of all work.

**Cons:** Requires querying across N projects, which means N times the data volume.
Pagination or limits would be needed to avoid overloading the response. The UX
for drilling down ("which project is this blocked job in?") adds complexity.

**Why rejected for MVP:** Per-project scoping is consistent with all existing screens
and keeps the backend queries simple. Cross-project aggregation is tracked in #131.

### Alternative 3: Real-time dashboard via WebSockets / SSE

Push dashboard updates to the client whenever any job status changes, without requiring
a page refresh or poll.

**Pros:** Always current; no stale data window.

**Cons:** Significant infrastructure — SSE emitters or WebSocket sessions per connected
user, fan-out on every mutation. Tracked separately in #80 (future).

**Why rejected:** 30-second staleTime with refetch-on-focus gives sufficient freshness
for an operational tool used by 5–50 people. Real-time push is a polish feature, not
an MVP requirement.

---

## Consequences

### Positive

- Single endpoint gives the dashboard a fast, waterfall-free load
- `DashboardService` delegates to existing services — no new repository queries for MVP
- Sections hidden when empty keep the dashboard clean for healthy projects
- 30s staleTime + refetch-on-focus keeps data fresh without polling overhead

### Negative

- A new `JobSummary` DTO adds a small amount of mapping code alongside the existing
  `JobResponse`
- Mutations in other features must remember to also invalidate `['dashboard', projectId]`
  — a convention that needs to be followed consistently

### Neutral

- MEMBER scoping is inherited from `jobService.list()` — no additional access-control
  logic in `DashboardService`
- `pendingApprovals` on the dashboard reuses `ApprovalResponse` — no new DTO needed
  for that section

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
