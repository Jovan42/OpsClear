# ADR-0034: Recurring Job Scheduling

**Status:** Accepted
**Date:** 2026-05-11
**Author:** Jovan Manojlovic

## Context

Teams repeatedly create the same jobs on a fixed cadence — a weekly deploy checklist, a monthly client invoice, a daily standup note, a quarterly compliance review. ADR-0032 introduced templates so the content of those jobs is no longer retyped, but the act of creating them is still manual. Templates also do not distribute work across a rotation of people, which is the usual reality for on-call style cadences.

This ADR introduces recurring schedules: a schedule defines **when** a job should fire and **who** the next assignee in a round-robin rotation is. The job content itself comes from a template — one template can drive any number of independent schedules, each with its own cadence and assignee list.

## Decision

Add a recurring scheduling system gated by the `RECURRING_SCHEDULING` addon (already seeded in the subscription model). A schedule is a project-scoped entity that references a `job_templates` row, carries a 6-field Spring cron expression with an IANA timezone, and owns an ordered list of assignees iterated round-robin.

A single `SchedulerPoller` running at fixed rate (60s) queries due schedules, materialises one job per due schedule via the existing template wildcard pipeline, advances the rotation, and recomputes the next firing time. Missed runs accumulated during downtime are recorded as `schedule_missed_runs` rows for manual review — never auto-flooded.

Pause/resume, expiry, future start dates, and indefinite pause due to empty rotation are all expressed through a single nullable `paused_until` timestamp — no separate boolean flags.

## Database schema

### `recurring_schedules` table

```sql
CREATE TABLE recurring_schedules (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id             UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    template_id            UUID         NOT NULL REFERENCES job_templates(id),
    name                   VARCHAR(100) NOT NULL,
    cron_expression        VARCHAR(100) NOT NULL,
    timezone               VARCHAR(60)  NOT NULL,
    paused_until           TIMESTAMPTZ,
    expires_at             TIMESTAMPTZ,
    current_rotation_index INT          NOT NULL DEFAULT 0,
    next_run_at            TIMESTAMPTZ  NOT NULL,
    last_run_at            TIMESTAMPTZ,
    created_by             UUID         NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX recurring_schedules_project_id_idx
    ON recurring_schedules (project_id);

CREATE INDEX recurring_schedules_due_idx
    ON recurring_schedules (next_run_at)
    WHERE paused_until IS NULL OR paused_until <= now();
```

Schedules do not need soft delete — deleting a schedule does not destroy any work product. Jobs that were already materialised carry `source_schedule_id` and outlive the schedule (FK is nullable on the jobs side, see below).

### `paused_until` semantics

A single nullable timestamp encodes every state previously imagined as three columns (`starts_at`, `is_active`, `paused_at`):

| Value | Meaning |
|-------|---------|
| `NULL` | Active — fires whenever `next_run_at <= now()` |
| Finite future timestamp | Paused — auto-resumes when the timestamp passes |
| `9999-01-01 00:00:00Z` | Sentinel for indefinite pause — only manual resume clears it |

Creating a schedule with a future start date sets `paused_until` to that date — no `starts_at` column needed. Empty-rotation auto-pause sets the sentinel.

The poller's due predicate is:

```sql
(paused_until IS NULL OR paused_until <= now())
AND next_run_at <= now()
AND (expires_at IS NULL OR expires_at > now())
```

`expires_at` is exclusive — the last fire is the last cron occurrence strictly before it.

### `schedule_assignees` table

```sql
CREATE TABLE schedule_assignees (
    id            UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id   UUID    NOT NULL REFERENCES recurring_schedules(id) ON DELETE CASCADE,
    user_id       UUID    NOT NULL REFERENCES users(id),
    "order"       INT     NOT NULL
);

CREATE UNIQUE INDEX schedule_assignees_position_idx
    ON schedule_assignees (schedule_id, "order");
```

Round-robin pick is `assignees[current_rotation_index % count]` ordered by `"order"`. The index is incremented after each successful job creation, never reset on pause/resume.

### `schedule_missed_runs` table

```sql
CREATE TABLE schedule_missed_runs (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id  UUID         NOT NULL REFERENCES recurring_schedules(id) ON DELETE CASCADE,
    expected_at  TIMESTAMPTZ  NOT NULL,
    recorded_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX schedule_missed_runs_schedule_id_idx
    ON schedule_missed_runs (schedule_id);
```

Rows are deleted on "Create job" (materialise) or "Dismiss". Cascade delete with the schedule removes orphan rows automatically.

