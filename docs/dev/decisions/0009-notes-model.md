# ADR-0009: Notes Model

**Status:** Accepted
**Date:** 2026-02-27
**Author:** Jovan Manojlovic

## Context

OpsClear needs a way for team members to record contextual information on a job — decisions made,
agreements reached, handover details, status updates. This must be captured in a way that cannot be
revised after the fact, so the record of what was said remains trustworthy.

The core product promise is *"What's the truth about our work TODAY?"* — a mutable comments thread
would undermine that promise. If anyone can edit or delete what was said, the record ceases to be a
source of truth.

### Requirements

- Any project member (OWNER, ADMIN, MEMBER) can add a note to any job in their project
- Notes are **immutable** — no editing, no deletion after creation
- A note records: who wrote it, when, and what it says
- Notes are returned in chronological order (oldest first)
- Content is bounded to prevent abuse

---

## Decision

### Schema

```sql
CREATE TABLE notes (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID      NOT NULL REFERENCES jobs(id),
    author_id  UUID      NOT NULL REFERENCES users(id),
    content    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notes_job_id ON notes(job_id);
-- project-scoped query joins through jobs; jobs.project_id index already exists from V2
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Primary key |
| `job_id` | UUID (FK) | The job this note belongs to |
| `author_id` | UUID (FK) | The user who wrote the note |
| `content` | VARCHAR(2000) | Note body |
| `created_at` | TIMESTAMP | Write timestamp — set by the database, never by the caller |

**No `deleted_at` column.** Notes are permanent. This is a deliberate departure from the soft-delete
pattern used elsewhere in the codebase — soft-deleting a note would defeat its audit purpose.

### Immutability Enforcement

Immutability is enforced at the service layer:

- `NoteService` exposes only `create` and `listByJob` — no update or delete methods
- `NoteController` exposes only `POST` and `GET` — no `PUT`, `PATCH`, or `DELETE` endpoints
- `NoteRepository` exposes only `insert` and `selectByJobId` — no update or delete queries

There is no database-level constraint preventing deletion (e.g. a trigger), as the application layer
is the enforcer for MVP. A future migration could add a trigger if the threat model requires it.

### NoteModel

```java
public class NoteModel {
    private UUID id;
    private UUID jobId;
    private UUID authorId;
    private String content;
    private Instant createdAt;
}
```

### API

| Method | Path | Description | Access |
|--------|------|-------------|--------|
| `POST` | `/api/projects/{projectId}/jobs/{jobId}/notes` | Add a note to a job | Any project member |
| `GET` | `/api/projects/{projectId}/jobs/{jobId}/notes` | List notes for a job (oldest first) | Any project member |
| `GET` | `/api/projects/{projectId}/notes` | All notes in the project, grouped by job | Any project member |

#### Create Request

```json
{ "content": "Client confirmed the new deadline over email — proceeding." }
```

`content` is validated: `@NotBlank`, max 2000 characters.

#### Note Response (single note)

```json
{
  "id": "...",
  "jobId": "...",
  "authorId": "...",
  "content": "Client confirmed the new deadline over email — proceeding.",
  "createdAt": "2026-02-27T10:15:00Z"
}
```

`authorId` is returned rather than a nested user object — consistent with how `blockedBy` is
returned in `JobResponse`. The frontend resolves display names from the project members list it
already holds.

#### Per-job list response

Notes returned as a JSON array ordered by `created_at ASC`. No pagination for MVP — notes
per job are expected to be small in number.

#### Project-level grouped response

`GET /api/projects/{projectId}/notes` returns notes grouped by job. Jobs are ordered by their
most recent note (`DESC`), so the most active jobs appear first. Notes within each group are
ordered `created_at ASC` (chronological thread, consistent with the per-job endpoint).

```json
[
  {
    "jobId": "...",
    "jobName": "Install electrical panel",
    "notes": [
      { "id": "...", "authorId": "...", "content": "Waiting on delivery.", "createdAt": "2026-02-27T08:00:00Z" },
      { "id": "...", "authorId": "...", "content": "Parts arrived, resuming tomorrow.", "createdAt": "2026-02-27T14:30:00Z" }
    ]
  },
  {
    "jobId": "...",
    "jobName": "Roof inspection",
    "notes": [
      { "id": "...", "authorId": "...", "content": "Access denied — rescheduled.", "createdAt": "2026-02-27T09:15:00Z" }
    ]
  }
]
```

Only jobs that have at least one note are included. The grouping is done in the service layer:
the repository fetches a flat result set (`notes JOIN jobs WHERE jobs.project_id = ?`
`ORDER BY max_note_created_at DESC, notes.created_at ASC`) and the service folds it into the
grouped structure before returning. No pagination for MVP.

### Access Control

The job access check already verifies that the caller is a member of the project. No additional
per-note permission check is needed: any member who can see the job can add or read its notes.

---

## Alternatives Considered

### Alternative 1: Mutable notes (allow edit/delete)

Allow authors to edit or delete their own notes.

**Pros:** Fixes typos without leaving a permanent record of the mistake.

**Cons:** Destroys the audit trail — the entire value proposition of notes is that they are
permanent. If notes can be changed, they cannot be trusted as a record of what was agreed.

**Why rejected:** Immutability is the point.

### Alternative 2: Soft-delete notes

Keep a `deleted_at` column — consistent with the rest of the codebase.

**Pros:** Consistent pattern; data is recoverable.

**Cons:** Soft-deleted notes are still logically present. If an OWNER can "delete" a note that an
employee wrote, it undermines the audit trail. The soft-delete pattern exists to support recovery
of accidentally deleted records (projects, jobs), not to support censorship of an audit log.

**Why rejected:** The semantics of soft-delete conflict with the purpose of notes. A clean schema
with no delete path is clearer and more honest.

### Alternative 3: Restrict note creation to OWNER/ADMIN

Only managers can write notes.

**Pros:** Cleaner authority model — only one side of the conversation writes to the record.

**Cons:** MEMBERs are the ones doing the work. They need to record blockers, agreements, and
handover context in real time without waiting for a manager. Restricting them makes the record
stale and incomplete.

**Why rejected:** Same rationale as blocking — the worker is the first to know. Notes written
by OWNER, ADMIN, and MEMBER together give the fullest picture.

### Alternative 4: Attach notes to the project, not the job

A single project-level activity feed rather than per-job notes.

**Pros:** Single view for all project activity.

**Cons:** Context is lost — a note about a delivery delay means nothing unless it's attached to
the specific job. The dashboard surfaces jobs, not projects, so per-job notes are more actionable.

**Why rejected:** Per-job scoping is more useful for day-to-day operational tracking.

---

## Consequences

### Positive

- Simple, append-only data model — no update or delete paths to implement or test
- Trustworthy audit trail — no one can revise what was written
- Any team member can record real-time context without manager involvement

### Negative

- Typos and accidental notes are permanent — callers should confirm before submitting
- No pagination: if a single job accumulates hundreds of notes, the `GET` response grows
  unbounded (acceptable for MVP; add cursor pagination post-MVP if needed)

### Neutral

- `notes` does not follow the soft-delete pattern — future contributors must be aware this table
  is intentionally different
- `authorId` is returned as a UUID; the frontend joins against the project members list for
  display names

---

## Implementation Notes

- Migration: `V7__create_notes_table.sql`
- `created_at` is set by `DEFAULT CURRENT_TIMESTAMP` in SQL — the service never passes a timestamp
- `NoteRepository` exposes:
  - `insert(jobId, authorId, content)` → `NoteModel`
  - `selectByJobId(jobId)` → `List<NoteModel>` ordered `created_at ASC`
  - `selectByProjectId(projectId)` → flat `List` of rows including `job_id` and `job_name`,
    ordered `max(created_at) over (partition by job_id) DESC, created_at ASC`; the service folds
    this into `List<NotesByJobResponse>`
- `NotesByJobResponse` is a response-only DTO: `{ jobId, jobName, notes: List<NoteResponse> }`
- The job existence and project membership check reuses the same guard already applied in
  `JobService` — pass `projectId` and `jobId` to `NoteService.create` and validate before inserting

---

## References

- [ADR-0007: Job Model and Status Flow](./0007-job-model-and-status-flow.md)
- [ADR-0008: Blocking Model](./0008-blocking-model.md)
- [ADR-0005: Roles and Permissions Model](./0005-roles-and-permissions.md)
