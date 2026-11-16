# ADR-0060: Configurable ID Prefixes

**Status:** Proposed
**Date:** 2026-11-16
**Author:** Jovan

## Context

Every project, job, milestone and template gets a friendly ID such as `PRJ-012`, `JOB-042`, `MIL-007` or `TPL-003` (ADR-0027). Prefixes are stored per org in `org_settings`, but nobody can change them, so every org gets the defaults. Some orgs call their work "tasks" or "tickets" and want IDs that read that way (e.g. `TSK-042`).

This ADR lets the org Owner change the four prefixes. It's a small settings feature on top of existing columns.

Promoted from JOB-076 (Future Consideration, PRJ-002) into PRJ-014 / MIL-044. This ADR is JOB-334. The friendly-ID work JOB-076 was waiting on (ADR-0027: generation, routing, backfill, frontend URLs) is done.

### Facts from the existing system that shape this design

- **There are four prefixes, not three.** `org_settings` has `project_prefix`, `job_prefix`, `milestone_prefix` (V013) and `template_prefix` (V017), defaulting to `PRJ`, `JOB`, `MIL` and `TPL`. `FriendlyIdEntityType` is `PROJECT, JOB, MILESTONE, TEMPLATE`.
- **The columns are `VARCHAR(3)`.** Prefixes of 2–3 characters fit without a migration.
- **Generation:** `FriendlyIdService.nextFriendlyId` reads the current prefix, increments the org's counter for that type (`org_sequences`) and formats `PREFIX-NNN`. The counter is per type and never resets.
- **Each record stores its full ID.** `friendly_id` (`VARCHAR(20)`) holds the full string. `FriendlyIdResolver` looks a record up by that exact string (`UPPER(friendly_id)`), scoped to the caller's org, and the route decides which table to search. A record keeps working under its old prefix forever.
- **The resolver** already accepts `^[A-Za-z]{2,6}-\d+$`, so 2–3 letter prefixes need no change there.
- **Permissions:** editing org details (`OrganisationService.update`) and Subscription are Owner only.
- **The org slug** (ADR-0026) is a separate value with the same 2–3 letters rule. It's not an ID prefix.
- **OpsClear's own dev tooling hardcodes `JOB-`.** `scripts/opsclear` (`JOB_REF_RE`, `CLOSES_LINE_RE`, `TITLE_JOBS_RE`, `BRANCH_RE`) and the GitHub job sync (ADR-0055) only recognise `JOB-123`.
- **E2E specs** don't hardcode friendly-ID prefixes.

## Decision

- Add **`GET` and `PATCH /api/organisations/{id}/settings`** for the four prefixes. Anyone in the org can read them; only the Owner can change them.
- **A change applies to new records only.** Existing IDs keep their prefix; numbering continues from the same counter.
- **Format:** 2–3 letters A–Z, stored uppercase.
- **Unique:** the four prefixes must differ from each other, and none may match a prefix that existing records of another type already carry.
- **UI:** an "ID prefixes" section on the org settings page with a live preview and a confirmation dialog.

## Product decisions (settled in JOB-334)

| Question | Decision |
|----------|----------|
| Who can change prefixes | Org **Owner** only, like org details and Subscription. Other members see them read-only. |
| Existing IDs | **Unchanged.** `JOB-042` stays `JOB-042`; the next job is `TSK-043`. Old URLs, notes, links and external references keep working. The UI says so before saving. |
| Which prefixes | All four: Project, Job, Milestone, Template. |
| Format | 2–3 letters A–Z. Input is uppercased; anything else gives 400. |
| Uniqueness | The four current prefixes must differ within the org. A prefix also can't be one that existing records of **another** type in the org already carry. |
| Old-prefix links | Keep resolving, because records store their full ID. GitHub `Closes JOB-x` references in our own repo are unaffected unless the OpsClear org changes its job prefix (see below). |
| OpsClear's own org | Documented only: don't change its job prefix, or `scripts/opsclear` and the job sync break. No code guard. |

## Technical design

### Database

None. The columns exist, and 2–3 characters fit `VARCHAR(3)`.

### API

**`GET /api/organisations/{id}/settings`** (any member of the org; others get 403/404 like the other org endpoints):

