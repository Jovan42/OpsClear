# ADR-0035: Job and Project Links

**Status:** Proposed
**Date:** 2026-07-20
**Author:** Jovan Manojlovic

## Context

External resources relevant to a job or project — a GitHub PR, a Figma file, a Notion doc, a Jira ticket — currently have nowhere structured to live. They get pasted into a job description or buried in a Note, where they're hard to find later. The immediate driver is attaching the GitHub PR that implements a job directly to that job.

This introduces a dedicated Links feature at two scopes:

- **Job-level** — a Links section on the job detail page, same placement as Notes and Approvals.
- **Project-level** — a persistent Links entry point in the project nav bar, for resources relevant to the whole project rather than one job (e.g. the Figma file for the whole redesign, the shared Notion wiki).

Both scopes are covered by this ADR — narrowing to job-level only was considered and rejected (see Alternatives); the two are cheap to build together since they share the same backend shape.

## Decision

Add a `JOB_LINKS` addon (490 RSD/month) gating both scopes. Each link stores a URL, an optional label, and creator metadata. Links are **mutable** (unlike Notes) — editable and deletable — because they are shortcuts to external resources, not an audit record; a wrong or stale URL must be fixable without leaving a permanent trace of the mistake.

Permissions: any project member can add a link; only Owner/Admin can edit or delete one. Same access tier as everything else — job/project membership already gates visibility.

## Database schema

Two tables, one per scope — mirroring how `job_relationships` and `schedule_assignees` are separate tables per relationship type rather than a single polymorphic table.

```sql
CREATE TABLE job_links (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID         NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    url        TEXT         NOT NULL,
    label      VARCHAR(100),
    created_by UUID         NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX job_links_job_id_idx ON job_links (job_id);

CREATE TABLE project_links (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    url        TEXT         NOT NULL,
    label      VARCHAR(100),
    created_by UUID         NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX project_links_project_id_idx ON project_links (project_id);
```

**No `order` column, no `deleted_at`.** "Ordered by insertion" is satisfied by `ORDER BY created_at ASC, id ASC` — editing a link changes `updated_at`, not `created_at`, so its position never moves. No soft delete: consistent with `notes`, `job_relationships`, and `schedule_assignees`, all of which hard-delete or cascade from the parent rather than following the top-level entity soft-delete pattern. `ON DELETE CASCADE` handles job/project deletion; there is no independent lifecycle to preserve.

Addon catalog entry, appended in the same migration:

```sql
INSERT INTO subscription_addons (key, name, price_monthly, price_annual, available, display_order) VALUES
  ('JOB_LINKS', 'Job links', 490, 408, FALSE, 10);
```

`available = FALSE` follows the `JOB_TEMPLATES` / `RECURRING_SCHEDULING` precedent — a follow-up migration flips it to `TRUE` when the feature is ready to sell (see `V018__activate_job_templates_addon.sql` for the pattern).

## URL validation

Loose — no scheme allow-list (so `mailto:`, `ftp:`, etc. all work, matching the "links to anything" intent). The one hard rule, non-negotiable regardless of strictness preference: reject `javascript:`, `data:`, and `vbscript:` schemes. Links render as real `<a href>` elements opened by any project member — an unvalidated `javascript:` URL is a stored self-XSS vector, not a strictness tradeoff. Enforced once, in `LinkService`, via a scheme blocklist checked against `java.net.URI.getScheme()`. Invalid URLs throw `ValidationException` → 400.

## Icon detection

