# ADR-0010: Approval Model

**Status:** Accepted
**Date:** 2026-03-01
**Author:** Jovan Manojlovic

## Context

OpsClear needs a lightweight mechanism for a team member to signal "I need a decision on
this job" without prescribing what action should follow. This is distinct from blocking
(which records that work is stuck) — approval is a request for a managerial decision:
proceed, authorise a purchase, authorise leaving a site, sign off on a result.

A single job can require several independent approvals simultaneously. For example:
- "Need to purchase replacement transformer — €800" (waiting on OWNER)
- "Need to close road access for 2 hours" (waiting on ADMIN)

Both can be outstanding at the same time, concerning the same job, requiring separate decisions.

The core product promise is *"What's the truth about our work TODAY?"* — the approval queue
on the dashboard answers: "What is waiting on me right now, and why?"

### Requirements

- A MEMBER can request approval on their own assigned job
- OWNER and ADMIN can request approval on any job in the project
- Every request has a mandatory description — what specifically needs approval
- Multiple approval requests can be pending for the same job simultaneously
- The request lands in the OWNER/ADMIN dashboard queue
- Any OWNER or ADMIN can approve or reject any pending request
- The decision records who decided, when, and optionally why (comment)
- The decision is permanent — no editing or deleting an approval record
- Approval does not automatically change job status — it is a separate signal
- The dashboard derives "has pending approvals" from the presence of `PENDING` records

---

## Decision

### Schema

```sql
CREATE TABLE approvals (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID         NOT NULL REFERENCES jobs(id),
    requester_id UUID         NOT NULL REFERENCES users(id),
    approver_id  UUID         REFERENCES users(id),
    description  VARCHAR(500) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    comment      VARCHAR(1000),
    requested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at   TIMESTAMP,

    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_approvals_job_id ON approvals(job_id);
CREATE INDEX idx_approvals_status ON approvals(status) WHERE status = 'PENDING';
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `job_id` | UUID (FK) | The job this approval concerns |
| `requester_id` | UUID (FK) | The user who requested approval |
| `approver_id` | UUID (FK) | The user who approved/rejected — `NULL` while `PENDING` |
| `description` | VARCHAR(500) | What specifically needs approval — required |
| `status` | VARCHAR(20) | `PENDING`, `APPROVED`, or `REJECTED` |
| `comment` | VARCHAR(1000) | Optional decision comment from approver |
| `requested_at` | TIMESTAMP | Set by DB default — never passed by the application |
| `decided_at` | TIMESTAMP | Set when the decision is made — `NULL` while `PENDING` |

**No `deleted_at` column.** Approval records are permanent — consistent with notes. Soft-deleting
an approval decision would undermine the audit trail.

### Multiple pending approvals per job

A job can have any number of `PENDING` approvals simultaneously. Each approval represents a
distinct, independently decidable request — buying parts and closing road access are unrelated
decisions that should not block each other.

The `description` field is what differentiates them and gives the approver context without
needing to read the full job history.

### Description is required

Every approval request must state what needs approval. Without it, the dashboard queue shows
"Job X needs approval" with no context — the owner must navigate to the job and read the notes
before they can act. With a description, the queue is actionable at a glance:

> "Install panel — Need to purchase replacement transformer — €800"

### Concurrent decision handling

Multiple OWNER/ADMIN users can see the same pending approval in their dashboard simultaneously.
Without a guard, two concurrent `PATCH /status` requests could both pass the `status = 'PENDING'`
check and both write — the last write wins silently, potentially overwriting a rejection with an
approval or vice versa.

The fix is an atomic `UPDATE ... WHERE status = 'PENDING'` in the repository:

```sql
UPDATE approvals
SET status      = ?,
    approver_id = ?,
    comment     = ?,
    decided_at  = ?
WHERE id     = ?
  AND status = 'PENDING'