### `jobs` table change

```sql
ALTER TABLE jobs
    ADD COLUMN source_schedule_id UUID REFERENCES recurring_schedules(id);
```

Nullable — `NULL` for manually created jobs. ON DELETE is the default (no action / restrict) — but in practice schedules are rarely deleted, and when they are the jobs simply keep a stale FK. The frontend tolerates a missing schedule by hiding the "Auto-created by schedule" label.

> **Note:** The FK has no `ON DELETE SET NULL` because the migration does not require it for V1 — schedule deletion is owner/admin only and an explicit user action. If this becomes painful we can revisit; the data damage is bounded to a UI label.

## API design

All schedule endpoints are project-scoped. Friendly IDs are not used — schedules are managed through their internal UUID. Every endpoint is gated by `@RequiresAddon(RECURRING_SCHEDULING)`.

### Schedule CRUD

```
GET    /api/projects/{projectId}/schedules
POST   /api/projects/{projectId}/schedules
GET    /api/projects/{projectId}/schedules/{scheduleId}
PATCH  /api/projects/{projectId}/schedules/{scheduleId}
DELETE /api/projects/{projectId}/schedules/{scheduleId}
```

`GET` list and detail require `MEMBER`. All write endpoints require `OWNER` or `ADMIN`. `DELETE` returns 204.

Request body (POST):

```json
{
  "templateId": "uuid",
  "name": "Weekly deploy",
  "cronExpression": "0 0 9 * * MON",
  "timezone": "Europe/Belgrade",
  "pausedUntil": "2026-06-01T00:00:00Z",
  "expiresAt": null,
  "assigneeIds": ["uuid", "uuid", "uuid"]
}
```

`assigneeIds` order is the rotation order. PATCH accepts any subset of the fields above — replacing `assigneeIds` rewrites the rotation but does not reset `current_rotation_index` (the modulo keeps the pointer valid).

### Pause / resume

```
POST /api/projects/{projectId}/schedules/{scheduleId}/pause
POST /api/projects/{projectId}/schedules/{scheduleId}/resume
```

Pause body: `{ "until": "<ISO timestamp>" }` for timed pause; omit the field or send `null` for indefinite (sets the sentinel `9999-01-01`). Resume always clears `paused_until` to `NULL`. Both require `OWNER` or `ADMIN`.

### Missed runs

```
GET    /api/projects/{projectId}/schedules/{scheduleId}/missed-runs
POST   /api/projects/{projectId}/schedules/{scheduleId}/missed-runs/{id}/materialize
DELETE /api/projects/{projectId}/schedules/{scheduleId}/missed-runs/{id}
POST   /api/projects/{projectId}/schedules/{scheduleId}/missed-runs/dismiss-all
```

`materialize` accepts the same payload as job creation overrides and returns 201 with the new job. Dismiss endpoints return 204. All require `OWNER` or `ADMIN`.

### Cron preview (auth required, no project scope)

```
POST /api/schedules/preview
```

Body: `{ "cronExpression": "...", "timezone": "..." }`. Returns `{ "nextRuns": ["<iso>", ...] }` — 5 entries computed from `now()` in the supplied timezone. Stateless — used by the form's "Next 5 runs" panel.

### Template deletion guard

The existing endpoints

```
DELETE /api/projects/{projectId}/templates/{id}
DELETE /api/organisations/{orgId}/templates/{id}
```

gain a 409 guard: if any non-paused schedule references the template, the response is

```json
{
  "error": "Conflict",
  "message": "Template is used by active schedules: [Weekly deploy, Monthly invoice]"
}
```

Non-paused = `paused_until IS NULL`. The user must pause or delete the schedules before retrying.

## Backend changes

### 1. `AddonCode` enum

`RECURRING_SCHEDULING` already exists — no enum change.

### 2. Models

Three jOOQ-backed models (consistent with the rest of the codebase, which uses jOOQ records rather than JPA entities for queries):

```java
RecurringScheduleModel      // schedule row + joined assignees + last_run_at, next_run_at, etc.
ScheduleAssigneeModel       // schedule_id, user_id, order, joined userName
ScheduleMissedRunModel      // schedule_id, expected_at, recorded_at
```

### 3. Repositories

