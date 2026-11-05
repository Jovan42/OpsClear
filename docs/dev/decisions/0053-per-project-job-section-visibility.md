# ADR-0053: Per-Project Job-Detail Section Visibility

**Status:** Proposed
**Date:** 2026-11-05
**Author:** Jovan Manojlovic

## Context

The job-detail screen shows up to five optional sections: **Notes, Approvals, Relationships, Links, Job status history**, each behind its own add-on (ADR-0031). An org that pays for, say, Notes gets it on every project, even projects that never use it. This adds clutter to the job screen there.

This ADR lets a project Owner/Admin **hide** specific sections for one project, without affecting other projects. It's a declutter preference, not access control.

Promoted from JOB-159 (Future Consideration, PRJ-002) into PRJ-012 / MIL-037. This ADR is JOB-248.

### Facts from the existing system that shape this design

- **Section rendering** in `JobDetailPage.tsx` checks only add-ons: `hasAddon('NOTES' | 'APPROVALS' | 'JOB_RELATIONSHIPS' | 'JOB_LINKS' | 'JOB_STATUS_HISTORY')`. Links is gated by `JOB_LINKS` (an add-on that exists in code but isn't listed in ADR-0031's table).
- **`LockedSectionRow`** collects sections whose add-on is missing into one "upgrade in org settings" row. It's computed only from add-ons.
- **"Request approval" isn't part of the Approvals section.** It's a button in `JobStatusBar`, enabled by `canRequestApproval={hasAddon('APPROVALS')}`.
- **Data hooks** (`useNotes`, `useApprovals`, `useJobs` for relationships/history) skip fetching when the add-on is missing (`enabled: hasAddon(...)`).
- **Project settings** is one page with sections: Details, Members, Block reasons, Danger zone.
- **ADR-0052** added `projects.require_completion_approval` and a small `PATCH /api/projects/{id}/settings` endpoint intended to be shared with this feature.

## Decision

Add `projects.hidden_job_sections`, a list of hidden section keys (empty = all shown), editable by project Owners/Admins via `PATCH /api/projects/{id}/settings`. On the job screen, a section renders only if **the org has its add-on AND the project hasn't hidden it**. Hidden sections are fully invisible: no placeholder, no locked row, no data fetched. Toggles live in a new **"Job screen"** section of Project settings.

## Product decisions (settled in JOB-248 unless noted)

- **Rule**: section shown = `hasAddon(X) AND NOT projectHides(X)`. A project can only hide what the org has; it can never reveal something the org hasn't paid for.
- **Sections covered**: Notes, Approvals, Relationships, Links, Job status history, **on the job-detail screen only**. Separate pages and nav items (Milestones, Templates, Schedules, the Approvals queue page, Dashboard widgets) are out of scope.
- **Who can change it**: project Owners/Admins.
- **Shared for the whole project**: stored on the server, the same for every teammate (unlike personal preferences in `localStorage`, ADR-0018/0023).
- **Fully invisible**: a hidden section renders as if it were never part of the screen. No "locked" or "hidden" placeholder.
- **Existing data stays hidden**: old notes or links on a job aren't shown (and no "N hidden items" hint) until the section is shown again. Nothing is deleted. Re-enabling shows everything as before.
- **No backend enforcement**: the Notes/Approvals/Relationships/Links/History endpoints are untouched. The data legitimately belongs to the org; this only changes what the UI shows.
- **Defaults**: every section shown, for existing and new projects.

### Decided in this ADR

- **Hiding Approvals also hides the manual "Request approval" button** in the status bar. A hidden section shouldn't leave its main action behind.
- **The completion gate (ADR-0052) is independent and keeps working when Approvals is hidden.** The gate is a workflow rule, not the Approvals section. Its "Request completion" button, "Approval requested" state and `CompletionRequestBanner` (approve / reject / withdraw) still show. The two settings don't block or change each other.
- **Hidden sections are left out of the locked "upgrade" row too.** If the org lacks Notes *and* the project hides Notes, that project's jobs don't nudge an upgrade for Notes. Other projects still do.
- **Hidden sections aren't fetched.** The data hooks skip requests for hidden sections, the same way they skip sections without an add-on.