```

The database guarantees only one concurrent writer can transition the row out of `PENDING`.
`ApprovalRepository.updateDecision()` returns the number of rows affected:

- `1` — success; the caller won the race
- `0` — someone else already decided it; service throws `ConflictException` ("This approval
  has already been decided")

No extra columns, no optimistic locking version field, no distributed locks — the `WHERE`
clause is sufficient because the `UPDATE` is atomic at the database level.

### Approver field: filled on decision

`approver_id` is `NULL` when the approval is `PENDING`. It is filled by whoever approves or
rejects the request. There is no pre-designation — any OWNER or ADMIN in the project can act
on any pending approval. First-come, first-served prevents bottlenecks when a named approver
is unavailable.

### Status enum

```java
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
```

### API

| Method | Path | Description | Access |
|--------|------|-------------|--------|
| `POST` | `/api/projects/{projectId}/jobs/{jobId}/approvals` | Request approval | MEMBER (own job), OWNER, ADMIN |
| `PATCH` | `/api/projects/{projectId}/jobs/{jobId}/approvals/{approvalId}/status` | Approve or reject | OWNER, ADMIN |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/approvals` | Approval history for a job | Any member |
| `GET` | `/api/projects/{projectId}/approvals/pending` | All pending approvals in project | OWNER, ADMIN |

Approve and reject share one endpoint — consistent with `PATCH /jobs/{jobId}/status` for
job status changes. A single endpoint reduces API surface and makes the pattern predictable
across the codebase.

#### Request Approval

```json
{ "description": "Need to purchase replacement transformer — €800" }
```

`description` is required (`@NotBlank`, max 500 characters).

#### Approve or Reject

```json
{ "status": "APPROVED", "comment": "Approved — order within budget." }
```

`status` must be `APPROVED` or `REJECTED` — transitions to `PENDING` are not allowed via
this endpoint. `comment` is optional in both cases.

#### Approval Response (single)

```json
{
  "id": "...",
  "jobId": "...",
  "requesterId": "...",
  "approverId": null,
  "description": "Need to purchase replacement transformer — €800",
  "status": "PENDING",
  "comment": null,
  "requestedAt": "2026-03-01T09:00:00Z",
  "decidedAt": null
}
```

#### Pending Approvals Response

`GET /api/projects/{projectId}/approvals/pending` returns a flat list ordered by
`requested_at ASC` (oldest first — longest waiting appears first). Each entry includes
the job title and the approval description so the dashboard is actionable without further
navigation:

```json
[
  {
    "id": "...",
    "jobId": "...",
    "jobTitle": "Install electrical panel",
    "requesterId": "...",
    "description": "Need to purchase replacement transformer — €800",
    "requestedAt": "2026-03-01T09:00:00Z"
  },
  {
    "id": "...",
    "jobId": "...",
    "jobTitle": "Install electrical panel",
    "requesterId": "...",
    "description": "Need to close road access for 2 hours",
    "requestedAt": "2026-03-01T10:15:00Z"
  }
]
```

The same job can appear multiple times — once per distinct pending approval. Only OWNER
and ADMIN can access this endpoint.

### Access Control

| Action | OWNER | ADMIN | MEMBER |
|--------|-------|-------|--------|
| Request approval | Any job | Any job | Own assigned job only |
| Approve / Reject | Any pending | Any pending | ✗ |
| View job approval history | ✓ | ✓ | ✓ |
| View pending approvals list | ✓ | ✓ | ✗ |

---

## Alternatives Considered

### Alternative 1: Separate POST /approve and POST /reject endpoints

Expose approve and reject as dedicated action endpoints rather than a shared `PATCH /status`.

**Pros:** Each endpoint body clearly signals its intent; no `status` field needed in the request.

**Cons:** Inconsistent with the existing `PATCH /jobs/{jobId}/status` pattern. Two ways to
transition state increases cognitive overhead for API consumers.

**Why rejected:** Consistency with the job status change pattern is more valuable than slightly
cleaner request bodies. `PATCH /approvals/{id}/status` mirrors `PATCH /jobs/{id}/status` exactly.

### Alternative 2: One PENDING approval per job at a time

Restrict each job to a single outstanding approval request at any time.

**Pros:** Simpler dashboard — one job, one decision outstanding; no ambiguity.

**Cons:** Real operational work frequently requires multiple independent approvals in parallel.
Forcing serialisation (request → wait → decide → request again) adds unnecessary delay and
friction. A purchase authorisation and a site access approval are completely unrelated — they
should not block each other.

**Why rejected:** The `description` field makes multiple pending approvals unambiguous. Each
entry in the dashboard queue has a clear subject line; the approver knows exactly what they
are deciding on.

