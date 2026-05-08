# ADR-0032: Job Templates

**Status:** Accepted
**Date:** 2026-05-08
**Author:** Jovan Manojlovic

## Context

Teams repeat the same type of job frequently — same title, client, responsible person, and structure. Currently every job is created from scratch, which is slow and error-prone. A construction company running a monthly "Electrical inspection" or a dev team creating a weekly "Deploy checklist" must retype everything every time.

This ADR introduces project-scoped job templates: reusable blueprints that pre-fill job fields and support dynamic `{{wildcard}}` placeholders resolved client-side at creation time.

## Decision

Add a job template system gated by the `JOB_TEMPLATES` addon. Templates are managed on a dedicated Templates page within each project and applied via a "Start from template" selector at the top of the job creation modal.

The backend stores raw template strings — all wildcard resolution happens on the frontend at job creation time.

## Database schema

### `job_templates` table

```sql
CREATE TABLE job_templates (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    friendly_id          VARCHAR(20)  NOT NULL,
    project_id           UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name                 VARCHAR(100) NOT NULL,
    title                VARCHAR(255),
    description          TEXT,
    client               VARCHAR(255),
    priority             VARCHAR(20),
    assignee_mode        VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    assignee_id          UUID         REFERENCES users(id) ON DELETE SET NULL,
    milestone_id         UUID         REFERENCES milestones(id) ON DELETE SET NULL,
    deadline_offset_days INT,
    occurrence_count     INT          NOT NULL DEFAULT 0,
    created_by           UUID         NOT NULL REFERENCES users(id),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at           TIMESTAMP
);

CREATE UNIQUE INDEX job_templates_friendly_id_idx
    ON job_templates (UPPER(friendly_id))
    WHERE deleted_at IS NULL;

CREATE INDEX job_templates_project_id_idx
    ON job_templates (project_id)
    WHERE deleted_at IS NULL;
```

Soft delete via `deleted_at` — matches the pattern used by jobs and milestones.

### Friendly ID

Add `TEMPLATE` to the `FriendlyIdEntityType` enum and `template_prefix VARCHAR(3) DEFAULT 'TPL'` to the `org_settings` table. Add `TEMPLATE` to `org_sequences` on first use. Generated IDs follow the `TPL-001` format.

### Addon availability

The `JOB_TEMPLATES` row already exists in `subscription_addons` (inserted in V016) with `available = FALSE`. A new migration sets `available = TRUE` and confirms the price.

## Template fields

| Field | Type | Notes |
|-------|------|-------|
| `name` | VARCHAR(100) | Template display name — separate from the job title |
| `title` | VARCHAR(255) | Job title pre-fill — supports `{{wildcards}}` |
| `description` | TEXT | Markdown — supports `{{wildcards}}`, checklists, HTML comment hints |
| `client` | VARCHAR(255) | Optional pre-fill |
| `priority` | VARCHAR(20) | Optional: LOW / MEDIUM / HIGH / CRITICAL |
| `assignee_mode` | VARCHAR(20) | NONE / FIXED / ASK (see below) |
| `assignee_id` | UUID | Populated when mode is FIXED; SET NULL on user removal |
| `milestone_id` | UUID | Optional reference; SET NULL if milestone is deleted |
| `deadline_offset_days` | INT | Nullable — deadline = creation date + N days |
| `occurrence_count` | INT | Incremented each time a job is created from this template |

Status is never stored on a template — jobs created from templates always start as NEW.

## Assignee modes

| Mode | Behaviour |
|------|-----------|
| `NONE` | Assignee field left empty, no prompt |
| `FIXED` | Pre-fills assignee with the stored `assignee_id` |
| `ASK` | Field is left blank and focused when the modal opens |

## Wildcard system

All wildcard resolution is **client-side only** — the backend stores raw strings.

### Base wildcards

| Wildcard | Resolves to |
|----------|-------------|
| `{{date}}` | Today's date in user preferred format |
| `{{day}}` | Day of the month (numeric) |
| `{{month}}` | Month name |
| `{{year}}` | Year |
| `{{week}}` | ISO week number (W19) |
| `{{quarter}}` | Q1 / Q2 / Q3 / Q4 |
| `{{project}}` | Project name |
| `{{creator}}` | Name of the logged-in user creating the job |
| `{{assignee}}` | Name of the assigned person (from template assignee field) |
| `{{occurrence}}` | Current `occurrence_count + 1` at time of creation |

