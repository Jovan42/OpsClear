# ADR-0008: Blocking Model

**Status:** Accepted
**Date:** 2026-02-27
**Author:** Jovan Manojlovic

## Context

`BLOCKED` was included in the `JobStatus` enum in Phase 3 (ADR-0007) but intentionally left
unimplemented — `validateTransition` throws immediately for any transition involving `BLOCKED`.

Blocking is a first-class operational concern for OpsClear: *"What's the truth about our work
TODAY?"* requires knowing not just that a job is stuck, but **who flagged it, why, and since when**.
A plain status change cannot capture this metadata.

### Requirements

- OWNER and ADMIN can block or unblock any job in the project
- MEMBER can block or unblock their own assigned job
- Blocking requires a mandatory reason
- The system records who blocked the job and when
- Unblocking restores the job to `IN_PROGRESS` and clears all blocking metadata
- A blocked job cannot be directly completed — it must be unblocked first
- Blocking history is not tracked per-block (notes provide the audit trail)
- The UI should offer a searchable dropdown of previously used block reasons per project,
  while still allowing free-text entry of new reasons

---

## Decision

### Block Reasons Table

Block reasons are stored in a dedicated per-project lookup table. When blocking a job, the
caller provides reason text. The service does a **find-or-create** on `project_block_reasons`
for that project and links the resulting record to the job via FK.

```sql
CREATE TABLE project_block_reasons (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    reason     VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uq_block_reason_per_project UNIQUE (project_id, reason)
);

CREATE INDEX idx_block_reasons_project ON project_block_reasons(project_id);
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `project_id` | UUID (FK) | Owning project |
| `reason` | VARCHAR(500) | Reason text — unique per project |
| `created_at` | TIMESTAMP | When this reason was first used |
| `deleted_at` | TIMESTAMP | Soft delete timestamp (NULL = active) |

**Soft delete rules:**
- `DELETE /block-reasons/{id}` performs a soft delete (sets `deleted_at`) — consistent with
  the rest of the codebase; returns `204 No Content`
- Soft-deleting a reason does **not** affect existing jobs that reference it — their
  `blocked_reason_id` FK remains intact and the reason text is still resolved on read
- A soft-deleted reason is excluded from the `GET /block-reasons` dropdown — it cannot
  be selected for new blocks
- Re-creating a reason with identical text for the same project restores (un-deletes) the
  existing record rather than inserting a duplicate (enforced by the unique constraint)

### New Columns on `jobs`

```sql
ALTER TABLE jobs
    ADD COLUMN blocked_by        UUID      REFERENCES users(id),
    ADD COLUMN blocked_reason_id UUID      REFERENCES project_block_reasons(id),
    ADD COLUMN blocked_at        TIMESTAMP;
```

| Field | Type | Description |
|-------|------|-------------|
| `blocked_by` | UUID (FK) | User (OWNER/ADMIN) who set the block |
| `blocked_reason_id` | UUID (FK) | The selected/created block reason |
| `blocked_at` | TIMESTAMP | When the block was set |

All three are `NULL` when the job is not blocked. They are cleared together on unblock.

### Updated JobModel

```java
private UUID blockedBy;
private UUID blockedReasonId;
private String blockedReason;   // resolved via JOIN — not a DB column
private Instant blockedAt;
```

`blockedReason` (the text) is populated by joining `project_block_reasons` when fetching a job,
so callers never need to resolve the FK themselves.

### Block Reasons API

A read endpoint lets the UI populate the dropdown before opening the block modal:

| Method | Path | Description | Access |
|--------|------|-------------|--------|
| `GET` | `/api/projects/{projectId}/block-reasons` | List active reasons for the project | Any member |
| `DELETE` | `/api/projects/{projectId}/block-reasons/{id}` | Soft-delete a reason | OWNER, ADMIN |

`GET /block-reasons` returns only active (non-deleted) reasons.

#### Block Reasons Response

```json
[
  { "id": "...", "reason": "Waiting for client sign-off" },
  { "id": "...", "reason": "Blocked on external dependency" }
]
```

### Status Changes via PATCH /status

Blocking and unblocking go through the existing `PATCH /api/projects/{projectId}/jobs/{id}/status`
endpoint — consistent with all other status transitions. No dedicated block/unblock endpoints.

| Method | Path | Description | Access |
|--------|------|-------------|--------|
| `PATCH` | `/api/projects/{projectId}/jobs/{id}/status` | Change job status (all transitions) | See transition table |

#### Block Request

`reason` is required when `status` is `BLOCKED`, ignored for all other transitions.

```json
{ "status": "BLOCKED", "reason": "Waiting for client to provide credentials" }
```

#### Unblock Request

```json
{ "status": "IN_PROGRESS" }
```

#### Job Response (extended)

Blocking fields are included in the existing `JobResponse` when present:

```json
{
  "id": "...",
  "status": "BLOCKED",
  "blockedBy": "550e8400-...",
  "blockedReasonId": "abc123-...",
  "blockedReason": "Waiting for client to provide credentials",
  "blockedAt": "2026-02-27T09:00:00Z",
  ...
}
```

Fields are `null` when the job is not blocked.

### Status Transition Rules (updated)

| From | To | Who | Notes |
|------|----|-----|-------|
| `NEW` | `IN_PROGRESS` | OWNER, ADMIN, assigned MEMBER | Start work |
| `IN_PROGRESS` | `COMPLETED` | OWNER, ADMIN, assigned MEMBER | Finish work |
| `IN_PROGRESS` | `BLOCKED` | OWNER, ADMIN, assigned MEMBER | `reason` required |
| `BLOCKED` | `IN_PROGRESS` | OWNER, ADMIN, assigned MEMBER | Clears blocking metadata |
| `COMPLETED` | `IN_PROGRESS` | OWNER, ADMIN | Reopen |

Invalid transitions (service throws `BadRequestException`):
- `NEW → BLOCKED`
- `BLOCKED → COMPLETED` (must unblock first)
- `COMPLETED → BLOCKED`

### UpdateStatusRequest (extended)

```java
public class UpdateStatusRequest {
    @NotNull
    private JobStatus status;