### Alternative 3: Pre-designate the approver on request

Require the requester to name a specific approver when creating the request.

**Pros:** Clear ownership — one person is accountable.

**Cons:** Adds friction to the request flow. In small teams, any OWNER/ADMIN can act — forcing
a name creates bottlenecks when the named approver is unavailable.

**Why rejected:** First-come, first-served among OWNER/ADMIN is simpler and more practical
for 5-50 person teams.

### Alternative 4: Tie approvals to status transitions

Add a new job status `AWAITING_APPROVAL` — approval is a status, not a separate entity.

**Pros:** No new table; approval is visible in the job status at a glance.

**Cons:** A job can be both blocked and awaiting approval — two orthogonal states that cannot
coexist in a single status field. Multiple simultaneous approvals are impossible to model this
way.

**Why rejected:** Approval and status are orthogonal concerns. A job can be `IN_PROGRESS`
and simultaneously have several pending approval requests.

### Alternative 5: Soft-delete approvals

Follow the soft-delete pattern used elsewhere in the codebase.

**Pros:** Consistent pattern; data is recoverable.

**Cons:** Approval decisions are deliberate audit events, not accidentally created records.
Allowing soft-delete would enable hiding a decision, undermining accountability.

**Why rejected:** Same rationale as notes — the audit trail is the point.

---

## Consequences

### Positive

- Minimal schema: one table, no join tables
- Multiple independent approvals per job — no artificial serialisation
- `description` makes the dashboard queue actionable at a glance
- Dashboard "pending approvals" is a simple `WHERE status = 'PENDING'`
- Decision history is permanent and auditable
- Approval is decoupled from job status — orthogonal concerns stay separate
- Any OWNER/ADMIN can act — no bottleneck on a named approver

### Negative

- No automatic action on decision — the follow-up (changing job status, ordering parts) is
  manual; the approval is a signal, not a workflow trigger
- A requester can accumulate many open approvals on a job — no cap enforced at the DB level
  (acceptable for MVP; the description field keeps them distinguishable)

### Neutral

- `approver_id` is nullable until decided — clients must handle `null`
- `comment` is nullable — clients should not require a comment to approve or reject
- The same job can appear multiple times in the pending list — one row per pending approval

---

## Implementation Notes

- Migration: `V8__create_approvals_table.sql`
- `ApprovalStatus` enum: `PENDING`, `APPROVED`, `REJECTED`
- `ApprovalModel`: `id`, `jobId`, `jobTitle` (JOIN field, populated by project-level query),
  `requesterId`, `approverId`, `description`, `status`, `comment`, `requestedAt`, `decidedAt`
- `ApprovalRepository`:
  - `insert(jobId, requesterId, description)` → `ApprovalModel`
  - `findByIdAndJobId(approvalId, jobId)` → `Optional<ApprovalModel>`
  - `findByJobId(jobId)` → `List<ApprovalModel>` ordered `requested_at DESC`
  - `findPendingByProjectId(projectId)` → `List<ApprovalModel>` (JOINs jobs for `job_title`),
    ordered `requested_at ASC`
  - `updateDecision(id, approverId, status, comment, decidedAt)` → `int` (rows affected);
    uses `WHERE id = ? AND status = 'PENDING'` for atomicity
- `ApprovalService`:
  - `request(projectId, jobId, description, requesterId)` — guards: project exists, job in
    project, caller is member, MEMBER only on own assigned job; inserts
  - `decide(projectId, jobId, approvalId, status, comment, callerId)` — guards: OWNER or ADMIN;
    approval exists and belongs to this job; status must be APPROVED or REJECTED; uses atomic
    `updateDecision` — throws `ConflictException` if rows affected = 0 (already decided)
  - `listByJob(projectId, jobId, callerId)` — any member
  - `listPendingByProject(projectId, callerId)` — OWNER or ADMIN only

---

## References

- [ADR-0007: Job Model and Status Flow](./0007-job-model-and-status-flow.md)
- [ADR-0008: Blocking Model](./0008-blocking-model.md)
- [ADR-0009: Notes Model](./0009-notes-model.md)
- [ADR-0005: Roles and Permissions Model](./0005-roles-and-permissions.md)
