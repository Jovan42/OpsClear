# ADR-0041: Polished Empty States Across the App

**Status:** Proposed
**Date:** 2026-07-23
**Author:** Jovan Manojlovic

## Context

An inventory of the current codebase found 15 existing "zero data" occurrences across `frontend/src`, every one a hand-rolled, one-off block — no shared `EmptyState` component exists anywhere. Styling (muted text color, padding) is duplicated and drifts slightly between call sites: `ProjectListPage`, `JobListPage` (two spots — full-empty and grouped-empty), `DashboardPage`, `LinksSection`, `ProjectLinksDropdown`, `RelationshipsSection`, `NoteThread`, `StatusHistory`, `AddRelationshipModal`, `MilestonesPage`, `SchedulesPage`, `TemplatesPage`, `OrgSettingsPage` (org templates), `ApprovalQueuePage`. (`FeaturesPage`'s "No preview yet" placeholder is out of scope — that's real marketing-page territory covered by ADR-0040, not real in-app data.)

The inventory also surfaced a real bug this ADR fixes, not just a styling inconsistency: `OrgSettingsPage`'s empty-state CTA checks `isOwnerOrAdmin` before rendering, but `MilestonesPage`, `SchedulesPage`, and `TemplatesPage`'s "Create first X" buttons only check `hasAddon(...)` — not the user's role. A Member without permission to create a milestone/schedule/template can currently see a CTA inviting them to do so anyway.

This work sits alongside ADR-0040 (interactive `/features` demos) as this phase's other half: ADR-0040 is where "example data" lives for prospects evaluating the product before paying; this ADR is what makes a real, genuinely-empty paid account feel considered rather than broken once someone actually signs up.

## Decision

Introduce one shared `EmptyState` component and migrate all 14 in-app occurrences to it, standardizing appearance, fixing the role-gating gap found above, and standardizing CTA placement (co-located inside the empty-state block itself, not decoupled into a section header).

## Product decisions

- **One shared `EmptyState` component:** small `lucide-react` icon (muted) + short, context-specific friendly copy + an optional action (button-styled CTA). No custom illustrations — keeps this lightweight, consistent with reusing the icon library already adopted in ADR-0039 rather than starting a new illustration asset pipeline.
- **Role-gated actions:** the action slot accepts an optional permission check (reusing the `canManage`-style convention `LinksSection`/`RelationshipsSection` already thread today), so a CTA only renders when the current user can actually perform the action. This directly fixes the `MilestonesPage`/`SchedulesPage`/`TemplatesPage` gap described above.
- **Copy stays role-agnostic** — only the CTA's visibility is gated by permission, not the message text itself.
- **Standardized CTA placement:** co-located inside the empty-state block everywhere, matching the pattern `ProjectListPage`/`JobListPage`/`MilestonesPage`/`SchedulesPage`/`TemplatesPage` already use. `LinksSection` and `RelationshipsSection` currently put their "+ Add" affordance in the section header, decoupled from the empty message — these migrate to also surface the action within the empty state itself (the header button can remain for the already-has-data case).
- **Action-less variant supported:** some existing empty states correctly have no CTA — `StatusHistory` (system-generated), `ApprovalQueuePage`'s "all caught up" (a good state, not a gap), `AddRelationshipModal` (a search picker with no results). This ADR does not invent CTAs for states that don't need one.

## Technical design

### Database
None.

### API
None.

### Backend
None.