```
RecurringScheduleRepository
  findDue()                                  // poller query: due predicate above
  findByProjectId(UUID projectId)
  findById(UUID id)
  findByTemplateIdAndActive(UUID templateId) // for the deletion guard
  save(RecurringScheduleModel)
  delete(UUID id)
  updateAfterRun(id, nextRunAt, lastRunAt, rotationIndex)  // single atomic update

ScheduleAssigneeRepository
  findBySchedule(UUID scheduleId)            // ordered by "order"
  replaceForSchedule(scheduleId, List<UUID>) // delete + insert in one tx
  deleteByUserId(UUID userId)                // called from ProjectMemberService

ScheduleMissedRunRepository
  findBySchedule(UUID scheduleId)
  insert(scheduleId, expectedAt)
  deleteById(UUID id)
  deleteAllForSchedule(UUID scheduleId)
```

### 4. `RecurringScheduleService`

Write methods `@Transactional`, reads `@Transactional(readOnly = true)`. Guards:

```
list(projectId, requesterId)        → requireMember
get(scheduleId, requesterId)        → requireMember
create(...)                         → requireOwnerOrAdmin
update(...)                         → requireOwnerOrAdmin
delete(...)                         → requireOwnerOrAdmin
pause(scheduleId, until, requester) → requireOwnerOrAdmin
resume(scheduleId, requester)       → requireOwnerOrAdmin
```

On create/update, `cron_expression` is validated as a parseable 6-field Spring cron AND the minimum interval between any two consecutive fires must be ≥ 1 hour (computed by parsing and stepping `next()` twice). Violations throw `ValidationException` → 400.

Empty-rotation hook (`onAssigneeRemoved(scheduleId)`) — called from `ProjectMemberService` after a member leaves the project: deletes their `schedule_assignees` rows; if the rotation count for the schedule reaches zero, sets `paused_until = '9999-01-01'` and the UI shows the "Paused — no assignees" badge.

### 5. `SchedulerPoller`

```java
@Component
public class SchedulerPoller {
    @Scheduled(fixedRate = 60_000)
    public void tick() { ... }
}
```

Per tick:

1. Load all due schedules via `RecurringScheduleRepository.findDue()`.
2. For each schedule, compare `next_run_at` against `now()`:
   - If `next_run_at` is more than one cron period in the past — insert one `schedule_missed_runs` row with `expected_at = next_run_at`, advance `next_run_at` to the next cron occurrence, and repeat until `next_run_at` is within one period of `now()`. No jobs are auto-created for missed runs.
   - Otherwise — materialise one job from the template, increment `current_rotation_index`, compute the new `next_run_at` via `CronExpression.parse(cron).next(ZonedDateTime.now(ZoneId.of(timezone)))`, set `last_run_at = now()`.
3. If `expires_at` is set and `next_run_at >= expires_at` — leave the schedule in place but the due predicate naturally excludes it from future ticks.

V1 assumes a single backend instance. Row-level locking is not added; the poller is the only writer of `next_run_at` and `current_rotation_index`.

### 6. Wildcard resolution for auto-created jobs

Reuses the wildcard system from ADR-0032 with these specifics:

| Wildcard | Resolves to |
|----------|-------------|
| `{{assignee}}` | Display name of the round-robin selected user |
| `{{date}}`, `{{day}}`, `{{month}}`, `{{year}}`, `{{week}}`, `{{quarter}}` | Derived from `scheduled_for` — **not** from `now()`. For normal runs `scheduled_for = next_run_at`; for materialised missed runs `scheduled_for = expected_at` |
| `{{occurrence}}` | `occurrence_count + 1` at materialisation time (same atomic increment as manual template use) |
| `{{creator}}` | Literal string `"System"` |
| Any unresolved wildcard (e.g. `{{client}}`) | **Left as literal text** in the job — no validation, no blocking. The assignee edits the job manually if needed |

`deadline = scheduled_for + template.deadline_offset_days` when the offset is set; otherwise `NULL`.

### 7. Template deletion guard

`JobTemplateService.delete(...)` and the org-level equivalent each call `RecurringScheduleRepository.findByTemplateIdAndActive(templateId)` first and throw `ConflictException` (mapped to 409 by `GlobalExceptionHandler`) listing the schedule names if non-empty.

### DTOs

```
RecurringScheduleResponse   — schedule + assignees + computed nextRunAt in UTC + status badge ("ACTIVE" | "PAUSED" | "PAUSED_NO_ASSIGNEES" | "EXPIRED")
ScheduleAssigneeResponse    — userId, userName, order
ScheduleMissedRunResponse   — id, expectedAt, recordedAt
CreateScheduleRequest       — templateId, name, cronExpression, timezone, pausedUntil, expiresAt, assigneeIds
UpdateScheduleRequest       — all fields optional, PATCH semantics
PauseScheduleRequest        — until (nullable)
PreviewCronRequest          — cronExpression, timezone
PreviewCronResponse         — nextRuns (List<Instant>, 5 entries)
```