Entirely client-side, from the URL hostname — never stored in the DB (icon choice must stay independent of the URL's lifetime, and services rebrand).

Fallback chain:
1. `simple-icons` for known services: GitHub, GitLab, Figma, Notion, Jira, Confluence, Linear, Slack, Vercel.
2. Google favicon service for unknown hosts: `https://www.google.com/s2/favicons?domain={hostname}&sz=32`.
3. Generic chain-link icon (Lucide) if the favicon request fails.

If the user leaves the label field blank when adding a link, the frontend pre-fills it with the detected service name before submit (e.g. pasting a GitHub PR URL auto-fills "GitHub"). `label` stays nullable in the schema — if a saved link genuinely has no label (detection found nothing and the user left it blank), the row renders using the hostname as a display fallback rather than blank text.

## Permissions

- **Add:** any project member (Owner, Admin, Member) — same rationale as Notes: whoever is doing the work should be able to attach the PR/doc they're using, without waiting on a manager.
- **Edit / delete:** Owner and Admin only. Unlike Notes, links are meant to be curated — a wrong URL from anyone should be fixable by whoever manages the project, not necessarily only its original creator.

## API design

```
POST   /api/projects/{projectId}/jobs/{jobId}/links
PUT    /api/projects/{projectId}/jobs/{jobId}/links/{linkId}
DELETE /api/projects/{projectId}/jobs/{jobId}/links/{linkId}

POST   /api/projects/{projectId}/links
PUT    /api/projects/{projectId}/links/{linkId}
DELETE /api/projects/{projectId}/links/{linkId}
```

No `GET` endpoints — links are embedded directly in `JobResponse` and `ProjectResponse` (see below), consistent with how `relationships` is already embedded in `JobResponse`. `POST` returns 201 with the created `LinkResponse`; `PUT` returns 200 with the updated `LinkResponse`; `DELETE` returns 204. Every endpoint is gated by `@RequiresAddon(AddonCode.JOB_LINKS)`.

Request body (POST and PUT — PUT is a full replace, no partial update):

```json
{ "url": "https://github.com/opsclear/opsclear/pull/226", "label": "GitHub PR #226" }
```

`url`: `@NotBlank`, scheme-blocklist validated in the service. `label`: optional, `@Size(max = 100)`.

Response:

```json
{
  "id": "...",
  "url": "https://github.com/opsclear/opsclear/pull/226",
  "label": "GitHub PR #226",
  "createdBy": "...",
  "createdAt": "2026-07-20T10:00:00Z",
  "updatedAt": "2026-07-20T10:00:00Z"
}
```

`createdBy` is a UUID, not a nested user object — same convention as `blockedBy` and `NoteResponse.authorId`. The frontend resolves the display name from the project members list it already holds.

## Backend changes

### 1. `AddonCode` enum

Add `JOB_LINKS`.

### 2. Models

```java
JobLinkModel      // id, jobId, url, label, createdBy, createdAt, updatedAt
ProjectLinkModel  // id, projectId, url, label, createdBy, createdAt, updatedAt
```

### 3. Repositories

```
JobLinkRepository
  findByJobId(UUID jobId)          // ORDER BY created_at ASC, id ASC
  findById(UUID id)
  save(JobLinkModel)                // id == null → insert, else update
  delete(UUID id)

ProjectLinkRepository
  findByProjectId(UUID projectId)   // ORDER BY created_at ASC, id ASC
  findById(UUID id)
  save(ProjectLinkModel)
  delete(UUID id)
```

### 4. `LinkService`

One service covers both scopes (same validation, same permission shape — a thin service per scope would just duplicate the guard bodies).

```
listByJob(projectId, jobId, callerId)              → requireMember
createForJob(projectId, jobId, url, label, callerId) → requireMember, validateUrl
updateJobLink(projectId, jobId, linkId, url, label, callerId) → requireOwnerOrAdmin, requireLinkBelongsToJob, validateUrl
deleteJobLink(projectId, jobId, linkId, callerId)   → requireOwnerOrAdmin, requireLinkBelongsToJob

listByProject(projectId, callerId)                  → requireMember
createForProject(projectId, url, label, callerId)   → requireMember, validateUrl
updateProjectLink(projectId, linkId, url, label, callerId) → requireOwnerOrAdmin, requireLinkBelongsToProject, validateUrl
deleteProjectLink(projectId, linkId, callerId)      → requireOwnerOrAdmin, requireLinkBelongsToProject
```

`validateUrl` (private guard, grouped with the others under `// --- Guards ---`): parses with `java.net.URI`, throws `ValidationException` if the scheme is in the blocklist (`javascript`, `data`, `vbscript`) or the URL doesn't parse.

Write methods `@Transactional`, reads `@Transactional(readOnly = true)`. `log.info(...)` on create/update/delete, matching the mutation-logging convention.

### 5. Controllers

`JobLinkController` and `ProjectLinkController`, both thin — `SecurityUtils.resolveUserId`, `FriendlyIdResolver.resolveProject` / `resolveJob`, delegate to `LinkService`, map to `LinkResponse::from`.

### 6. `ErrorMessages`

New nested class `ErrorMessages.Link`:
```java
NOT_FOUND = "Link not found"
INVALID_URL = "URL is invalid or uses a disallowed scheme"
WRONG_JOB = "Link does not belong to this job"
WRONG_PROJECT = "Link does not belong to this project"
```

### 7. `JobResponse` / `ProjectResponse`

Both gain a `List<LinkResponse> links` field, populated on every fetch (list and detail) — same as `relationships` on `JobResponse`, no separate "populated only on getById" carve-out since link lists are expected to stay small.

## Frontend changes

### 1. Dependency

Add `simple-icons` to `frontend/package.json`. Icon-detection utility lives in `frontend/src/utils/linkIcon.ts` — hostname → icon lookup, then favicon fallback, then generic Lucide chain-link icon.

### 2. API client

`frontend/src/api/links.ts`:

```ts
jobLinksApi = { create(projectId, jobId, body), update(projectId, jobId, linkId, body), delete(projectId, jobId, linkId) }
projectLinksApi = { create(projectId, body), update(projectId, linkId, body), delete(projectId, linkId) }
```

No `list` methods — links arrive embedded in the job/project the caller already fetched. Mutations invalidate `['jobs', projectId, jobId]` or `['projects', projectId]` on success, same pattern as every other job/project mutation.

### 3. Job-level UI

`LinksSection.tsx` on `JobDetailPage`, positioned below the description, collapsible — same shell as the Notes and Approvals sections. Row: icon (from `linkIcon.ts`) + label (opens URL in a new tab, `rel="noopener noreferrer"`) + copy-URL button + edit pencil (Owner/Admin) + delete × (Owner/Admin). A muted "Added by {name}" sits as small secondary text under the label, resolved from the project members list already loaded — same treatment as the recent schedule-badge simplification (subtle metadata line, not a prominent badge).

Adding: inline form at the bottom of the section (URL input, label input auto-filled on paste via the icon-detection utility). Editing: the row swaps to the same inline form, pre-filled. Deleting: opens the existing `ConfirmModal` (`variant="danger"`) — no new undo-toast mechanism; reusing the shared confirmation component is simpler than building and testing a delayed-delete-with-cancel flow for what is explicitly a low-stakes action.

When `JOB_LINKS` is inactive: no `LinksSection` is rendered at all. Instead "Links" is added to the same `lockedSections` array already aggregated on `JobDetailPage` (alongside Notes, Approvals, etc.) and shown once via the existing `LockedSectionRow` — no new locked-state component.

### 4. Project-level UI

In `AppLayout.tsx`'s `ProjectNav`, add a Links entry using the same `lockedNavLink()` teaser pattern as Dashboard/Milestones/Templates/Schedules — visible with a small lock icon when `!hasAddon('JOB_LINKS')` rather than fully hidden, for consistency with the rest of that nav row (this differs from the job-level section, which fully hides — the nav strip's own established convention is teaser-on-lock, the job-detail page's is hide-on-lock, and Links follows whichever its own placement already does).

When unlocked, the entry is a button (not a `NavLink`) showing a small chain-link icon when the project has zero links, or "Links (N)" once it has any — clicking opens `ProjectLinksDropdown.tsx`, a floating panel, not a route. Dropdown body: list of links (icon + label + edit + delete for Owner/Admin, read-only list for Members) plus an inline add form at the bottom, same interaction shell as the job-level section. Uses the project's already-loaded `useProject(projectId)` data for the list — no separate fetch.

### 5. Types

`LinkResponse` added to `frontend/src/types/index.ts`; `JobResponse` and `ProjectResponse` types gain `links: LinkResponse[]`.

## Migrations

| Migration | Content |
|-----------|---------|
| `V024__job_and_project_links.sql` | `job_links`, `project_links` tables + indexes; `JOB_LINKS` catalog entry (`available = FALSE`) |

After migration, run `./gradlew generateJooq` to regenerate jOOQ classes. A follow-up migration activates the addon (`available = TRUE`) once the feature is ready to sell, per the `JOB_TEMPLATES` precedent.

## Alternatives considered

### Single polymorphic `links` table (`scope_type` + `scope_id`)

One table for both job- and project-level links, discriminated by a scope column. Rejected — loses the FK constraint and cascade-delete guarantee that separate `job_id`/`project_id` foreign keys give for free, for a saving of one migration file. The codebase already prefers this split (separate relationship/assignee tables) over polymorphic scoping.

### Job-level links only, defer project-level to V2

Ship only the job-detail Links section now, revisit project-level placement later. Considered because the project nav bar is already crowded (7 items) and a dropdown-trigger button is a different interaction model from every other item in that row. Rejected — the backend shape (schema, validation, permissions, icon detection) is identical for both scopes, so building both now is barely more work than building one, and splitting them would mean re-opening this exact ADR later for an almost-identical project-level version.

### Immutable links, matching Notes

Rejected, per ADR-0009's own reasoning in reverse: Notes are immutable because they're an audit trail — the point is that they can't be revised. Links are shortcuts to resources, not a record of what was said; a stale or mistyped URL has no audit value and must be fixable.

### Confirmation dialog replaced entirely by 5-second undo toast (original draft)

Rejected in favor of reusing the existing `ConfirmModal` component. An undo toast needs a delayed/cancelable delete flow that doesn't exist anywhere else in the codebase yet — new infrastructure to build and test for a low-stakes action that the existing confirm-modal pattern already handles adequately.

### Strict `http(s)`-only URL scheme allow-list

Considered for simplicity and safety. Rejected in favor of a blocklist (`javascript:`, `data:`, `vbscript:` only) — the feature explicitly wants to link to things like `mailto:` addresses or other non-`http` resources; the actual security requirement is narrower than "http(s) only."

### Link count cap per job/project

Considered a soft cap (e.g. 20) to bound row growth. Rejected — no evidence this becomes a real problem, and Notes already ships with no cap under the same "unlikely to matter for MVP" reasoning (ADR-0009).

### `created_by` omitted from the UI entirely

Considered showing only icon + label + actions, keeping `created_by` audit-only (stored, never rendered) — mirroring how the original draft's UI states never mention it. Rejected in favor of a subtle "Added by {name}" line: the data is already there, cheap to surface, and consistent with the project's recent direction of small secondary-metadata lines (e.g. the schedule-source badge simplification) rather than adding no attribution at all.

### Link preview / metadata fetch (title, favicon via backend proxy)

Rejected for V1 — requires a backend proxy to avoid CORS/SSRF concerns fetching arbitrary user-supplied URLs server-side, and adds latency to what should be an instant add. Client-side hostname-based icon detection avoids both problems.

### Drag-to-reorder

Deferred to V2 — insertion order is sufficient for V1; reordering adds a mutable `order` column and a drag-and-drop UI for a feature not yet proven to need it.

## Consequences

### Positive

- Job-level and project-level links share identical schema, validation, and permission logic — one `LinkService`, minimal duplication
- No new locked-state UI component needed on either scope — both reuse existing conventions (`LockedSectionRow` aggregation, `lockedNavLink()` teaser)
- No new confirmation/undo pattern introduced — reuses `ConfirmModal`
- Hard-delete-only schema, no `deleted_at` — simplest possible child table, consistent with `notes`/`job_relationships`/`schedule_assignees`
- Icon detection is fully client-side and stateless — no backend proxy, no stored icon data to keep in sync with rebrands

### Negative

- The project nav bar gains an 8th item and its first non-route (dropdown-trigger) entry — a new interaction pattern in that row worth watching for crowding as more nav features get added
- `JobResponse`/`ProjectResponse` grow another embedded list every caller must tolerate, same growth pattern already accepted for `relationships`
- Scheme-blocklist validation must be kept in sync if new dangerous schemes emerge — it's a blocklist, not an allow-list, so it's inherently incomplete against schemes not yet considered

### Neutral

- `JOB_LINKS` addon seeded `available = FALSE`, activated in a follow-up migration — no immediate subscription UI change required until that flips
- `simple-icons` added as a new frontend dependency
- Job-level Links uses hide-on-lock (matches Notes/Approvals); project-level Links uses teaser-on-lock (matches Dashboard/Milestones/Templates/Schedules) — an intentional divergence driven by each placement's existing convention, not an inconsistency to fix later

## Implementation order

1. `V024__job_and_project_links.sql` migration + `./gradlew generateJooq`
2. `JobLinkRepository`, `ProjectLinkRepository`
3. `LinkService` — CRUD for both scopes, permission guards, URL scheme validation
4. `JobLinkController`, `ProjectLinkController`
5. Embed `links` in `JobResponse` and `ProjectResponse`
6. `simple-icons` dependency + `linkIcon.ts` detection utility (client-side)
7. `LinksSection` on `JobDetailPage` + `lockedSections` integration
8. `ProjectLinksDropdown` + nav bar entry in `AppLayout.tsx`
9. Follow-up migration to activate the `JOB_LINKS` addon when ready to sell

## References

- [ADR-0009: Notes Model](0009-notes-model.md)
- [ADR-0031: Feature gating full stack](0031-feature-gating.md)
- [ADR-0034: Recurring Job Scheduling](0034-recurring-jobs.md)
- [JOB-101: ADR — Dedicated links section on jobs and projects](https://46.101.205.77:8443/projects/PRJ-006/jobs/JOB-101)
