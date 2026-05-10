# ADR-0033: Org-Level Job Templates

**Status:** Accepted
**Date:** 2026-05-10
**Author:** Jovan Manojlovic

## Context

ADR-0032 introduced project-scoped job templates. In practice, teams with multiple projects define the same template repeatedly in each project — e.g. "Bug Report", "Client Call Follow-up", "Deploy Checklist". These cross-cutting templates should be defined once at the organisation level and be available in every project's job creation modal.

## Decision

Extend the existing `job_templates` table to support two scopes: project-level (current) and org-level (new). Both scopes are served through the same data model; scope is determined by which FK is populated.

Org-level templates are managed from a new **Templates** tab in Org Settings. Project-level templates remain on the per-project Templates page. Both appear in the "Start from template" dropdown in `NewJobModal`, labelled by scope.

## Database schema changes

### `job_templates` table modifications

```sql
-- project_id becomes nullable (was NOT NULL)
ALTER TABLE job_templates ALTER COLUMN project_id DROP NOT NULL;

-- add nullable org_id
ALTER TABLE job_templates
    ADD COLUMN org_id UUID REFERENCES organisations(id) ON DELETE CASCADE;

-- exactly one of project_id / org_id must be set
ALTER TABLE job_templates
    ADD CONSTRAINT job_templates_scope_check
    CHECK ((project_id IS NULL) <> (org_id IS NULL));

-- index for org-scope queries
CREATE INDEX job_templates_org_id_idx
    ON job_templates (org_id)
    WHERE deleted_at IS NULL;
```

Existing rows keep `project_id` set and `org_id = NULL` — no backfill required.

### Friendly IDs

`TEMPLATE` entity type already exists in `FriendlyIdEntityType` and `org_sequences`. Org-level templates share the same sequence and `TPL-` prefix per org — no schema change needed.

## API design

### Org-level CRUD (new endpoints)

```
GET    /api/organisations/{orgId}/templates
POST   /api/organisations/{orgId}/templates
PUT    /api/organisations/{orgId}/templates/{id}
DELETE /api/organisations/{orgId}/templates/{id}
```

All four require `OWNER` or `ADMIN` role and are gated by `@RequiresAddon(JOB_TEMPLATES)`.

Request/response body is identical to the existing project-level DTOs. `projectId` is absent from org-level requests (scope comes from the URL path). Response adds `scope` and `orgId` fields.

### Combined project listing (one change)

```
GET /api/projects/{projectId}/templates
```

Returns project-scoped templates **plus** org-scoped templates for the org that owns the project, merged into a single list. Each item is tagged:

```json
{ "scope": "PROJECT", "projectId": "...", "orgId": null, ... }
{ "scope": "ORG",     "projectId": null, "orgId": "...", ... }
```

Service implementation: single jOOQ query with `WHERE (project_id = ? OR org_id = ?)` filtered by the org the requester belongs to, ordered by scope then name.

### Usage recording (unchanged)

```
POST /api/projects/{projectId}/templates/{id}/use
```

The service validates that the template belongs to the project **or** to the org that owns the project before incrementing `occurrence_count`. No endpoint change.

## Backend changes

### 1. `JobTemplateModel`

Add `orgId` and `scope` fields. `scope` is a computed value (`"PROJECT"` or `"ORG"`) derived from which FK is set — not stored in the DB.

### 2. `JobTemplateRepository`

Replace `findActiveByProjectId` with:

```
findActiveByProjectIdOrOrgId(UUID projectId, UUID orgId)
```

Existing `save`, `softDelete`, `incrementOccurrenceCount` methods are unchanged.

### 3. `JobTemplateService`

- `list(projectId, requesterId)` — resolves the org from the project, calls the combined query.
- `create(projectId or orgId, body, requesterId)` — sets either `projectId` or `orgId` depending on the calling endpoint; all other logic unchanged.
- `recordUsage(id, requesterId)` — adds org-ownership check alongside existing project-ownership check.

### 4. `OrgTemplateController` (new)

New controller at `/api/organisations/{orgId}/templates`. Mirrors `JobTemplateController` but resolves scope from the org path param. Shares `JobTemplateService` — no duplicate logic.

### DTOs

`JobTemplateResponse` gains `scope` (`"PROJECT"` | `"ORG"`) and `orgId` fields. Request DTOs are unchanged — scope is inferred from the endpoint.

## Frontend changes

### 1. Org Settings — Templates tab (new)

New tab in `OrgSettingsPage` using the same `TemplateRow` / `TemplateFormModal` components as the project Templates page. Calls `/api/organisations/{orgId}/templates`.

### 2. `NewJobModal` dropdown

Templates returned by the combined listing already carry `scope`. The dropdown option label is prefixed:

```
[Org]  Onboarding Call
       Bug Report          ← project-level (no prefix)
       Deploy Checklist    ← project-level
```

No logic change — `handleTemplateSelect` works unchanged since all field mappings are the same.

### 3. `templatesApi` additions

```ts
orgList(orgId)
orgCreate(orgId, body)
orgUpdate(orgId, templateId, body)
orgDelete(orgId, templateId)
```

Existing `list`, `create`, `update`, `delete`, `recordUsage` are unchanged.

## Migrations

| Migration | Content |
|-----------|---------|
| `V020__org_level_templates.sql` | `ALTER TABLE job_templates` — drop NOT NULL on `project_id`, add `org_id` column, add CHECK constraint, add index |

## Alternatives considered

### Separate `org_templates` table

Cleaner schema but duplicates ~15 columns and all query/service logic. Single-table dual-scope keeps everything in one place and occurrence tracking, friendly IDs, and soft-delete patterns apply uniformly.

### Separate `scope` column instead of nullable FKs

A `scope VARCHAR(20)` column with both FKs always present would work but allows inconsistent state (both set, neither set). The nullable FK + CHECK constraint enforces the invariant at the database level with no application code needed.

### Copy org template to project on use

Would decouple job history from the org template but breaks the "one source of truth" model — updates to the org template would not reflect in future uses. Not worth the complexity.

## Consequences

### Positive

- Teams with multiple projects define shared templates once at the org level
- Combined listing endpoint keeps `NewJobModal` a single API call — no change to the dropdown UX
- All existing project-level template data and behaviour is unchanged
- Org templates share the same `TPL-` friendly ID sequence — consistent UX

### Negative

- `project_id` becomes nullable — any query that assumes it is non-null must be updated
- Combined listing query is slightly more complex (OR condition on two FKs)
- `OrgTemplateController` is a new surface area requiring its own integration tests

### Neutral

- `TemplateFormModal` is reused as-is — no component duplication
- Org-template CRUD is owner/admin only — same permission model as project templates

## Implementation order

1. `V020__org_level_templates.sql` migration
2. `JobTemplateModel` — add `orgId`, `scope`
3. `JobTemplateRepository` — update combined listing query
4. `JobTemplateService` — update `list` and `recordUsage`; add org-scoped `create`/`update`/`delete`
5. `OrgTemplateController` + DTOs update (`scope`, `orgId` in response)
6. `templatesApi` — add org CRUD methods
7. Org Settings Templates tab
8. `NewJobModal` — scope label in dropdown

## References

- [ADR-0026: Organisation layer](0026-organisation-layer.md)
- [ADR-0031: Feature gating full stack](0031-feature-gating.md)
- [ADR-0032: Job templates](0032-job-templates.md)
- [JOB-107: ADR — Org-level job templates](https://46.101.205.77:8443/projects/PRJ-006/jobs/JOB-107)
