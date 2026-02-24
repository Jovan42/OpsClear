# ADR-0007: Job Model and Status Flow

**Status:** Accepted
**Date:** 2026-02-24
**Author:** Jovan Manojlovic

## Context

The Job is the central entity of OpsClear. Everything else (notes, approvals, blocking) revolves around it.
The core question OpsClear answers is: *"What's the truth about our work TODAY?"*

A Job represents a unit of work within a Project. It has an owner-assigned responsible person,
an optional deadline, and a status that moves through a defined lifecycle.

### Requirements

- Jobs belong to a Project (project-scoped, row-level isolation)
- A Job has a responsible person (assigned user)
- A Job has 4 statuses: `NEW → IN_PROGRESS → BLOCKED → COMPLETED`
- Status transitions are controlled — not all transitions are valid
- Blocking is a first-class operation with reason tracking (Phase 4)
- OWNER and ADMIN see all project jobs; MEMBERs see only assigned jobs
- Soft delete (audit trail)

---

## Decision

### Job Entity

#### Database Schema

```sql
CREATE TABLE jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    client       VARCHAR(255),
    assigned_to  UUID REFERENCES users(id),
    deadline     TIMESTAMP,
    status       VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP,

    CONSTRAINT chk_job_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'))
);

CREATE INDEX idx_jobs_project    ON jobs(project_id);
CREATE INDEX idx_jobs_assigned   ON jobs(assigned_to);
CREATE INDEX idx_jobs_status     ON jobs(project_id, status) WHERE deleted_at IS NULL;
```

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `project_id` | UUID (FK) | Owning project |
| `title` | VARCHAR(255) | Short description of the work |
| `description` | TEXT | Optional longer description |
| `client` | VARCHAR(255) | Optional — who the job is for |
| `assigned_to` | UUID (FK) | Responsible person (nullable — unassigned) |
| `deadline` | TIMESTAMP | Optional due date |
| `status` | VARCHAR(20) | Current status (see below) |
| `created_by` | UUID (FK) | User who created the job |
| `created_at` | TIMESTAMP | Creation time |
| `updated_at` | TIMESTAMP | Last modification time |
| `deleted_at` | TIMESTAMP | Soft delete timestamp (NULL = active) |

#### Java Model (jOOQ plain POJO)

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobModel {
    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private String client;
    private UUID assignedTo;
    private String assignedToName;
    private Instant deadline;
    private JobStatus status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }
}
```

---

### Status Enum

```java
public enum JobStatus {
    NEW,
    IN_PROGRESS,
    BLOCKED,
    COMPLETED
}
```

### Status Transitions

```
         ┌──────────────────────────────────────────┐
         ▼                                          │
        NEW ──► IN_PROGRESS ──► COMPLETED ──► IN_PROGRESS (reopen)
                    │
                    ▼
                 BLOCKED ──► IN_PROGRESS (unblock)
