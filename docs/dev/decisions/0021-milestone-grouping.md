# ADR-0021: Milestone Grouping for Jobs

**Status:** Accepted
**Date:** 2026-03-16
**Author:** OpsClear Team

## Context

As projects grow, a flat list of jobs becomes hard to navigate. Teams need a lightweight grouping layer to organise work into logical chunks (e.g. "Backend API", "Sprint 1", "Phase 2") without introducing the complexity of sub-projects or task hierarchies.

Two structural approaches were considered: a dedicated `milestones` table (Option A) or reusing an existing free-text `group` field on jobs (Option B).

## Decision

Introduce a `milestones` table and a nullable `milestone_id` FK on `jobs`.

```
Project
  ├── Milestone: "Backend API"
  │     ├── Job: Setup auth
  │     └── Job: Create endpoints
  └── Job: Ungrouped job   (milestone_id = null)
```

- `Milestone` has: `id`, `project_id`, `name`, optional `description`, optional `deadline`, `created_at`, `deleted_at` (soft delete)
- `Job.milestone_id` is nullable — jobs without a milestone belong directly to the project
- No nested milestones; milestones inherit project membership and roles
- Milestone CRUD lives at `/api/projects/:id/milestones`
- Job list supports an optional `?milestoneId=` filter
- Frontend: milestone selector in create/edit job modal; grouped view in job list (jobs grouped by milestone, ungrouped last)

## Alternatives Considered

### Option B — Free-text group label on jobs

Add a `group` VARCHAR column directly on `jobs` with no dedicated table.

**Pros:**
- Zero schema join complexity
- No migration to `milestones` table needed

**Cons:**
- No shared metadata (description, deadline) per group
- Group names can drift across jobs (typos, inconsistent casing)
- Cannot be renamed in one operation — requires updating every job in the group
- Cannot be soft-deleted or completed independently

**Why rejected:** The lack of a canonical record per milestone means group names drift and there is no place to attach deadline or description. A dedicated table costs little and enables future milestone-level status tracking.

### Option C — Tags / labels (many-to-many)

Allow jobs to carry multiple free-form tags.

**Why rejected:** Many-to-many adds join complexity with limited benefit for the primary use case (linear sprint-style grouping). Can be added later independently.

## Consequences

### Positive

- Jobs can be organised into named milestones with optional deadlines
- Milestone deadline gives a natural overdue signal at the group level
- Renaming a milestone renames it for all jobs in one operation
- Ungrouped jobs remain fully supported (nullable FK, no forced grouping)

### Negative

- One additional table and FK to maintain
- Job create/edit form gains an optional milestone selector (small UX cost)
- Deleting a milestone must decide what to do with its jobs — chosen: set `milestone_id = NULL` (ungroup, not cascade)

### Neutral

- Existing jobs default to `milestone_id = NULL` — no data migration required
- Milestone membership does not affect permissions (inherits from project)

## Implementation Notes

**Migrations**

```sql
-- V005__milestones.sql
CREATE TABLE milestones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    deadline    DATE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_milestones_project ON milestones (project_id) WHERE deleted_at IS NULL;

ALTER TABLE jobs
    ADD COLUMN milestone_id UUID REFERENCES milestones(id) ON DELETE SET NULL;
```

**Backend**

- `Milestone` model + `MilestoneRepository` (soft delete pattern)
- `MilestoneService`: create, list, update, softDelete — OWNER/ADMIN only for mutations
- `MilestoneController`: `GET/POST /api/projects/:id/milestones`, `PUT/DELETE /api/projects/:id/milestones/:mid`
- `JobService.create` / `update`: accept optional `milestoneId`; validate it belongs to the same project
- `JobRepository.findByProject`: add optional `milestoneId` filter parameter
- `JobResponse`: include `milestoneId` and `milestoneName`

**Frontend**

### 1. Milestone management — ProjectSettingsPage

Milestones are managed in a new **Milestones** section of `ProjectSettingsPage` (OWNER/ADMIN only), following the same pattern as Block Reasons:

- List of milestones showing name, optional deadline, and a Remove button
- "Add milestone" inline form: name (required, max 100 chars), optional description, optional deadline (`<input type="date">`)
- Edit is done inline (name + deadline editable in place)
- Soft delete with a confirmation prompt; jobs in the deleted milestone become ungrouped

No dedicated milestones page — the settings page is the natural home for project-level configuration.

### 2. Job list — grouped view

When a project has at least one milestone the job list switches to a **grouped layout** by default:

```
▼ Backend API  (5 jobs)  · deadline: 31 Mar 2026
  [job row]
  [job row]
  ...

▼ Sprint 2  (3 jobs)
  [job row]
  ...

▼ Ungrouped  (2 jobs)
  [job row]
  ...
```

- Each milestone group has a collapsible header showing: milestone name, job count badge, and deadline (red if overdue)
- Groups are sorted by `deadline ASC` (no deadline last), then by creation order
- **Ungrouped** section always appears last; hidden if empty
- The existing flat view (no grouping) is preserved when the project has no milestones
- A **"Grouped / Flat"** toggle button in the toolbar lets the user switch to the flat table view even when milestones exist

### 3. Milestone filter

A **milestone dropdown** is added to the job list filter toolbar alongside the existing priority dropdown:

```
[Search…]  [Priority ▾]  [Milestone ▾]
```

- Options: "All milestones" (default), then each milestone by name, then "Ungrouped"
- Selecting a milestone filters the job list to that milestone only (both in grouped and flat view)
- Filter is client-side (milestones already fetched)

### 4. Milestone badge on job rows/cards

When the flat view is active (or when filtering across milestones), each job row/card shows a subtle **milestone chip** (name only, gray) to the right of the title — similar to how priority badge is shown. Hidden when no milestone is assigned.

### 5. Job create / edit modal

- A **Milestone** optional dropdown appears in `NewJobModal` when the project has at least one milestone
- Options: "None" (default) + each active milestone by name
- Pre-selected when creating from within a milestone group view

### 6. TanStack Query key conventions — milestones domain

| Key | Usage |
|-----|-------|
| `['milestones', projectId]` | Milestone list for a project |

Mutations invalidate `['milestones', projectId]`. The job list (`['jobs', projectId]`) is also invalidated when a milestone is deleted (jobs may have shifted to ungrouped).

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/projects/:id/milestones` | List active milestones |
| POST | `/api/projects/:id/milestones` | Create milestone (OWNER/ADMIN) |
| PUT | `/api/projects/:id/milestones/:mid` | Update milestone (OWNER/ADMIN) |
| DELETE | `/api/projects/:id/milestones/:mid` | Soft delete milestone (OWNER/ADMIN) |

## References

- [Issue #187 — feat(projects): milestone grouping for jobs within a project](https://github.com/Jovan42/OpsClear/issues/187)
- ADR-0007 — Job model and status flow
- ADR-0014 — Jobs screens
