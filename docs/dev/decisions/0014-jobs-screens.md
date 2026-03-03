# ADR-0014: Jobs Screens

**Status:** Accepted
**Date:** 2026-03-03
**Author:** Jovan Manojlovic

## Context

Module 7.4 covers the job-related screens for a single project:

1. **Job list** — shows all jobs in a project with status filtering and a way to create
   a new job.
2. **Create / edit job form** — title, description, client, assigned member, deadline.
3. **Job detail** — full job information, status controls, block/unblock, notes, and
   approval request button (detail + inline actions are covered together here and in
   ADR-0015).

Key decisions: list layout (table vs cards), filter mechanism, create/edit surface
(modal vs page), navigation model, and TanStack Query key conventions for the jobs domain.

---

## Decision

### 1. Job list layout

Jobs are rendered in a **full-width table** with one row per job. A card grid (used for
projects) is less appropriate here because jobs have more scannable columns: title,
client, assignee, deadline, and status.

Columns:

| Title | Client | Assigned to | Deadline | Status |
|-------|--------|-------------|----------|--------|

- The **entire row** is clickable and navigates to the job detail page.
- Rows that are `BLOCKED` get a subtle red left border to draw attention.
- The table is not paginated for MVP (small teams, ≤ ~50 active jobs per project).

### 2. Status filter tabs

A row of filter tabs sits above the table:

```
All (12)  |  New (3)  |  In Progress (5)  |  Blocked (2)  |  Completed (2)
```

- Filtering is client-side (all jobs are already fetched).
- Each tab shows a count badge.
- When count is **0** the tab is muted (gray text, gray badge) and still clickable but
  visually de-emphasised.
- When count is **> 0** the badge uses the status colour:
  - New → gray
  - In Progress → blue
  - Blocked → red
  - Completed → green
- Default tab is **All**.

### 3. Create job — modal

A **"+ New Job"** button opens a modal (same pattern as New Project). This avoids a
full-page navigation for a simple form.

Form fields (RHF + Zod):
- `title` — required, max 255 chars
- `client` — optional, max 255 chars
- `assignedTo` — optional, member search dropdown (same email-typeahead pattern as
  AddMemberForm, but scoped to project members)
- `deadline` — optional, date input (`<input type="date">`)

Edit reuses the same modal component with pre-filled values.

### 4. Edit job — slide-in or same modal

Edit is accessible from the job detail page (not inline on the list). The list is
read-only; editing requires navigating to the detail view.

### 5. Navigation model

- Card/row click → `/projects/:projectId/jobs/:jobId` (job detail)
- Back button in `AppLayout` → `navigate(-1)`
- No breadcrumb for MVP

### 6. TanStack Query key conventions — jobs domain

| Key | Usage |
|-----|-------|
| `['jobs', projectId]` | Job list for a project |
| `['jobs', projectId, jobId]` | Single job detail |

Mutations invalidate `['jobs', projectId]`. Delete also calls
`removeQueries({ queryKey: ['jobs', projectId, jobId] })` before invalidating the list
(same pattern as project delete) to avoid refetch-after-delete requests.

### 7. Assign member UX

The `assignedTo` field in create/edit uses a typeahead scoped to project members
(fetched from `GET /api/projects/:projectId/members`) — no extra API endpoint needed,
members are already cached from the settings page.

---

## Consequences

- Table layout works best on desktop; on mobile it scrolls horizontally.
- Client-side filtering means all jobs are fetched upfront — acceptable for MVP scale.
- Reusing the modal pattern keeps the screen count low and navigation simple.
