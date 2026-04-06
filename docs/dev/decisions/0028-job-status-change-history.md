# ADR-0028: Job status change history

**Status:** Accepted
**Date:** 2026-04-06
**Author:** Jovan Manojlovic

## Context

There is no record of when a job status changed or who changed it. Notes
provide an informal audit trail but cannot answer: when did this job get
blocked? How long was it in progress? Who moved it to completed?

This is high value for managers who need to understand job lifecycle and
identify bottlenecks — the core promise of OpsClear.

## Decision

Introduce an append-only `job_status_history` table that records every
status transition, including job creation as the first entry.

### Data model

```
job_status_history
├── id           UUID PK
├── job_id       UUID FK → jobs(id)
├── changed_from VARCHAR (nullable — null on job creation)
├── changed_to   VARCHAR NOT NULL
├── changed_by   UUID FK → users(id) (nullable — future system-generated transitions)
├── changed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
└── block_reason VARCHAR (nullable — populated only when changed_to = BLOCKED)
```

Append-only. No update or delete operations. No soft delete.

### First entry

Job creation is recorded as the first history entry:

| Field          | Value |
|----------------|-------|
| `changed_from` | null  |
| `changed_to`   | NEW   |
| `changed_by`   | user who created the job |
| `block_reason` | null  |

This gives a complete picture of the job lifecycle from day zero without
needing to infer the initial state from the job record itself.

### Block reason

When a job transitions to `BLOCKED`, the block reason text is copied into
`block_reason` on the history entry. This makes the history self-contained —
no need to cross-reference the current job record to understand what happened
at a given point in time.

### Changed by

Always set for user-initiated transitions. Nullable to support future
system-generated transitions (e.g. deadline passed → auto-blocked). For all
current use cases this will always be populated.

### Bulk status changes

Not in scope. When bulk transitions are introduced, each job receives its own
independent history entry regardless of how the change was triggered.

### Duration calculation

Raw timestamps are returned by the API. Duration per status (e.g. time spent
`IN_PROGRESS` before `BLOCKED`) is calculated on the frontend — keeps the API
simple and lets the frontend format durations according to user preferences.

### API

```
GET /api/projects/{projectId}/jobs/{jobId}/history
```

Returns a flat array of history entries ordered by `changed_at` ascending.
No pagination — a job will never accumulate a large number of status changes.

### UI

Collapsible section on the job detail page, collapsed by default. Kept
separate from the notes section — notes remain an independent audit trail
for free-form comments and agreements.

### Interaction with notes

Status history and notes serve different purposes and are displayed separately:

| | Status history | Notes |
|--|----------------|-------|
| **Author** | System-recorded | User-written |
| **Content** | Structured transition data | Free-form text |
| **Mutability** | Append-only, no edits | Append-only, no edits |
| **Purpose** | When/who changed status | Comments and agreements |

No unified timeline for now.

## Alternatives Considered

### Alternative 1: Derive history from notes

Require users to add a note when changing status — reconstruct history from
note timestamps.

**Pros:**
- No new table

**Cons:**
- Notes are optional — history would be incomplete
- Mixing structured transition data with free-form comments creates a messy UX
- Cannot reliably answer "how long was this blocked?"

**Why rejected:** Unreliable and conflates two distinct concepts.

### Alternative 2: Store history as a JSONB column on the job

Append transition events to a `history JSONB[]` column on `jobs`.

**Pros:**
- No separate table
- Atomic with the job row

**Cons:**
- No FK integrity on `job_id`, `changed_by`
- Cannot index or query individual transitions efficiently
- JSONB array grows unboundedly on the job row

**Why rejected:** Loses relational integrity and query flexibility for minimal gain.

### Alternative 3: Unified activity timeline (history + notes)

Display status changes and notes in a single chronological feed.

**Pros:**
- Single surface for full job activity
- Familiar pattern (GitHub, Linear)

**Cons:**
- More complex to implement and render
- Forces two different data shapes into one component
- Can be introduced later without changing the data model

**Why rejected:** Premature. The data model does not prevent a unified timeline
in future — this is a UI decision that can be deferred.

## Consequences

### Positive

- Managers can answer "when did this get blocked?" and "how long was it in progress?"
- History is self-contained — block reason is stored inline at the time of transition
- Foundation for future analytics (average time per status, bottleneck detection)

### Negative

- Every status change (including job creation) writes an additional row
- `JobService` must be updated to insert history entries on create and on every status transition

### Neutral

- Notes are unchanged — they remain a separate, independent audit trail
- No changes to existing job response shape (history is a separate endpoint)

## Implementation Notes

1. Flyway migration: `job_status_history` table
2. `JobStatusHistoryModel` — plain model, no soft delete
3. `JobStatusHistoryRepository` — `insert()` and `findByJobId()` (ordered by `changed_at` ASC)
4. Update `JobService.create()` — insert first history entry (`null → NEW`)
5. Update `JobService.updateStatus()` — insert history entry on every transition
6. `JobHistoryController` — `GET /api/projects/{projectId}/jobs/{jobId}/history`
7. Frontend — collapsible `StatusHistory` section on job detail, collapsed by default

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`)
- [ ] Add example requests for new endpoints
- [ ] Update environment variables if needed
- [ ] Test the flow manually before marking complete

## References

- ADR-0007: Job model and status flow
- ADR-0008: Blocking model (block reason stored on job — copied to history on BLOCKED transition)
- ADR-0009: Notes model (separate append-only audit trail for free-form comments)