```json
{
  "projectPrefix": "PRJ", "jobPrefix": "JOB", "milestonePrefix": "MIL", "templatePrefix": "TPL",
  "nextProjectId": "PRJ-015", "nextJobId": "JOB-347", "nextMilestoneId": "MIL-045", "nextTemplateId": "TPL-005"
}
```

The `next*Id` values are the current prefix + `last_value + 1` from `org_sequences`. They're for the preview only, with no reservation (a record created in the meantime takes that number).

**`PATCH /api/organisations/{id}/settings`** (Owner only, otherwise 403):

```json
{ "jobPrefix": "TSK", "milestonePrefix": "PHS" }
```

- All fields are optional; omitted fields don't change. Returns the same shape as GET.
- **400 Validation Error** when:
  - a value isn't 2–3 letters A–Z, after trimming and uppercasing
  - two of the resulting four prefixes are equal
  - a prefix is already used by another type's existing records, with a message such as `Prefix "JOB" is already used by existing jobs`
- Setting a prefix to the value it already has is a no-op.

Postman: add both requests.

### Backend

- **`OrgSettingsController`** with the two endpoints (`ApiPaths` constants).
- **`OrgSettingsService`:**
  - `get(orgId, callerId)`: membership check.
  - `update(orgId, request, callerId)`:
    1. Owner check, reusing the org service's owner rule.
    2. Normalise each value (trim, uppercase) and validate the format.
    3. Build the resulting four prefixes and check they're distinct.
    4. For each prefix that **changes**, check it isn't used by another type's records (below).
    5. Update `org_settings` and set `updated_at`.
    6. Log at INFO with org, caller, old → new values.
- **`OrgSettingsRepository`** (or extend `FriendlyIdRepository`):
  - read/update `org_settings`
  - read `org_sequences.last_value` per type
  - **`isPrefixUsedByOtherType(orgId, prefix, type)`:** checks whether any non-deleted record of the *other* three types in the org has a `friendly_id` starting with `PREFIX-`. That's one query per changed prefix, `EXISTS` over each table scoped to the org: projects by `organisation_id`, jobs and milestones through their project, templates through project or org. It runs only on a save, so a scan across one org's rows is fine.
- **DTOs:** `OrgSettingsResponse` and `UpdateOrgSettingsRequest`. The request uses `@Pattern(regexp = "^\\s*[A-Za-z]{2,3}\\s*$")` on each optional field; the distinctness and history checks live in the service.
- **`FriendlyIdService`** is unchanged: it already reads the prefix each time it generates an ID.
- **Tests:**
  - Unit tests: normalisation, format, distinctness, the history check (blocked and allowed cases), non-owner gives 403, no-op on an unchanged value.
  - Integration tests:
    - GET as a member
    - PATCH as Owner, then a newly created job gets the new prefix while an old job still resolves by its old ID
    - PATCH as Admin or Member gives 403
    - duplicate prefixes give 400
    - a prefix used by another type's records gives 400
    - an invalid format gives 400
    - another org gives 403/404

### Frontend

- **New `IdPrefixesSection`** on `OrgSettingsPage`, below Details, styled like the other sections. It has four labelled inputs: Projects, Jobs, Milestones, Templates.
  - **Owner:** inputs are editable and uppercase as you type, with at most 3 letters. Under each one, a live preview such as "Next job: TSK-347", built from `next*Id` with the new prefix. Save is enabled only when something changed.
  - **Others:** values shown read-only.
- **Confirmation dialog on Save:** "New records will use the new prefixes. Existing IDs like JOB-042 keep their current prefix, and their links keep working."
- **Validation:** Zod mirrors the backend rules (2–3 letters, all four distinct). The history check comes from the backend's 400 message, shown inline on the field it names (or as a toast).
- **API:** `getOrgSettings(orgId)` and `updateOrgSettings(orgId, patch)` in `api/organisations.ts`, plus `useOrgSettings` / `useUpdateOrgSettings` hooks. After saving, invalidate the settings query.
- **i18n:** en and sr keys for the section, labels, preview, dialog and errors.
- **E2E:** the Owner changes the job prefix → creates a job → it gets the new prefix, and an existing job still opens by its old ID. A non-owner sees the values read-only. The spec restores the default prefix afterwards so the shared seed org isn't left changed.

### Constraints & edge cases

