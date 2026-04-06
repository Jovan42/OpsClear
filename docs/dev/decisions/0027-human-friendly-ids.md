# ADR-0027: Human-friendly IDs for projects, jobs, and milestones

**Status:** Proposed
**Date:** 2026-03-26
**Author:** Jovan Manojlovic

## Context

All entities currently use UUIDs as their only identifier. URLs like
`/projects/736da184.../jobs/c1373faf...` are opaque — hard to reference
in conversations, emails, or support tickets.

Linear (`ENG-123`) and Jira (`PROJ-123`) prove this pattern works well at scale.
The change is significantly cheaper before real users rely on the current URL
structure — once production data accumulates, backfilling and migrating URLs
means broken bookmarks and redirect infrastructure.

## Decision

Introduce per-org, per-entity-type sequential IDs with configurable prefixes.

### Entities in scope

| Entity | Default prefix | Example |
|--------|---------------|---------|
| Project | `PRJ` | `PRJ-001` |
| Job | `JOB` | `JOB-042` |
| Milestone | `MIL` | `MIL-007` |

Users are excluded — they are internal and never referenced in URLs or tickets.

### Prefix rules

- 2–3 uppercase letters (`[A-Z]{2,3}`)
- Unique per org
- Configurable by OWNER or ADMIN in org settings
- **Immutable once set** — changing a prefix would break existing references and bookmarks
- Defaults (`PRJ`, `JOB`, `MIL`) are assigned automatically on org creation

### Org settings table

Prefix configuration is stored in a new `org_settings` table (1-to-1 with org):

```
org_settings
├── org_id FK (unique)
├── job_prefix      (default: JOB)
├── project_prefix  (default: PRJ)
├── milestone_prefix (default: MIL)
└── created_at / updated_at
```

Created automatically with defaults when the org is created. OWNER or ADMIN
can update prefixes only before any records exist for that entity type —
immutable once the first record is created.

### Sequence strategy

Each org has a counter per entity type in a new `org_sequences` table:

```
org_sequences
├── org_id FK
├── entity_type (PROJECT | JOB | MILESTONE)
├── last_value (int)
└── PRIMARY KEY (org_id, entity_type)
```

Incremented via `SELECT ... FOR UPDATE` to prevent race conditions under
concurrent inserts. Counters are independent per entity type —
`PRJ-001`, `JOB-001`, `MIL-001` each have their own sequence.

### Gap behaviour

Gaps in the sequence are acceptable. If a record is soft-deleted, its
friendly ID is retained on the record and never reused. Restoring a
soft-deleted record restores its original friendly ID intact.

### URL structure

Human-friendly IDs are used in URLs — not display-only:

```
/acme/projects/PRJ-001/jobs/JOB-042
```

The API resolves friendly IDs to UUIDs internally for all DB operations:

- `GET /api/orgs/acme/projects/PRJ-001/jobs/JOB-042` resolves correctly
- Required so that page refresh and direct URL access work correctly
- UUIDs remain in the DB as primary keys but are never exposed in URLs
- URL resolution is case-insensitive — API normalises input to uppercase

### Backfill

Existing records have no friendly ID. A Flyway migration backfills IDs
in `created_at` ascending order per entity type per org.

## Alternatives Considered

### Alternative 1: Display-only friendly IDs (UUID routes only)

Keep UUID-based routes, show friendly IDs as labels only.

**Pros:**
- No routing changes required
- Simpler implementation

**Cons:**
- Page refresh breaks if the app navigates to a friendly-ID URL
- Copy-pasting a URL from the UI gives an opaque UUID link
- Defeats the purpose — teammates can't reference `JOB-042` in conversation and open it directly

**Why rejected:** The value of human-friendly IDs is in the URL. Display-only is half a solution.

### Alternative 2: Global sequence (not per-org)

One shared counter across all orgs per entity type.

**Pros:**
- Simpler — no `org_sequences` table, just a single counter

**Cons:**
- IDs jump unpredictably (org A creates `JOB-001`, org B gets `JOB-002`)
- Numbers feel random rather than sequential within a workspace

**Why rejected:** Per-org sequences give each organisation a clean `JOB-001, JOB-002, ...` experience.

### Alternative 3: Postgres native sequences (one per org)

Create a Postgres `SEQUENCE` object per org.

**Pros:**
- Native DB-level atomicity, no `FOR UPDATE` lock needed

**Cons:**
- Creating a sequence per org means DDL on every org creation
- Cannot be done inside a regular transaction / Flyway migration cleanly
- Harder to inspect and manage

**Why rejected:** `SELECT MAX + 1 FOR UPDATE` on `org_sequences` is simpler to manage and sufficient at OpsClear's scale.

## Consequences

### Positive

- URLs are human-readable and shareable (`/acme/projects/PRJ-001/jobs/JOB-042`)
- Teammates can reference `JOB-042` in Slack or email and recipients can navigate directly
- Consistent with industry-standard patterns (Linear, Jira)

### Negative

- Every entity creation acquires a short row-level lock on `org_sequences`
- Routing layer must resolve friendly IDs to UUIDs before hitting the DB
- Prefix immutability is a constraint users must understand upfront

### Neutral

- UUIDs remain as primary keys — no FK changes needed
- Org slug (from ADR-0026) provides the first URL segment

## Implementation Notes

- `org_settings` and `org_sequences` are created as part of the org layer (ADR-0026) or in a dedicated migration
- Backfill migration must run after `org_sequences` is populated
- All API path resolvers must handle both friendly ID and UUID for a transition period if backward compat is needed (decided: friendly ID only in new routes)
- Frontend router must use friendly IDs in all `<Link>` and `navigate()` calls

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`)
- [ ] Add example requests for new endpoints
- [ ] Update environment variables if needed
- [ ] Test the flow manually before marking complete

## References

- ADR-0026: Organisation layer (org slug is the URL namespace; `org_settings` and `org_sequences` live under the org)
- Linear, Jira — industry reference for this pattern