    private String reason; // required when status == BLOCKED, ignored otherwise
}
```

### JobService changes

`updateStatus` is extended to handle blocking transitions:
- `IN_PROGRESS → BLOCKED`: validates `reason` is not blank, find-or-creates the
  `project_block_reasons` record, writes `blocked_by`, `blocked_reason_id`, `blocked_at`
- `BLOCKED → IN_PROGRESS`: clears all three blocking fields

---

## Alternatives Considered

### Alternative 1: Dedicated POST /block and POST /unblock endpoints

Expose blocking as separate action endpoints outside of `PATCH /status`.

**Pros:** Block endpoint body clearly signals that `reason` is always required.

**Cons:** Inconsistent — some status changes go through `PATCH /status`, others through
dedicated endpoints. Two ways to change job status increases cognitive overhead for API consumers.

**Why rejected:** Consistency with the existing status change pattern is more valuable than
a marginally cleaner request contract.

### Alternative 2: Free-text `blocked_reason` on `jobs` (denormalized)

Keep `blocked_reason TEXT` directly on the `jobs` row; use a suggestions table only as a UX cache.

**Pros:** Simpler schema, no JOIN needed, audit trail is self-contained on the job row.

**Cons:** Duplicate strings across jobs, no reliable aggregation ("most common blockers"),
fragile `GROUP BY reason` over free text.

**Why rejected:** The FK approach gives consistent reason text, enables reliable reporting,
and still allows free-text entry of new reasons.

### Alternative 3: Separate `job_blocks` history table

Store every block/unblock event as a row in a separate table.

**Pros:** Full audit trail of every block/unblock cycle.

**Cons:** More complex queries. Notes already provide sufficient audit trail for MVP.

**Why rejected:** Over-engineered for MVP. Can be revisited post-MVP if reporting needs it.

### Alternative 4: Restrict blocking to OWNER/ADMIN only

Treat blocking as a managerial decision — only OWNER/ADMIN can set or clear `BLOCKED`.

**Pros:** Clear authority boundary; manager controls the official status of work.

**Cons:** Directly contradicts OpsClear's core promise — *"What's the truth about our work
TODAY?"* If the person doing the work cannot report a blocker, the dashboard goes stale until
a manager intervenes. The worker on-site is the first to know a job is stuck.

**Why rejected:** The MEMBER is scoped to their own assigned job only, so the permission is
narrow. Blocking is better understood as an escalation signal *from* the worker *to* the
manager, not the other way around. MEMBERs can block/unblock their own assigned job;
OWNER/ADMIN can block/unblock any job in the project.

---

## Consequences

### Positive

- All status changes go through one endpoint — consistent API surface
- Block reasons are consistent across jobs — reliable aggregation, no typo fragility
- Dashboard can query `WHERE status = 'BLOCKED'` and JOIN to show reason text and blocker name
- UI gets a searchable dropdown via `GET /block-reasons` before opening the status modal
- MEMBERs can report blockers in real time without waiting for a manager, keeping the
  dashboard truthful

### Negative

- `UpdateStatusRequest` gains a conditional field (`reason`) — validation logic depends on
  the value of `status`
- Fetching a job requires a JOIN to `project_block_reasons` to resolve reason text
- Slightly more implementation surface: `BlockReasonRepository`, `BlockReasonService`,
  `BlockReasonController`

### Neutral

- `JobResponse` gains nullable blocking fields; clients must handle `null` for non-blocked jobs
- `BLOCKED` transitions are now permitted by `validateTransition` (previously threw immediately)

---

## Implementation Notes

- Migrations:
  - `V009__create_project_block_reasons.sql` — new table
  - `V010__add_blocking_fields_to_jobs.sql` — three columns on `jobs`
- `blocked_reason_id` is NOT NULL when `status = 'BLOCKED'` — enforced at service layer
- The existing `chk_job_status` constraint already includes `'BLOCKED'` as a valid value
- Find-or-create on `project_block_reasons` uses an upsert:
  `INSERT ... ON CONFLICT (project_id, reason) DO UPDATE SET deleted_at = NULL RETURNING id`
  — also un-deletes a previously soft-deleted reason if the same text is re-entered
- `DELETE /block-reasons/{id}` always soft-deletes; existing job references are unaffected

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`)
- [ ] Add example requests for `GET /block-reasons`, `DELETE /block-reasons/{id}`,
      `PATCH /status` with `BLOCKED`, `PATCH /status` with `IN_PROGRESS` (unblock)
- [ ] Test the flow manually before marking complete

---

## References

- [ADR-0007: Job Model and Status Flow](./0007-job-model-and-status-flow.md)
- [ADR-0005: Roles and Permissions Model](./0005-roles-and-permissions.md)
