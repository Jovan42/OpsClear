# ADR-0016: Approvals Screens

**Status:** Accepted
**Date:** 2026-03-09
**Author:** Jovan Manojlovic

## Context

Module 7.5 covers the approval queue page — a dedicated screen for OWNER and ADMIN users to
review all pending approval requests across a project in one place, without having to navigate
into individual job detail pages.

The approve/reject modal (`ApprovalDecisionModal`) was already implemented as part of ADR-0015
(job detail inline actions). This ADR covers the queue page itself: layout, grouping, ordering,
access guard, nav badge, and conflict handling.

The backend provides `GET /api/projects/:projectId/approvals/pending` which returns a flat list
of `ApprovalResponse` items (the same DTO used for per-job approval endpoints). Each item
includes `jobId`, `jobTitle`, `requesterId`, `description`, and `requestedAt` — enough to render
a grouped view without additional requests. The frontend narrows this to a `PendingApprovalResponse`
type that omits decision-related fields (`approverId`, `comment`, `decidedAt`) since the queue
only shows pending items.

---

## Decision

### 1. Route and access guard

The approval queue lives at `/projects/:projectId/approvals`.

**Access guard:** Only OWNER and ADMIN may view this page. A MEMBER navigating to this URL is
**redirected to the job list** (`/projects/:projectId/jobs`). The guard uses the same
`useProjectRole(projectId)` hook used in ADR-0013 and ADR-0015. The backend enforces the same
restriction independently.

A MEMBER has no use for the queue page: they cannot decide approvals and the page shows nothing
actionable to them. Redirecting silently is preferable to showing an "Access Denied" error — the
MEMBER simply lands on the page they would have gone to anyway.

### 2. Layout — grouped by job, oldest-first

The page renders pending approvals **grouped by job**, each group headed by the job name:

```
┌──────────────────────────────────────────────┐
│  Approvals  (5 pending)                       │
├──────────────────────────────────────────────┤
│  Install transformer — Unit 4         [→ Job] │
│  ┌─────────────────────────────────────────┐ │
│  │ Need to purchase transformer — €800     │ │
│  │ Requested by Jane Doe · Mar 3, 09:15   │ │
│  │                      [Reject] [Approve] │ │
│  └─────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────┐ │
│  │ Approve overtime for weekend shift      │ │
│  │ Requested by Jane Doe · Mar 4, 14:02   │ │
│  │                      [Reject] [Approve] │ │
│  └─────────────────────────────────────────┘ │
├──────────────────────────────────────────────┤
│  Replace HVAC unit — Building B       [→ Job] │
│  ┌─────────────────────────────────────────┐ │
│  │ Order replacement compressor           │ │
│  │ Requested by Alex Smith · Mar 5, 11:30 │ │
│  │                      [Reject] [Approve] │ │
│  └─────────────────────────────────────────┘ │
└──────────────────────────────────────────────┘
```

Within each job group, approvals are sorted **oldest-first** (`requestedAt` ascending) — the
backlog is processed in the order requests arrived.

Jobs are sorted by the **oldest request across all their pending approvals**, so the job with
the longest-waiting approval floats to the top of the page.

Each job group header shows a **`→ Job`** link that navigates to the job detail page
(`/projects/:projectId/jobs/:jobId`), allowing the OWNER/ADMIN to review the full job context
before deciding.

**Empty state:** If there are no pending approvals, the page shows:
```
All caught up — no pending approvals.
```

### 3. Flat list vs grouped-by-job

**Flat list** (all cards in one stream, ordered by `requestedAt`) was the alternative.

**Why grouped was chosen:**

- Approvals within the same job share context — seeing them together helps the owner decide
  consistently (e.g., not approving a purchase and rejecting a related one from the same job).
- Grouping naturally exposes how many requests are outstanding per job, making it easy to spot
  jobs generating disproportionate approval traffic.
- `PendingApprovalResponse` already includes `jobTitle`, so grouping requires no extra API calls.

### 4. Nav badge — pending count on project nav

The approval queue link in the project navigation bar (shown when inside a project context) displays
a **numeric badge** with the count of pending approvals:

```
 Jobs   Approvals ⓝ   Settings
```

The badge is driven by the same `['approvals', projectId, 'pending']` query used by the queue
page. When the queue page is mounted, the count is already fetched. When the user is on a
different project page, the query is fetched on mount of the nav component with a
**5-minute `staleTime`** — stale enough to avoid hammering the API on every page transition,
fresh enough to reflect decisions made by co-admins during a session.

The badge is hidden (not shown as "0") when there are no pending approvals.

### 5. Concurrent conflict handling