- **Numbering continues:** `JOB-346` → `TSK-347`. The counter is per type and never resets, so even after `JOB` → `TSK` → `JOB` no ID is ever repeated.
- **A record created during the change** gets whichever prefix was in place when its ID was generated. No locking is needed.
- **The preview is a hint, not a reservation.** A job created by someone else in the meantime takes the shown number.
- **The history check** also covers an org's own past prefixes for the *same* type. For example, going back from `TSK` to `JOB` for jobs is allowed, because those records are jobs too.
- **Case:** input is stored uppercase, and lookups are already case-insensitive.
- **Org slug vs prefix:** independent. A prefix may equal the org slug.
- **OpsClear's own org:** changing its job prefix would stop `scripts/opsclear` and the job sync from recognising new jobs. Documented in AGENTS.md; no guard.
- **The API key / CLI regex** (`FRIENDLY_RE = ^[A-Z]+-\d+$`) already accepts any letters, so general CLI lookups of a renamed org's IDs work.

## Alternatives considered

### Rename existing IDs when the prefix changes (decided against)

Consistent IDs everywhere, but every bookmarked URL, note mention, link label and external reference (e.g. GitHub) to an old ID would break. It would also need a bulk rewrite.

### Owner + Admin can change prefixes (decided against)

More flexible, but prefixes are org-wide identity and rarely change. Owner only matches org details and Subscription.

### Longer prefixes (2–6 letters) (decided against for now)

They'd allow `TASK` or `ISSUE`, but need a migration to widen four columns. 2–3 letters fits the existing columns and matches the org slug rule. The resolver already accepts 6, so widening later is only a migration plus a validation change.

### Allow types to share a prefix (decided against)

The backend copes (lookups go by entity type), but an ID like `T-12` would be ambiguous to people and in text such as notes or commit messages.

### Only check the four current prefixes, not existing records (decided against)

Simpler, but after `JOB` → `TSK` for jobs, milestones could take `JOB`, and new `JOB-005` milestones would sit next to old `JOB-005` jobs.

### Put the prefixes on `PATCH /api/organisations/{id}` (decided against)

One endpoint, but it would mix the `organisations` and `org_settings` tables in one request and response. A separate `/settings` maps 1:1 to its table.

### Fields inside the Details form (decided against)

Less UI, but there'd be no clear warning that existing IDs don't change. A separate section with a confirmation dialog makes that explicit.

### Make OpsClear's tooling read the org's job prefix (decided against)

It only benefits our own org, which has no reason to change its prefix. A note in AGENTS.md is enough.

## Consequences

### Positive

- Orgs can name IDs to match their own vocabulary.
- No migration, and no change to ID generation or lookup.
- Old IDs and links keep working forever.

### Negative

- An org that changes a prefix will have mixed IDs (`JOB-042` and `TSK-347`) in the same project. That's by design and explained in the UI.
- The history check adds a scan across one org's projects, jobs, milestones and templates on each prefix save (rare).

### Neutral

- AGENTS.md gets a line saying the OpsClear org's job prefix must stay `JOB`.
- ADR-0027's ID format stays the same apart from prefixes now being editable.

## Implementation order

1. **Backend: org ID prefix settings.**
   - `OrgSettingsController`, `OrgSettingsService`, repository methods (settings, sequences, history check), DTOs
   - unit + integration tests, Postman
   - AGENTS.md line on the OpsClear org's job prefix

   No blockers.
2. **Frontend: ID prefixes section on org settings.**
   - `IdPrefixesSection` with preview and confirmation dialog, read-only for non-owners
   - API methods + hooks, i18n en/sr
   - E2E

   *Blocked by Backend.*

No DB job.

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`): GET and PATCH `/api/organisations/{id}/settings`
- [ ] Add example requests (change the job prefix; duplicate → 400; used by another type → 400)
- [ ] Update environment variables if needed (none)
- [ ] Test the flow manually before marking complete

## References

- JOB-076: source Future Consideration
- ADR-0026: Organisation layer (Owner role, org slug)
- ADR-0027: Human-friendly IDs (generation, `org_settings`, `org_sequences`, resolver)
- ADR-0032 / ADR-0033: Job templates and org-level templates (`template_prefix`)
- ADR-0037: Localization (en/sr)
- ADR-0055: GitHub job sync (`JOB-` hardcoded in our tooling)