```

| From | To | Who | Notes |
|------|----|-----|-------|
| `NEW` | `IN_PROGRESS` | OWNER, ADMIN, assigned MEMBER | Start work |
| `IN_PROGRESS` | `COMPLETED` | OWNER, ADMIN, assigned MEMBER | Finish work |
| `IN_PROGRESS` | `BLOCKED` | OWNER, ADMIN | Requires block reason (Phase 4) |
| `BLOCKED` | `IN_PROGRESS` | OWNER, ADMIN | Unblock (Phase 4) |
| `COMPLETED` | `IN_PROGRESS` | OWNER, ADMIN | Reopen |

**Invalid transitions** (service throws `BadRequestException`):
- `NEW → COMPLETED` (must go through IN_PROGRESS)
- `NEW → BLOCKED`
- `COMPLETED → NEW`
- `BLOCKED → COMPLETED` (must unblock first)

> **Note:** `BLOCKED` status and block/unblock endpoints are implemented in Phase 4.
> In Phase 3, only `NEW`, `IN_PROGRESS`, and `COMPLETED` transitions are supported.

---

### API Endpoints

| Method | Path | Description | Access |
|--------|------|-------------|--------|
| `POST` | `/api/projects/{projectId}/jobs` | Create job | Any member |
| `GET` | `/api/projects/{projectId}/jobs` | List jobs | OWNER/ADMIN: all; MEMBER: assigned only |
| `GET` | `/api/projects/{projectId}/jobs/{id}` | Get job | Any member (MEMBER: assigned only) |
| `PUT` | `/api/projects/{projectId}/jobs/{id}` | Update job fields | OWNER, ADMIN |
| `PATCH` | `/api/projects/{projectId}/jobs/{id}/status` | Change status | See transition table |
| `DELETE` | `/api/projects/{projectId}/jobs/{id}` | Soft delete | OWNER, ADMIN |

#### Request / Response shapes

**Create Job Request:**
```json
{
  "title": "Fix login page bug",
  "description": "Users report 500 on login",
  "client": "Acme Corp",
  "assignedTo": "550e8400-e29b-41d4-a716-446655440000",
  "deadline": "2026-03-01T00:00:00Z"
}
```

**Job Response:**
```json
{
  "id": "...",
  "projectId": "...",
  "title": "Fix login page bug",
  "description": "Users report 500 on login",
  "client": "Acme Corp",
  "assignedTo": "550e8400-e29b-41d4-a716-446655440000",
  "assignedToName": "Jane Doe",
  "deadline": "2026-03-01T00:00:00Z",
  "status": "NEW",
  "createdBy": "...",
  "createdAt": "2026-02-24T10:00:00Z",
  "updatedAt": "2026-02-24T10:00:00Z"
}
```

**Update Status Request:**
```json
{ "status": "IN_PROGRESS" }
```

---

### Access Control

All job endpoints first verify the caller is a project member (same pattern as project endpoints).
On top of that:

| Operation | OWNER | ADMIN | MEMBER |
|-----------|-------|-------|--------|
| Create job | Yes | Yes | Yes |
| List jobs | All jobs | All jobs | Assigned only |
| Get job | Yes | Yes | Assigned only |
| Update job fields | Yes | Yes | No |
| Change status (own transitions) | Yes | Yes | Assigned only |
| Delete job | Yes | Yes | No |

---

## Alternatives Considered

### Alternative 1: More status values (e.g., CANCELLED, ON_HOLD)

**Why rejected:** Violates the "simple over complex" design principle. The 4 statuses cover the
OpsClear use case. Additional statuses can be added post-MVP based on user feedback.

### Alternative 2: Separate `job_assignments` table (multiple assignees)

**Why rejected:** The MVP use case is one responsible person per job. Multiple assignees adds
query and permission complexity. A single `assigned_to` FK is enough for now.

### Alternative 3: Jobs at user level (not project-scoped)

**Why rejected:** Contradicts the project-as-tenant design from ADR-0004. All entities live
under a project.

---

## Consequences

### Positive

- Simple, well-understood model
- Status transitions are explicit and enforceable in the service layer
- MEMBER isolation (sees only assigned jobs) gives privacy by default
- Blocking is designed into the schema from day one (Phase 4 ready)

### Negative

- MEMBERs cannot see unassigned jobs — if a job has no assignee, only OWNER/ADMIN can see it
- No bulk status updates in MVP

### Neutral

- Blocking fields (`blocked_by`, `blocked_reason`, `blocked_at`) will be added in Phase 4 migration
- Notes and Approvals reference `job_id` — schema already accounts for this via FK

---

## Future Considerations

### Configurable per-project statuses

Project owners could define their own status set and allowed transitions, replacing the fixed
4-status enum. This would require a `project_statuses` table and a `status_transitions` table,
plus UI to configure them per project. Deferred post-MVP — the fixed statuses cover all current
SME use cases. Tracked in issue #100.

---

## References

- [ADR-0004: Projects Model and Multi-Tenancy](./0004-projects-model.md)
- [ADR-0005: Roles and Permissions Model](./0005-roles-and-permissions.md)
