# ADR-0022: Job Relationships

**Status:** Accepted
**Date:** 2026-03-18
**Author:** Jovan Manojlovic

## Context

Jobs often depend on or relate to other jobs, but there is no way to express that in the system. Teams work around it with notes or job titles (e.g. "Blocked by #45"). This makes dependencies invisible, hard to track, and easy to miss.

The goal is to allow jobs to reference each other in a structured way so that relationships between work items are explicit and visible.

## Decision

Introduce a `job_relationships` table that links two jobs with a typed, directional relationship.

### Relationship types

| Type | Meaning |
|------|---------|
| `BLOCKED_BY` | Hard dependency — this job cannot progress until the linked job is resolved |
| `RELATED_TO` | Loose context — informational, no hard dependency |
| `DUPLICATES` | This job is a duplicate of the linked job — informational only |

### Data model

```sql
CREATE TABLE job_relationships (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    target_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    type         VARCHAR(20) NOT NULL CHECK (type IN ('BLOCKED_BY', 'RELATED_TO', 'DUPLICATES')),
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT no_self_reference CHECK (source_job_id != target_job_id),
    CONSTRAINT unique_relationship UNIQUE (source_job_id, target_job_id, type)
);
```

### API

```
POST   /api/projects/{projectId}/jobs/{jobId}/relationships
DELETE /api/projects/{projectId}/jobs/{jobId}/relationships/{relationshipId}
```

`POST` request body:
```json
{ "targetJobId": "uuid", "type": "BLOCKED_BY" }
```

`JobResponse` includes a `relationships` list:
```json
{
  "relationships": [
    {
      "id": "uuid",
      "type": "BLOCKED_BY",
      "direction": "OUTGOING",
      "job": { "id": "uuid", "title": "...", "status": "IN_PROGRESS" }
    }
  ]
}
```

Both sides of a relationship are returned — if A is BLOCKED_BY B, then B's response includes an `INCOMING` entry of type `BLOCKED_BY` pointing to A.

### Rules

- **Multiplicity:** a job can have multiple relationships of any type
- **Duplicate prevention:** the `UNIQUE` constraint on `(source_job_id, target_job_id, type)` prevents the same relationship being added twice
- **Self-reference:** prevented by `CHECK (source_job_id != target_job_id)`
- **Scope:** same-project jobs only — validated at the service layer
- **Permissions:** any project member can add or remove relationships
- **Cascade:** `ON DELETE CASCADE` — deleting a job silently removes all its relationships

### BLOCKED_BY and block status

These are decoupled. Adding a `BLOCKED_BY` relationship does not automatically change the job's status. The UI offers to set the job to `BLOCKED` when a `BLOCKED_BY` relationship is added, but does not force it. This keeps the data model simple and avoids complex status automation.

### DUPLICATES behavior

Informational only. No automatic closing, status change, or merge. The user decides what to do with the duplicate.

## Alternatives Considered

### Alternative 1: Single JSONB field on jobs

Store relationships as a JSONB array on the `jobs` table.

**Pros:**
- No new table, simpler migration

**Cons:**
- Not queryable — cannot efficiently find "all jobs blocking job X"
- No referential integrity

**Why rejected:** Relationships need to be queryable from both sides.

### Alternative 2: Bidirectional rows (store both directions)

Store A→B and B→A as separate rows.

**Pros:**
- Simple queries — always read by `source_job_id`

**Cons:**
- Double writes, risk of inconsistency if one delete fails
- Duplicate prevention is harder

**Why rejected:** Directional rows with a query that unions both sides is cleaner and consistent.

### Alternative 3: Mandatory status coupling for BLOCKED_BY

Automatically set job status to `BLOCKED` when a `BLOCKED_BY` relationship is added.

**Pros:**
- Tighter model, less manual work

**Cons:**
- Automation is surprising and hard to undo
- Conflicts with the existing manual block flow

**Why rejected:** Keep relationships and status independent. The UI can offer a hint.

## Consequences

### Positive

- Dependencies between jobs are explicit and visible
- Both sides of a relationship are shown without extra queries
- `ON DELETE CASCADE` keeps the DB clean automatically
- Unique constraint prevents accidental duplicate relationships

### Negative

- `JobResponse` grows with a `relationships` list — slightly larger payload
- Queries for job detail must now JOIN or fetch relationships separately

### Neutral

- Cross-project and milestone relationships are out of scope — can be revisited based on demand
- Integration tests need to cover relationship CRUD and both-side visibility

## Implementation Notes

1. Flyway migration to create `job_relationships` table
2. `JobRelationship` entity + `JobRelationshipRepository`
3. `JobRelationshipService` — create, delete, validate same-project scope, validate no self-reference
4. `JobRelationshipController` — POST + DELETE endpoints
5. Update `JobResponse` to include `relationships` list (fetch in `JobService.getById`)
6. Frontend — relationships section on job detail page, add modal with job search + type picker

## API Changes Checklist

- [ ] Update Postman collection
- [ ] Add example requests for new endpoints
- [ ] Test the flow manually before marking complete

## References

- [ADR-0007: Job Model and Status Flow](0007-job-model-and-status-flow.md)
- [ADR-0008: Blocking Model](0008-blocking-model.md)
- [ADR-0015: Job Detail and Inline Actions](0015-job-detail-and-inline-actions.md)
