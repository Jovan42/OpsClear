# ADR-0045: Org-Wide Project Directory for Owner/Admin

**Status:** Proposed
**Date:** 2026-08-14
**Author:** Jovan Manojlovic

## Context

Per ADR-0026, org membership does not grant project access — project membership is a separate, explicit layer, even for the org Owner. This is the right call for access control (an org shouldn't let every Owner poke into every project by default as a business grows), but it has a real cost: an Owner can be structurally blind to projects they're not a member of, with no way to even know they exist.

This was discovered investigating JOB-100, suspected as a billing bug: a subscription-downgrade check reported more active projects than the Owner's own `GET /api/projects` list showed. It turned out not to be a bug — the org genuinely had a project the Owner wasn't a member of. As a business scales and an owner delegates, a manager might create a project for a client or initiative and simply not think to add the owner — not malicious, just oversight. Since the product's entire pitch is being the source of truth about what's actually happening in the business, an owner unable to see entire projects running inside their own account is the product failing at its one job for the person who cares most.

## Decision

Add a read-only, reporting-only directory view (org settings, near member management) listing every project in the org — regardless of the viewing Owner/Admin's membership in it.

> **Amended 2026-08-14, before implementation started (JOB-187/188 were still unstarted):** placement moved from "org settings, near member management" to a new dedicated **"Overview"** page, reachable via its own `UserMenu` entry, shared with the cross-project pending approval queue (ADR-0046) as a second section on the same page. Both features answer the same underlying question — "what's happening across my org that I might not otherwise see" — and adding two more sections to the already-growing Organisation settings page risked that page becoming a dumping ground. A dedicated page also keeps the `UserMenu` itself from accumulating unrelated entries: `Organisation`, `Feedback`, and now `Overview` are grouped together (visually separated from personal `Account settings` and `Sign out`) rather than nested indefinitely inside one settings page. See ADR-0046 for the full menu/page restructuring.

## Product decisions

- Lists every project in the org: name, owner, status, and **project-level member count** — the count directly serves the "spot orphaned/under-staffed projects" motivation, cheap to compute alongside the existing query.
- **Sorted by member count ascending** — 0-member (or low-member) projects surface first, making the blind-spot problem visible at a glance rather than requiring a manual scan through an alphabetical or chronological list.
- Gated to Owner/Admin org roles only — this deliberately bypasses the project-membership boundary, so access must be restricted to the roles ultimately accountable for the whole org.
- **All project statuses shown, not filtered to active-only** — the original motivating confusion (JOB-100) was specifically about reconciling a count that included non-active projects, so hiding any status here would reintroduce the same kind of mismatch this ADR exists to resolve.
- **Explicitly read-only — no action to join or add oneself to a listed project.** Seeing a project you're not in naturally raises "let me join it," but self-adding to a project you weren't invited to is a real permission-boundary question in its own right (it would mean quietly working around ADR-0026's membership model as a side effect of a reporting view). Deliberately out of scope here; worth its own future consideration if it's ever wanted.

## Technical design

### Database
None — reads existing `projects`/`project_members` data.

### API
- New endpoint: `GET /api/organisations/{orgId}/projects/directory` — returns every project in the org with name, owner, status, and member count, sorted by member count ascending. Gated to Owner/Admin.

### Backend
- New service method (or an addition to `ProjectService`): fetch all projects for an org without filtering by caller membership, with a `COUNT` on `project_members` per project, ordered by that count ascending.

### Frontend
- Table of all projects (name, owner, status, member count), sorted with the lowest-member-count projects first — rendered as the "Project Directory" section on the new **Overview** page (see amendment above and ADR-0046), not inside the existing org settings page.

### Constraints & edge cases
- Must not expose job/content data from projects the viewer isn't a member of — project name/owner/status/member-count only, nothing deeper.
- Must not be read as contradicting ADR-0026 — reporting only, no access-grant side effect, no action to change membership from this view.

## Alternatives considered

### Omit member counts, project name/owner/status only

Considered for a smaller first cut. Rejected — the count is cheap to compute alongside the existing query and directly addresses the "spot blind spots" motivation.

### Filter to active projects only

Considered, on the assumption that completed projects are less relevant to a "blind spot" view. Rejected — the original JOB-100 confusion was specifically about a count that included non-active projects; filtering here would reproduce a version of the same mismatch.

### "Request to join" or "add myself" action from the directory

Considered, since seeing a project you're not in naturally invites that action. Rejected for this ADR — self-adding to a project you weren't invited to is a real permission-boundary decision (effectively working around ADR-0026's membership model), and deserves its own deliberate consideration rather than being a quiet side effect of a reporting view.

## Consequences

### Positive
- Closes the visual-reconciliation gap between org-wide counts (used for subscription/downgrade logic) and what an Owner/Admin can actually see in their own project list
- Ascending member-count sort surfaces exactly the projects most likely to be a blind spot, without requiring the Owner to scan the whole list
- Purely additive — no changes to existing project access/membership logic

### Negative
- Another Owner/Admin-only surface to maintain, though small (one read-only view)

### Neutral
- No action taken from this view beyond viewing — if a "join project" capability is ever wanted, it's a separate, deliberate future decision, not a consequence of this one

## Implementation order
1. Backend: org-wide project directory endpoint (name, owner, status, member count, sorted ascending by count), Owner/Admin gated
2. Frontend: directory view in org settings

## References

- ADR-0026 (referenced, not modified): established that org membership does not grant project access
- JOB-100 (Maintenance): the investigation that surfaced this blind spot
- JOB-137 (Future Consideration, promoted to PRJ-010/MIL-027): original scoping notes this ADR implements
- JOB-148 (PRJ-010/MIL-029): quick project switcher — will consume this ADR's directory data for Owner/Admin roles once both ship
- ADR-0046: Cross-Project Pending Approval Queue (`docs/dev/decisions/0046-cross-project-approval-queue.md`) — shares the "Overview" page introduced by this amendment