### Frontend
- New `EmptyState` component (e.g. `frontend/src/components/EmptyState.tsx`): props for `icon` (a `lucide-react` icon component), `message`, optional `description`, and an optional `action` object (`label`, `onClick`, optional `canPerform` boolean).
- Migrate all 14 in-app occurrences (excluding `FeaturesPage`) to use the shared component:
  - `ProjectListPage`, `JobListPage` (both spots), `DashboardPage`, `MilestonesPage`, `SchedulesPage`, `TemplatesPage`, `OrgSettingsPage` — CTA logic ported as-is (already correct, or, for the addon-only-gated three, extended with the missing role check)
  - `LinksSection`, `RelationshipsSection` — CTA moves from the section header into the empty-state block itself when the section has no data; existing `canManage`/`projectCompleted` props feed the new `canPerform` check
  - `NoteThread` — currently no CTA; evaluate whether the composer being "always below" is sufficient or whether the empty state should point to it
  - `StatusHistory`, `ApprovalQueuePage`, `AddRelationshipModal` — message/icon only, no action, using the same shared component for visual consistency

### Constraints & edge cases
- Must not introduce a CTA where none existed for a good reason (`StatusHistory`, `ApprovalQueuePage`'s "all caught up", the search-picker in `AddRelationshipModal`).
- Migrating `LinksSection`/`RelationshipsSection` must not remove the existing header "+ Add" affordance for the has-data case — only the empty-state case gains a co-located action.
- Fixing the `MilestonesPage`/`SchedulesPage`/`TemplatesPage` role-gating gap is a behavior change (a Member who could previously see — though not use — the CTA will no longer see it at all); called out explicitly here since it rides along with what's otherwise a visual polish pass.

## Alternatives considered

### Custom illustrations per empty state

Rejected in favor of `lucide-react` icons — more design/maintenance overhead than this warrants, and breaks from the icon library already adopted for the markdown toolbar (ADR-0039).

### Leave CTA placement as-is (header button for sections, inline for pages)

Rejected — standardizing on one pattern is simpler to reason about and fixes the "empty state itself is actionless" gap in `LinksSection`/`RelationshipsSection` today.

### Skip the role-gating fix, treat this as pure visual polish

Rejected — the inconsistency was already found during the inventory, and leaving it in place while touching every one of these components anyway would be an easy, cheap fix to skip for no good reason.

## Consequences

### Positive

- One shared, consistent empty-state pattern across the whole app instead of 14 hand-rolled variants
- Fixes a real (if minor) permissions gap: Members no longer see "create" CTAs for actions they can't perform on Milestones/Schedules/Templates
- `LinksSection`/`RelationshipsSection` empty states become actionable in place, not requiring a user to look elsewhere for how to add their first item

### Negative

- Touches 14 existing components — a wide, shallow change, more files than typical for a single job even though each individual change is small
- Behavior change (role-gated CTAs disappearing for Members) needs to be called out clearly in the PR so it isn't mistaken for a regression

### Neutral

- No new dependencies — `lucide-react` is already adopted via ADR-0039
- `FeaturesPage`'s marketing-page empty state is untouched by this ADR (see ADR-0040 instead)

## Implementation order

1. `EmptyState` shared component (icon, message, description, optional gated action)
2. Migrate page-level empty states: `ProjectListPage`, `JobListPage` (both spots), `DashboardPage`
3. Migrate `MilestonesPage`, `SchedulesPage`, `TemplatesPage` — port existing addon-gate logic, add the missing role check
4. Migrate `OrgSettingsPage` (org templates) — existing role check ports over directly
5. Migrate `LinksSection`, `RelationshipsSection` — move CTA into the empty-state block, reuse existing `canManage`/`projectCompleted` props
6. Migrate the action-less cases: `StatusHistory`, `ApprovalQueuePage`, `AddRelationshipModal`, `NoteThread` (after deciding its CTA question)
7. Manual verification across all 14 locations, including confirming the role-gating fix behaves correctly for a Member account

## References

- ADR-0039: Markdown Formatting Toolbar (`docs/dev/decisions/0039-markdown-formatting-toolbar.md`) — adopted `lucide-react`, reused here
- ADR-0040: Interactive Live-Component Demos on /features (`docs/dev/decisions/0040-interactive-feature-demos.md`) — companion piece: where example data lives, so real accounts can stay genuinely empty
- JOB-139 (Future Consideration, promoted to PRJ-008/MIL-022): original scoping notes this ADR implements