`status` is computed server-side from `paused_until`, `expires_at`, and assignee count.

## Frontend changes

### 1. API client

New `schedulesApi` in `frontend/src/api/schedules.ts`:

```ts
list(projectId)
get(projectId, scheduleId)
create(projectId, body)
update(projectId, scheduleId, body)
delete(projectId, scheduleId)
pause(projectId, scheduleId, until)
resume(projectId, scheduleId)
listMissedRuns(projectId, scheduleId)
materializeMissedRun(projectId, scheduleId, missedRunId)
dismissMissedRun(projectId, scheduleId, missedRunId)
dismissAllMissedRuns(projectId, scheduleId)
preview(cronExpression, timezone)
```

### 2. Nav item

In `AppLayout.tsx` `ProjectNav`, add a Schedules entry beside Templates:

```ts
const schedulesLocked = !hasAddon('RECURRING_SCHEDULING');
```

Render the nav link normally when `!schedulesLocked`, `lockedNavLink()` otherwise.

### 3. Schedules page (`frontend/src/features/schedules/`)

- `SchedulesPage.tsx` — table view of all schedules in the project.
  - Columns: name, template (linked to template page), cron in human-readable form (via `cronstrue`), next run in the user's local timezone, assignee rotation avatars, status badge (Active / Paused / Paused — no assignees / Expired), missed-runs count badge.
  - "New schedule" button (owner/admin). Row actions: edit, pause/resume, delete, expand to show missed runs.
- `ScheduleFormModal.tsx` — create/edit form.
  - Template picker (loads from `templatesApi.list`).
  - Cadence presets: Daily / Weekly (day picker) / Monthly (day-of-month picker). Each preset generates the cron string. "Advanced" toggle exposes a raw 6-field cron input.
  - `cronstrue` renders live human-readable translation under the input.
  - "Next 5 runs" panel — calls `schedulesApi.preview()` debounced on every cron change.
  - Assignee picker — ordered list with drag handles. Order in the list is the rotation order.
  - Optional start date (sets `pausedUntil`) and expiry date (`expiresAt`).
- `PauseDialog.tsx` — radio options: 1 day / 1 week / 1 month / Custom date / Indefinitely. Submits via `schedulesApi.pause`.
- `MissedRunsPanel.tsx` — expandable section under each schedule row. Lists `expected_at` per row with "Create job" and "Dismiss" buttons. Bulk "Dismiss all" at the top.
- `CreateJobFromMissedRunModal.tsx` — pre-fills the standard `NewJobModal` from the template and overrides `deadline = expected_at + template.deadline_offset_days`.

### 4. Template page entry point

Each template card on the project Templates page (and the org Templates tab) gains a "Schedule this template" button that opens `ScheduleFormModal` pre-filled with that `templateId`. Hidden when the user is not owner/admin or the addon is not active.

### 5. Job detail label

In `JobDetailPage.tsx`, when the job has `sourceScheduleId`, show a small badge "Auto-created by schedule [name]" linking to the schedule on the Schedules page. If the schedule is no longer fetchable (404), the badge is hidden silently.

### 6. Hooks

TanStack Query hooks:

```ts
useSchedules(projectId)
useSchedule(projectId, scheduleId)
useScheduleMissedRuns(projectId, scheduleId)
useSchedulePreview(cronExpression, timezone)   // debounced enabled flag
```

Mutations follow the standard pattern (invalidate `['schedules', projectId]` on success).

### 7. Dependency

Add `cronstrue` to `frontend/package.json` for human-readable cron translation. Already supports the standard 5-field cron — Spring's 6-field form requires stripping the leading seconds before passing it in (utility wrapper in `frontend/src/utils/cron.ts`).

### 8. Timezones

All `nextRunAt`, `lastRunAt`, `expectedAt`, and `pausedUntil` values are stored and transferred in UTC. The frontend renders them in the browser's local timezone using `Intl.DateTimeFormat`. The schedule's own `timezone` field is used only on the backend for cron evaluation and is shown in the form for clarity.

## Migrations

| Migration | Content |
|-----------|---------|
| `V021__recurring_schedules.sql` | `recurring_schedules`, `schedule_assignees`, `schedule_missed_runs` tables + indexes; `source_schedule_id` column on `jobs` |