When two OWNER/ADMIN users are both on the approval queue and one decides an approval while
the other still has the modal open, the second user's `PATCH` will receive a **409 Conflict**
from the backend (per ADR-0010).

On 409, the decision modal:
1. Closes itself.
2. Invalidates `['approvals', projectId, 'pending']` to refresh the list.
3. Shows a **toast notification**: "This approval was already decided by another user."

The card for the decided approval disappears from the list on the next render (it is no longer
pending), giving the second user immediate visual confirmation of the resolution.

No retry or merge logic is attempted — the decision has already been made.

### 6. Approve / Reject modal

The `ApprovalDecisionModal` component built in ADR-0015 is reused directly. It accepts
`projectId`, `jobId`, `approvalId`, and `decision` props. The queue page passes these from
the `PendingApprovalResponse` item the user clicked.

On success, the mutation invalidates `['approvals', projectId, 'pending']`, which removes the
decided card from the queue immediately.

**Optimistic update:** The approval card is removed from the local list immediately on submit
(optimistic), then confirmed when the query re-fetches. On error (including 409), the card
reappears and the conflict toast is shown.

### 7. TanStack Query key conventions

Extending conventions from ADR-0014 and ADR-0015:

| Key | Usage |
|-----|-------|
| `['approvals', projectId, 'pending']` | All pending approvals for the queue page and nav badge |

Mutations:
- Approve / Reject from queue page → invalidate `['approvals', projectId, 'pending']`
  and `['jobs', projectId, jobId, 'approvals']` (job detail stays consistent if open)

### 8. Feature folder structure

```
features/approvals/
├── ApprovalQueuePage.tsx     # queue page — grouped list, empty state
└── useApprovalQueue.ts       # useApprovalQueue(projectId) — wraps listPending
```

`ApprovalDecisionModal` stays in `features/jobs/components/` — it is equally used from the
job detail page and the queue page, and its mutation scope is always job-scoped.

---

## Alternatives Considered

### Alternative 1: Flat list, oldest-first (no grouping)

Render all pending approvals in a single chronological stream regardless of which job they
belong to.

**Pros:** Simpler render logic; strict FIFO processing for the operator.

**Cons:** Loses job context — an operator cannot tell at a glance whether two requests are
related. Long lists become difficult to scan without a visual anchor.

**Why rejected:** The `PendingApprovalResponse` already carries `jobTitle`, making grouping
trivial. The contextual benefit outweighs the marginal implementation complexity.

### Alternative 2: Approval queue as a modal / drawer overlay

Open the approval queue as a slide-over drawer triggered from a nav badge click rather than
navigating to a full page.

**Pros:** Never leaves the current context; feels lighter.

**Cons:** Drawers have limited vertical space — a project with many pending approvals would
require scrolling inside a scroll, which is awkward. Sharing a direct URL to the queue is also
not possible. The `→ Job` link navigating away from a drawer creates a confusing UX.

**Why rejected:** A full page is simpler to implement, shareable by URL, and gives the queue
the visual weight it deserves as a primary operational workflow.

### Alternative 3: Show all decisions (APPROVED / REJECTED) in the queue, not just PENDING

Display a full history of all approvals in the queue, with a "Pending only" toggle.

**Pros:** Full audit trail visible in one place.

**Cons:** Decided approvals are already visible on each job's detail page. Mixing them into the
queue page clutters the action-oriented view and buries the items that actually need attention.

**Why rejected:** The queue page is an action surface, not a history log. Decided approvals
belong on the job detail page where they have full context.

---

## Consequences

### Positive

- OWNER/ADMIN can process all outstanding approvals without navigating into individual jobs
- Grouped layout surfaces related requests together, reducing context-switching
- Nav badge provides a passive indicator of outstanding work without requiring the user to
  visit the queue page
- Reusing `ApprovalDecisionModal` from ADR-0015 adds zero new UI complexity for the decision flow

### Negative

- Nav badge query adds a background fetch on project-context pages; mitigated by 5-minute
  `staleTime`
- Optimistic removal of decided cards means a brief flash if the server returns an error and
  the card reappears

### Neutral

- `useApprovalQueue` is a thin wrapper over `approvalsApi.listPending` — no new API surface

---

## References

- [ADR-0005: Roles and Permissions Model](./0005-roles-and-permissions.md)
- [ADR-0010: Approval Model](./0010-approval-model.md)
- [ADR-0011: Frontend Architecture](./0011-frontend-architecture.md)
- [ADR-0013: Projects Screens](./0013-projects-screens.md)
- [ADR-0014: Jobs Screens](./0014-jobs-screens.md)
- [ADR-0015: Job Detail and Inline Actions](./0015-job-detail-and-inline-actions.md)
