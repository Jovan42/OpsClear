# ADR-0042: Job Types

**Status:** Proposed
**Date:** 2026-07-23
**Author:** Jovan Manojlovic

## Context

Once a project accumulates enough jobs, status alone isn't enough signal — owners need to see the composition of work at a glance (how many bugs vs. features, what the backlog looks like by category), and filter/dashboard by that classification without relying on search.

## Decision

Add a per-project job type system: each project defines its own set of types (name + color), jobs carry an optional type, the job list gains a type filter, the dashboard gains a type-breakdown widget, and job templates gain a default type that pre-fills on use.

## Product decisions

- Types are defined **per project**, not org-wide — projects in the same org can have completely different type vocabularies (a dev project might have Bug/Feature/Chore; a sales project might have Invoice/Call/Proposal).
- Each type has a name and a color; display order is user-controlled. **Color is chosen from a fixed, curated swatch set, not a free-form picker** — a small palette drawn from Tailwind's existing shade family already used elsewhere in the app for badges (`--color-{hue}-500`): red, orange, amber, green, teal, blue, indigo, purple, pink, gray. Ten options is enough hue separation for a per-project list that's realistically a handful of types, without the paradox-of-choice a full palette would add. Stored as a **DB-level enum** (see Database below), not a raw hex string — enforced at the schema level, not just by application validation.
- A job's `type_id` is nullable — no type is a valid, unremarkable state, not an error.
- **No cap on the number of types per project for V1.** An unreadable dashboard from too many types is self-limiting (the owner creating them won't want that either); adding an artificial cap now would be solving a problem not yet observed.
- **A type carries no behavior beyond classification for V1** — no required fields, no restricted transitions tied to a type. Purely a label. Anything more turns this into a workflow-engine feature, a much bigger and riskier scope than validated demand supports right now.
- Job list gains a type filter; dashboard gains a type-breakdown widget, both degrading gracefully (hidden or empty-stated) when a project has no types defined yet.
- **Gated behind a new `JOB_TYPES` add-on**, consistent with how the rest of the app is monetized — only `job-tracking` and `blockage` are free/Included today; everything of comparable substance (Dashboard, Milestones, Job relationships, Notes, etc.) is a paid add-on, and this is scoped similarly to Milestones/Job relationships. Priced at 1490 RSD/mo (matching those two), seeded `available: false` and flipped to `true` in a follow-up migration once ready to sell, per the precedent already established for `JOB_TEMPLATES`/`RECURRING_SCHEDULING`.

## Technical design

### Database
- `job_types` table: `id`, `project_id` (FK), `name`, `color` (Postgres enum `job_type_color`: `RED`, `ORANGE`, `AMBER`, `GREEN`, `TEAL`, `BLUE`, `INDIGO`, `PURPLE`, `PINK`, `GRAY`), `display_order`, timestamps. The enum-to-hex mapping (e.g. `RED` → `#ef4444`) lives in application code (frontend and backend), not the database — the DB only stores the fixed set of names.
- `jobs.type_id` — nullable FK to `job_types.id`, `ON DELETE SET NULL`.
- `job_templates.default_type_id` — nullable FK to `job_types.id`, for project-scoped templates, `ON DELETE SET NULL` (same safety-net treatment as `jobs.type_id`).
- `job_templates.default_type_name` — nullable text, for org-scoped templates (see Template system interaction below).
- Addon catalog entry: `JOB_TYPES`, 1490 RSD/mo, `available: false` initially (activated in a follow-up migration when ready to sell, per the `JOB_TEMPLATES`/`RECURRING_SCHEDULING` precedent).

### API
- CRUD endpoints for types under `/api/projects/{projectId}/job-types`, gated by `@RequiresAddon(AddonCode.JOB_TYPES)`.
- `DELETE /api/projects/{projectId}/job-types/{typeId}` — **service-layer guard**: returns 409 if any job *or* project-scoped template in the project still references the type ("Cannot delete type: N job(s) and M template(s) still use this type"), the same shape as the existing job-template deletion guard (blocked while an active schedule references it). Both jobs and templates are checked in the same guard rather than treating template references as lower-stakes — avoids an inconsistent experience where deleting a type sometimes warns and sometimes doesn't depending on what happens to reference it. The delete only proceeds once nothing references the type. The `ON DELETE SET NULL` constraints on `jobs.type_id` and `job_templates.default_type_id` stay as a DB-level safety net for any path that might bypass the guard — in normal operation, the guard is what a user actually experiences; the constraints just prevent broken references if it's ever bypassed some other way.
- `jobs` list endpoint gains a `type_id` filter param.
- Dashboard endpoint gains a type-breakdown aggregate.
- Template CRUD endpoints (`/api/projects/{projectId}/templates`, `/api/organisations/{orgId}/templates`) gain `default_type_id` (project-scoped) / `default_type_name` (org-scoped) on request/response bodies.
- Template "use" endpoint (`POST .../templates/{templateId}/use`) resolves the default type at creation time: project-scoped templates copy `default_type_id` directly onto the new job; org-scoped templates look up `default_type_name` (case-insensitive) against the target project's types and set `type_id` if a match is found, otherwise leave it blank.

### Backend
- `JobTypeService`: CRUD + the delete guard described above (`requireNoJobsOrTemplatesReference(typeId)` before allowing delete — checks both `jobs.type_id` and `job_templates.default_type_id`). `color` is a jOOQ-generated enum type — invalid values are rejected at the database level, not just by application validation, even if sent directly via the API.
- `JobService`: accept optional `type_id` on create/update; no validation beyond "must belong to the same project" if set.
- `JobTemplateService`: accept optional `default_type_id`/`default_type_name` on template create/update (validated per template scope — project-scoped templates only accept `default_type_id`, org-scoped only accept `default_type_name`); resolve the default type when a template is used, per the API section above.

### Frontend
- Type management UI (create/edit/delete/reorder) — likely under project settings, gated by `hasAddon('JOB_TYPES')`. Color chosen via a swatch picker (the 10 fixed options), not a raw color input; a small frontend constant maps each enum value to its hex code for rendering.
- Colored badge on job rows/detail showing the assigned type.
- Type filter control on the job list.
- Dashboard type-breakdown widget (degrades gracefully to hidden/empty-stated when no types exist).
- Template create/edit forms gain a default-type field: a type picker for project-scoped templates, a free-text/autocomplete name field for org-scoped templates.
- `/features` gains a `job-types` card (per ADR-0040's interactive-demo pattern, once this add-on is ready to sell).

### Constraints & edge cases
- Must not break existing jobs — `type_id` nullable, `ON DELETE SET NULL` as described above.
- Dashboard widget must degrade gracefully with zero types defined — no broken/empty-looking chart.
- `color` as a DB enum means adding an eleventh swatch later requires a migration (`ALTER TYPE ... ADD VALUE`) rather than a config change — an accepted tradeoff for the stronger guarantee of enforcing the fixed set at the schema level, not just in application code.

### Template system interaction

Templates can be **org-scoped** or **project-scoped**, but types are **project-scoped only** — an org-level template's "default type" can't hold a single `type_id` FK, since that ID is only meaningful within one specific project; it has no meaning (or a different meaning) in another project.

Resolution:
- **Project-scoped templates** get a real `default_type_id` (nullable FK to `job_types.id`, same project — no ambiguity).
- **Org-scoped templates** instead store a `default_type_name` (nullable text), matched by name (case-insensitive) against the target project's types at the moment the template is used. If no match is found in that project, the created job's type is left blank rather than erroring.

This ships as part of this phase, not deferred — see Implementation order.

## Alternatives considered

### Silent `ON DELETE SET NULL` with no service-level guard

Rejected — a type actively used for filtering and dashboard stats disappearing from jobs with no warning is a bad surprise, inconsistent with how the codebase already handles this exact shape of problem (the job-template deletion guard).

### Guard only checks jobs, treats template references as lower-stakes (silent `SET NULL` for templates)

Considered, on the reasoning that a template's default type is "just a convenience default," not committed data like a job's actual type. Rejected in favor of checking both in the same guard — an inconsistent experience (deleting a type warns you sometimes but not others, depending on what happens to reference it) is more confusing than the small cost of one extra check.

### Cap on types per project

Rejected for V1 — no evidence this becomes a real problem, matches the "unlikely to matter for MVP" reasoning already used elsewhere (e.g. Notes, per ADR-0009).

### Types carrying behavior (required fields, restricted transitions)

Rejected for V1 — a much bigger scope than validated demand supports, and works against "simple over complex."

### Single `type_id` FK on templates regardless of scope

Rejected — doesn't compose across org-scoped templates applied to different projects, since types don't exist at org scope. Name-matching for org-scoped templates resolves this without requiring types to become org-level, which would contradict the core per-project design decision.

### Free-form color picker / raw hex string

Rejected in favor of a fixed, curated swatch set stored as a DB enum. An open color picker risks type badges that clash with the rest of the UI or are hard to distinguish from each other; a raw hex string with only application-level validation would still let a direct API call bypass the constraint. The DB enum enforces the fixed set at the schema level.

### Included/free feature rather than a paid add-on

Considered, since job types could be framed as an extension of the already-free `job-tracking` feature. Rejected — it's comparable in scope to existing paid add-ons like Milestones and Job relationships (a new table, CRUD, dashboard widget), and this phase (PRJ-008) is explicitly about strengthening monetization, so treating a substantial new feature as free would work against that goal without a specific reason to.

## Consequences

### Positive

- Owners get real compositional visibility into their work (bug vs. feature, category breakdown) without relying on search or memory
- Type deletion is safe by construction — guard prevents silent, confusing data loss; DB constraint prevents broken references if the guard is ever bypassed
- Template integration ships as part of this phase — no redesign needed later, no gap where templates and types don't talk to each other
- Fits the app's existing monetization pattern rather than being an inconsistent exception

### Negative

- Another per-project configuration surface (types) alongside milestones, templates, block reasons — adds to what a new project owner has to set up, though entirely optional (nullable `type_id`)
- Org-scoped templates' name-matching for default type is best-effort, not guaranteed — a renamed or missing type in the target project silently results in no default type, rather than an error

### Neutral

- No behavior beyond classification for V1 — a deliberate scope cut, not a technical limitation; could be revisited if real demand emerges
- Dashboard widget addition is purely additive — no changes to existing dashboard widgets

## Implementation order

1. `job_types` table (with `job_type_color` enum) + `jobs.type_id` migration + `JOB_TYPES` addon catalog entry (`available: false`)
2. `JobTypeService` (CRUD + delete guard) + `JobTypeController`, gated by `@RequiresAddon(AddonCode.JOB_TYPES)`
3. `JobService` — accept optional `type_id` on create/update
4. Type filter on the jobs list endpoint; type-breakdown aggregate on the dashboard endpoint
5. `job_templates.default_type_id`/`default_type_name` migration + `JobTemplateService` changes + default-type resolution on template use
6. Frontend: type management UI (swatch picker), colored badges, job list filter, dashboard widget, template form default-type fields
7. Follow-up migration to activate the `JOB_TYPES` addon (`available: true`) once ready to sell, per the established precedent
8. `/features` interactive demo card for Job types (per ADR-0040), once the addon is active

## References

- JOB-120 (Future Consideration, promoted to PRJ-008/MIL-024): original scoping notes this ADR implements
- `JOB-111`: job-template deletion 409 guard — the precedent this ADR's delete-guard pattern follows