### Arithmetic wildcards

Time-based wildcards support `+N` / `-N` offset: `{{wildcard+N}}` or `{{wildcard-N}}`.

```
{{date+5}}    → today + 5 days
{{month-1}}   → previous month name (December in January)
{{week+1}}    → next ISO week
{{quarter+1}} → next quarter (rolls year correctly)
{{year-1}}    → previous year
```

Month and quarter arithmetic handles year rollover correctly — `{{month+2}}` in November → January. `{{year}}` is unaffected; use `{{year+1}}` explicitly when needed.

Arithmetic is not supported on: `project`, `creator`, `assignee`, `occurrence`.

### Description format

Template descriptions support full Markdown:
- Headers: `## Section`
- Checklists: `- [ ] item`
- HTML comment hints: `<!-- fill this in -->` — visible in editor, invisible in rendered view
- Wildcards anywhere in the text

## Backend changes

### 1. `AddonCode` enum

Add `JOB_TEMPLATES` to `com.opsclear.aop.AddonCode`.

### 2. `JobTemplateModel`

New model following the `MilestoneModel` pattern:

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobTemplateModel {
    private UUID id;
    private String friendlyId;
    private UUID projectId;
    private String name;
    private String title;
    private String description;
    private String client;
    private String priority;
    private String assigneeMode;   // NONE / FIXED / ASK
    private UUID assigneeId;
    private String assigneeName;   // JOIN from users
    private UUID milestoneId;
    private String milestoneName;  // JOIN from milestones
    private Integer deadlineOffsetDays;
    private int occurrenceCount;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete()   { this.deletedAt = Instant.now(); }
}
```

### 3. `JobTemplateRepository`

Uses jOOQ `DSLContext` — same pattern as `MilestoneRepository`:

```
findActiveByProjectId(UUID projectId)
findByIdAndDeletedAtIsNull(UUID id)
save(JobTemplateModel model)          // insert or update
softDelete(UUID id)
incrementOccurrenceCount(UUID id)     // atomic UPDATE ... SET occurrence_count = occurrence_count + 1
```

### 4. `JobTemplateService`

Write methods `@Transactional`, reads `@Transactional(readOnly = true)`. Guards:

```
list(projectId, requesterId)     → requireMember
create(...)                      → requireOwnerOrAdmin
update(...)                      → requireOwnerOrAdmin
delete(...)                      → requireOwnerOrAdmin
recordUsage(id, requesterId)     → requireMember (called when job is created from template)
```

`recordUsage` calls `incrementOccurrenceCount` atomically — no read-modify-write race.

### 5. `JobTemplateController`

All endpoints annotated `@RequiresAddon(AddonCode.JOB_TEMPLATES)`:

```
GET    /api/projects/{projectId}/templates          → list
POST   /api/projects/{projectId}/templates          → create (201)
PUT    /api/projects/{projectId}/templates/{id}     → update
DELETE /api/projects/{projectId}/templates/{id}     → soft delete (204)
POST   /api/projects/{projectId}/templates/{id}/use → increment occurrence_count (200)
```

The `/use` endpoint is called by the frontend when a job is successfully created from a template. Decoupled from job creation so the job controller stays unchanged.

### DTOs

`JobTemplateResponse.from(JobTemplateModel)` — static factory, same pattern as `MilestoneResponse`.

`CreateJobTemplateRequest` / `UpdateJobTemplateRequest` — Jakarta validation (`@NotBlank` on `name`).

## Frontend changes

### 1. API client

New `templatesApi` in `frontend/src/api/templates.ts`:

```ts
list(projectId)
create(projectId, body)
update(projectId, templateId, body)
delete(projectId, templateId)
recordUsage(projectId, templateId)
```

### 2. Nav item

In `AppLayout.tsx` `ProjectNav`, add alongside Milestones:

```ts
const templatesLocked = !hasAddon('JOB_TEMPLATES');
```

Render nav link when `!templatesLocked`, locked nav link otherwise (same `lockedNavLink()` helper).

### 3. Templates page (`frontend/src/features/templates/`)

- `TemplatesPage.tsx` — list of templates (name, title preview, last used, occurrence count, created by). Create button (owner/admin only). Edit and delete per row (owner/admin only). Empty state for members.
- `TemplateFormModal.tsx` — create/edit form: name, title, description (MarkdownEditor), client, priority, assignee mode + assignee picker, milestone picker, deadline offset. Wildcard reference panel (collapsible cheat sheet of available `{{wildcards}}`).

### 4. Wildcard resolution utility

`frontend/src/utils/resolveWildcards.ts` — pure function, no side effects:

```ts
resolveWildcards(
  template: JobTemplateModel,
  context: { now: Date; projectName: string; creatorName: string; occurrence: number }
): ResolvedTemplate
```

Parses `{{wildcard}}` and `{{wildcard±N}}` patterns with a regex, resolves each against `context`. Handles month/quarter/year rollover. Returns an object with resolved `title` and `description`.

### 5. Job creation modal (`NewJobModal.tsx`)

Add a `"Start from template"` select at the top (above the title field). Optional — selecting a template:

1. Calls `resolveWildcards()` with current context
2. Populates all form fields (title, description, client, priority, assignee, milestone, deadline)
3. If `assigneeMode === 'ASK'` — assignee field is left blank and focused
4. On form submit — calls `templatesApi.recordUsage()` alongside `jobsApi.create()`

Milestone field is hidden if the template's milestone no longer exists (graceful null handling).

## Migrations

| Migration | Content |
|-----------|---------|
| `V017__job_templates.sql` | `job_templates` table + indexes; `template_prefix` column on `org_settings`; `TEMPLATE` row seed in `org_sequences` foundation; `available = TRUE` on `JOB_TEMPLATES` addon |

## Alternatives considered

### Org-wide templates
More powerful but adds cross-project reference complexity (milestone and assignee references break across projects). Rejected for V1 — revisit based on demand.

### "Save as template" from existing job
Wildcards would not naturally exist in a job description, making the round-trip confusing. Rejected for V1 — dedicated create form is cleaner.

### Structured form templates (Linear-style)
Named fields with required/optional toggles. Significant implementation complexity for marginal gain. Markdown + HTML comment hints cover 80% of the value.

### Server-side wildcard resolution
Would require the backend to know about user timezone, display preferences, and project name at render time. Client-side is simpler and already has all the context needed.

### `[bracket]` wildcard syntax
Conflicts with Markdown link syntax `[text](url)`. `{{variable}}` is the Handlebars/Mustache convention — visually distinct, zero Markdown conflict.

## Consequences

### Positive
- Eliminates repetitive job creation for recurring work
- All wildcard resolution is client-side — no backend complexity for date/time handling
- Foundation for MIL-015 recurring jobs (schedule references a template; no template data model changes expected)
- `JOB_TEMPLATES` addon already seeded in the catalog — no subscription schema changes beyond setting `available = TRUE`

### Negative
- `AddonCode` enum requires a new value — minor backend change
- Friendly ID system gains a new entity type (`TEMPLATE`) — migration + enum update needed
- `occurrence_count` must be incremented atomically to avoid race conditions under concurrent use

### Neutral
- Job creation modal gains a new optional field — no existing flow is changed
- Job controller is unchanged — `/use` endpoint on the template controller handles occurrence tracking

## Implementation notes

1. `V017__job_templates.sql` migration
2. `FriendlyIdEntityType.TEMPLATE` + `org_settings.template_prefix`
3. `AddonCode.JOB_TEMPLATES` in backend enum
4. `JobTemplateModel` + `JobTemplateRepository` (jOOQ)
5. `JobTemplateService` with `incrementOccurrenceCount` atomic update
6. `JobTemplateController` + DTOs — all behind `@RequiresAddon(AddonCode.JOB_TEMPLATES)`
7. `templatesApi` frontend client
8. `resolveWildcards` utility with full arithmetic + rollover support
9. `TemplatesPage` + `TemplateFormModal`
10. `NewJobModal` — "Start from template" selector + `recordUsage` call on submit
11. `AppLayout` — nav item gated by `hasAddon('JOB_TEMPLATES')`

## References

- [ADR-0021: Milestone grouping](0021-milestone-grouping.md)
- [ADR-0031: Feature gating full stack](0031-feature-gating.md)
- [JOB-098: ADR — Job templates](https://46.101.205.77:8443/projects/PRJ-006/jobs/JOB-098)