### Project settings UI

- New section **"Job screen"** on the Project settings page, above Danger zone:
  - **"Sections shown on jobs"**: five toggles (Notes, Approvals, Relationships, Links, Job status history), all on by default. One line of help text: "Hide sections this project doesn't use. Nothing is deleted; turning a section back on shows its data again."
  - A section whose add-on the org doesn't have is shown **disabled with a lock icon** and "Not in your plan", so admins understand why it's absent from jobs regardless.
  - ADR-0052's **"Require approval to complete jobs"** toggle belongs in this same section, so job-workflow settings sit together. (Whichever ADR is implemented first creates the section; the other adds to it.)
- Members see the Project settings page as today; the toggles are read-only or hidden for them, following the page's existing role handling.

## Technical design

### Database

New migration (next free number at implementation time: after ADR-0051's `V041` and ADR-0052's migration if those have landed):

```sql
ALTER TABLE projects
    ADD COLUMN hidden_job_sections VARCHAR(30)[] NOT NULL DEFAULT '{}',
    ADD CONSTRAINT chk_projects_hidden_job_sections CHECK (
        hidden_job_sections <@ ARRAY['NOTES','APPROVALS','RELATIONSHIPS','LINKS','STATUS_HISTORY']::VARCHAR(30)[]
    );
```

- One column next to ADR-0052's `require_completion_approval`. Empty array = everything shown.
- **Why a list column** (decided): it maps directly to the API as a list, needs no join on every project load, and adding a future section is only a change to the check constraint and enum.

### API

| Method | Path | Change |
|---|---|---|
| `PATCH` | `/api/projects/{id}/settings` | Accepts `hiddenJobSections: ["NOTES", "LINKS"]` alongside ADR-0052's `requireCompletionApproval`. Each field is optional; only fields present are updated. Owner/Admin only. Unknown values → 400; duplicates are de-duplicated. Hiding a section whose add-on the org lacks is allowed (it only affects the locked row). If ADR-0052 isn't implemented yet, this ADR's backend job creates the endpoint. |
| `GET` | `/api/projects`, `/api/projects/{id}` | `ProjectResponse` gains `hiddenJobSections: string[]`. |