After migration, run `./gradlew generateJooq` to regenerate jOOQ classes.

## Alternatives considered

### Spring `@Scheduled` per schedule (dynamic registration)

Register a Spring task per active schedule at startup, re-register on edits. Rejected — complex in-memory state, no clean pause/resume, painful recovery after restart, and no natural way to record missed runs.

### Quartz Scheduler

Industry-standard job scheduling library with persistent storage and clustering. Rejected — significant dependency and ops overhead for a feature that fits comfortably inside a 60-second DB poll loop. Revisit only if multi-instance scheduling becomes a hard requirement.

### Schedule embedded in template (1:1)

Adding `cron_expression`, `timezone`, etc. directly on `job_templates`. Rejected — users want one template (e.g. "Deploy checklist") to drive multiple independent schedules (weekly for the backend team, monthly for the mobile team, ad-hoc for releases). Splitting templates from schedules is the natural model.

### Linked-list round-robin (`next_user_id` FK on assignees)

Pointer-style rotation. Rejected — reordering and inserting in the middle becomes messy. Index-based with an `order` column is simpler and matches how the UI displays the rotation.

### Catch-up on missed runs (auto-create all on recovery)

After downtime, materialise every missed occurrence as a real job. Rejected — extended downtime would flood projects with stale, no-longer-relevant work. Recording the misses and letting the user choose per-occurrence is the safer default.

### Three columns: `starts_at`, `is_active`, `paused_at`

The initial draft. Rejected in favour of the single `paused_until` field — it cleanly expresses all four states (active, future start, timed pause, indefinite pause) with no opportunity for inconsistent combinations and no application-level state machine.

## Consequences

### Positive

- One template can drive many schedules — natural fit for "same job, different cadences or teams"
- Single `paused_until` field replaces three booleans/timestamps — fewer ways to end up in an invalid state
- Missed runs are captured, not lost, and never auto-flood the project
- DB-polling poller is trivially debuggable, restart-safe, and uses zero new infrastructure
- Reuses the existing template wildcard pipeline — no new resolution rules to learn
- Foundation for future "rotation calendars" view if needed

### Negative

- Polling interval (60s) means schedules fire with up to ~1 minute of jitter — acceptable for the cadences this serves
- Single-instance assumption: running two backend instances without row-level locking would produce duplicate jobs. Documented; revisit if/when we scale horizontally
- Template deletion now has a 409 path — frontend must surface a clear error
- `jobs.source_schedule_id` is a new nullable FK every job query must tolerate
- `current_rotation_index` is a monotonically increasing integer — eventual overflow is theoretical (`INT` allows ~68 years of hourly fires) and not addressed in V1

### Neutral

- `RECURRING_SCHEDULING` addon already seeded — no subscription schema changes
- Cron syntax surfaces to end users via presets, not raw cron — the "Advanced" toggle exists for power users
- Schedules are not soft-deleted — deleting a schedule is a deliberate destructive action

## Implementation order

1. `V021__recurring_schedules.sql` migration + `./gradlew generateJooq`
2. `RecurringScheduleRepository`, `ScheduleAssigneeRepository`, `ScheduleMissedRunRepository`
3. `RecurringScheduleService` — CRUD, pause/resume, round-robin advancement, empty-rotation hook from `ProjectMemberService`
4. `SchedulerPoller` — fixed-rate polling, job materialisation via template, `next_run_at` computation, wildcard resolution with `scheduled_for` semantics
5. Missed-run detection and storage inside the poller
6. REST controllers — schedule CRUD, pause/resume, missed-run endpoints, `/api/schedules/preview`
7. Template deletion 409 guard in `JobTemplateService` and `OrgTemplateController`
8. `schedulesApi` + TanStack Query hooks
9. `SchedulesPage` + `ScheduleFormModal` (presets + cronstrue + next-5-runs preview) + `PauseDialog`
10. `MissedRunsPanel` + `CreateJobFromMissedRunModal`
11. `source_schedule_id` badge on `JobDetailPage`
12. "Schedule this template" entry point on project and org Templates pages
13. `AppLayout` — Schedules nav item gated by `hasAddon('RECURRING_SCHEDULING')`

## References

- [ADR-0031: Feature gating full stack](0031-feature-gating.md)
- [ADR-0032: Job templates](0032-job-templates.md)
- [ADR-0033: Org-level job templates](0033-org-level-templates.md)
- [JOB-099: ADR — Recurring job scheduling](https://46.101.205.77:8443/projects/PRJ-006/jobs/JOB-099)
