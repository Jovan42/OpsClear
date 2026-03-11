# ADR-0020: Job Priority

**Status:** Accepted
**Date:** 2026-03-11
**Author:** OpsClear Team

## Context

As project job lists grow, teams need a way to signal urgency — which jobs should be worked on first. Without priority, everything appears equally important and managers can't communicate what is critical.

Two approaches were considered: a structured priority label (enum) or a free-form manual ordering (drag-and-drop).

## Decision

Add a `priority` enum field to `jobs`: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.

- Default value: `MEDIUM`
- Stored as `VARCHAR(10)` with a `CHECK` constraint on the DB
- Exposed on `JobResponse`, accepted on `CreateJobRequest` / `UpdateJobRequest`
- Job list supports filtering by `?priority=HIGH` (combined with existing `?q=` search)
- Default sort order: `priority DESC` (CRITICAL first), then `status` (BLOCKED first), then `created_at DESC`

**Why not manual ordering (Option B):**
- Drag-and-drop is complex to implement correctly (position integers, gap management, concurrent edits)
- Breaks on mobile — touch drag-and-drop requires significant extra work
- Can be layered on top of Option A later if needed

## Alternatives Considered

### Option B — Manual drag-and-drop ordering

Store a `position` integer per job, allow reordering via drag-and-drop in the UI.

**Pros:**
- Fully custom order, maximum flexibility

**Cons:**
- Complex position management (integer gaps, re-indexing)
- Poor mobile experience
- Concurrent edit conflicts

**Why rejected:** Too complex for the value gained at this stage. Priority enum covers 90% of the use case with a fraction of the implementation effort.

## Consequences

### Positive

- Teams can immediately communicate urgency without training
- CRITICAL jobs are visually distinct and sort to the top
- Filterable — manager can view only HIGH/CRITICAL jobs
- Mobile-friendly (dropdown selector, no drag-and-drop)

### Negative

- Priority is subjective — teams need to agree on what CRITICAL means for them
- One more field to fill in on job create/edit (mitigated by default of MEDIUM)

### Neutral

- Existing jobs will default to `MEDIUM` via migration `DEFAULT`
- Search (`?q=`) and priority filter (`?priority=`) stack independently

## Implementation Notes

**Migration**
```sql
ALTER TABLE jobs
  ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM'
    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));
```

**Backend**
- `JobPriority` enum: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`
- `CreateJobRequest` / `UpdateJobRequest`: optional `priority` field, defaults to `MEDIUM` when absent
- `JobResponse`: include `priority`
- `JobController.list`: add `@RequestParam(required = false) JobPriority priority` — filter in service/repo when present
- Default sort: priority weight desc → status weight → created_at desc (applied in repository)

**Frontend**
- Priority badge component (colour-coded: LOW=gray, MEDIUM=blue, HIGH=orange, CRITICAL=red)
- Priority column in job list table; badge in mobile card
- Priority selector in create/edit job modal
- Priority filter tab row (alongside status filter tabs) or combined filter toolbar
- Default sort reflects backend default (CRITICAL first)

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`)
- [ ] Add example requests for priority filter endpoint
- [ ] Test priority filter combined with search (`?q=&priority=`)

## References

- [Issue #189 — feat(jobs): job priority and ordering](https://github.com/Jovan42/OpsClear/issues/189)
- ADR-0007 — Job model and status flow
- ADR-0014 — Jobs screens