- Section keys: `NOTES`, `APPROVALS`, `RELATIONSHIPS`, `LINKS`, `STATUS_HISTORY`, as a backend enum `JobSection`. The frontend maps them to add-on codes (`NOTES`, `APPROVALS`, `JOB_RELATIONSHIPS`, `JOB_LINKS`, `JOB_STATUS_HISTORY`).
- Not add-on gated itself (it's a project setting). No changes to any section endpoint.

### Backend

- `JobSection` enum, `hiddenJobSections` on the project model / jOOQ mapping (`VARCHAR[]` ↔ `List<JobSection>`), `ProjectResponse` field.
- `ProjectService.updateSettings` handles `hiddenJobSections` (validate, de-duplicate, store), Owner/Admin check. Shares the endpoint with ADR-0052.
- Integration tests: Owner/Admin can set/clear, Member gets 403, invalid value → 400, value round-trips in `GET /api/projects/{id}`, other projects unaffected, section endpoints still work when hidden.

### Frontend

- **`useSectionVisibility(project)`** hook: one place that answers `isVisible(section)` = `hasAddon(addonFor(section)) && !project.hiddenJobSections.includes(section)` and `isLocked(section)` = `!hasAddon(...) && !hidden`. Used by every place below so the rule can't drift.
- **`JobDetailPage`**: replace the five `hasAddon(...)` checks with `isVisible(...)`. Build `lockedSections` from `isLocked(...)`. Pass `canRequestApproval = isVisible('APPROVALS')` to `JobStatusBar`. The completion-gate UI from ADR-0052 is driven by the gate setting and `hasAddon('APPROVALS')`, **not** by section visibility.
- **Data hooks** (`useNotes`, `useApprovals`, relationships/history in `useJobs`): `enabled` uses `isVisible(...)`, so hidden sections cause no requests.
- **Project settings page**: new "Job screen" section with the five toggles (disabled + lock for sections not in the plan), saving via `PATCH /api/projects/{id}/settings`, and invalidating the project query so open job screens update.
- **i18n**: en + sr strings in `projects.json` (settings) and the existing jobs namespace where needed.
- **E2E**: hide Notes on one project and check the Notes section is gone there and still present on another project. Hide Approvals and check the section and the "Request approval" button are gone. Check that a section without its add-on, when hidden, doesn't appear in the locked row.

### Constraints & edge cases

- **Notifications (ADR-0051)**: a `NOTE_ADDED` notification can still be created through the API on a project that hides Notes. Clicking it opens the job, where Notes isn't shown. Accepted for V1: hiding is a display preference, and the API remains authoritative.
- **Stats (ADR-0050)** read `job_status_history` directly and are unaffected by hiding Job status history.
- **Completed projects** (read-only): settings follow the existing project-completed rules for the settings page.
- **Add-on later bought**: if a project already hides that section, it stays hidden there after the purchase (the project's choice is kept), and appears on other projects.

## Alternatives considered

### Five boolean columns on `projects`
Rejected (decided). Five more columns, and every new optional section would need a migration plus an API change.

### Separate `project_hidden_sections` table
Rejected (decided). Normalized, but adds a join to every project load for a small setting.

### Store in `localStorage` per user
Rejected (settled). The setting must be the same for every teammate on the project.

### Show hidden sections as locked or collapsed placeholders
Rejected (settled). A placeholder suggests something is missing or needs payment, when it's a deliberate choice.

### Enforce on the backend (reject note/link/approval writes on projects that hide them)
Rejected (settled). It would turn a display preference into access control, and the data already belongs to the org.

### Can't hide Approvals while the completion gate is on (or hiding it disables the gate)
Rejected (decided). It would couple two independent settings. The gate keeps its own UI (banner and status bar), so hiding the Approvals section doesn't break it.

### Keep showing the upgrade nudge for project-hidden sections
Rejected (decided). The project chose not to use the section, so an upsell there is noise.

### A separate settings sub-page
Rejected (decided). Six toggles fit comfortably as a section on the existing page.

## Consequences

### Positive
- Projects that don't use a section get a cleaner job screen without affecting other projects.
- One shared visibility hook replaces scattered `hasAddon` checks, so section rules live in one place.
- No requests for hidden sections.

### Negative
- Data in a hidden section is invisible on the job screen until re-enabled. Users may not realise old notes exist (accepted V1 simplification).
- Notifications and API clients can still reference hidden sections.

### Neutral
- Shares `PATCH /api/projects/{id}/settings` and the "Job screen" settings section with ADR-0052.
- Approvals queue page, nav and Dashboard approval widgets are unchanged.

## Implementation order

1. **DB**: migration adding `projects.hidden_job_sections` with its check constraint.
2. **Backend: section visibility setting**: `JobSection` enum, model/jOOQ mapping, `ProjectResponse.hiddenJobSections`, `hiddenJobSections` in `PATCH /api/projects/{id}/settings` (creating the endpoint if ADR-0052 hasn't yet). Unit + integration tests. *Blocked by DB.*
3. **Frontend 1: job screen visibility**: `useSectionVisibility`, `JobDetailPage` sections, `LockedSectionRow` input, "Request approval" button, data hooks, E2E. *Blocked by Backend.*
4. **Frontend 2: Project settings "Job screen" section**: five toggles with plan locks, save + cache invalidation, i18n, E2E. *Blocked by Backend.*

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`): `hiddenJobSections` in project settings and project responses
- [ ] Add example requests (hide sections, clear all)
- [ ] Update environment variables if needed
- [ ] Test the flow manually before marking complete

## References

- ADR-0018 / ADR-0023: personal preferences in `localStorage` (why this isn't one)
- ADR-0031: Feature gating (section add-ons)
- ADR-0049: E2E coverage plan (locked-section audit)
- ADR-0050: Job completion velocity stats (unaffected by hidden history)
- ADR-0051: In-app notification centre (`NOTE_ADDED` edge case)
- ADR-0052: Gated completion requiring approval (shared settings endpoint, gate independence)
- JOB-159 (Future Consideration, PRJ-002): original idea
- JOB-248 (PRJ-012 / MIL-037): this ADR's job and its settled decisions
