# ADR-0015: Job Detail and Inline Actions

**Status:** Accepted
**Date:** 2026-03-04
**Author:** Jovan Manojlovic

## Context

Module 7.5 covers the job detail page (`/projects/:projectId/jobs/:jobId`) and all the
inline actions a user can perform on a job:

- View full job information
- Change status (start, complete, reopen)
- Block or unblock a job
- Add an immutable note
- Request an approval

The key decisions are: page layout (single page vs tabs), which sections use accordion
collapse, how each action is surfaced (modal vs inline), optimistic updates, how the
blocked state is communicated visually, and which controls are visible to each role.

---

## Decision

### 1. Page layout — single scroll, no tabs, accordion sections

The detail page uses a **single scrolling layout** with four sections stacked vertically:

```
┌──────────────────────────────────────────────┐
│  ← Back   |  Job Title             [Edit] [⋮] │
│  Status badge  |  Blocked banner (if blocked) │
├──────────────────────────────────────────────┤
│  INFO SECTION                                 │
│  Client · Assigned to · Deadline · Created by │
│  Description                                  │
├──────────────────────────────────────────────┤
│  ACTION BAR                                   │
│  [Status buttons]  [Block]  [Request Approval]│
├──────────────────────────────────────────────┤
│  Notes (12)                               ▼  │  ← accordion, default expanded
│  Chronological thread + inline add-note form  │
├──────────────────────────────────────────────┤
│  Approvals (2 pending)                    ▼  │  ← accordion, expanded if pending
│  Approval cards with approve/reject buttons   │
└──────────────────────────────────────────────┘
```

**Info section and action bar** are always fully visible — they are compact and represent
the primary reason a user navigates to this page.

**Notes and Approvals** are accordion sections that can be collapsed:

- **Notes** — defaults to **expanded**. The audit trail should be immediately visible, but
  the section is collapsible once a job accumulates many notes and the user wants to focus
  on the action bar. Collapse state is not persisted — always opens expanded on page load.

- **Approvals** — defaults to **expanded if there are any `PENDING` approvals, collapsed
  otherwise**. When there is nothing to act on, the section header (`Approvals (0 pending)`)
  communicates the state without forcing a scroll past an empty list. The pending count badge
  in the header remains visible even when collapsed, so outstanding requests are never hidden.

### 2. Status action bar

Available status transition buttons are shown inline, not in a dropdown. Showing the allowed
next states as discrete buttons makes the valid transitions explicit and discoverable.

The buttons rendered depend on the job's current status and the caller's role:

| Current status | Button(s) shown | Who sees them |
|----------------|-----------------|---------------|
| `NEW` | **Start** → `IN_PROGRESS` | OWNER, ADMIN, assigned MEMBER |
| `IN_PROGRESS` | **Complete** → `COMPLETED` | OWNER, ADMIN, assigned MEMBER |
| `IN_PROGRESS` | **Block** → modal | OWNER, ADMIN, assigned MEMBER |
| `BLOCKED` | **Unblock** → `IN_PROGRESS` | OWNER, ADMIN, assigned MEMBER |
| `COMPLETED` | **Reopen** → `IN_PROGRESS` | OWNER, ADMIN only |

**Confirmation dialogs:**

- `IN_PROGRESS → COMPLETED` ("Complete"): a **confirmation dialog** is shown — "Mark this job
  as complete? This cannot be undone by the assigned member." — because MEMBERs cannot reopen a
  job once completed.
- `COMPLETED → IN_PROGRESS` ("Reopen"), `BLOCKED → IN_PROGRESS` ("Unblock"): no confirmation
  dialog — these are reversible by OWNER/ADMIN.
- `NEW → IN_PROGRESS` ("Start"): no confirmation — low stakes, reversible.

### 3. Blocked state — visual banner

When a job has `status = 'BLOCKED'`, a prominent **red banner** appears directly below the
title bar and before the info section:

```
⚠ Blocked since Mar 3 by Jane Doe
  "Waiting for client to provide site access credentials"
```

The banner shows: blocked since (date), blocked by (display name), and the block reason text.
It disappears immediately when the job is unblocked.

This treatment makes the blocked state impossible to miss — it does not rely on the status
badge alone, which can be overlooked when scanning the page.

### 4. Block action — modal

Clicking **Block** opens a modal. Inside:

- A **searchable dropdown** pre-populated with the project's existing block reasons (fetched
  from `GET /api/projects/:projectId/block-reasons`).
- The user can select an existing reason or type a new free-text reason in the same field
  (combobox pattern: the input filters the dropdown as you type; if no match, the typed text
  is used as a new reason).
- A **Confirm** button calls `PATCH /status` with `{ status: "BLOCKED", reason: "..." }`.

The find-or-create behaviour is handled server-side (ADR-0008); the frontend passes the raw
reason string, not a reason ID.

**Unblock**: no modal — a single **Unblock** button calls `PATCH /status` with
`{ status: "IN_PROGRESS" }` directly (with a brief confirmation dialog: "Unblock this job
and resume work?").

### 5. Add note — inline form

A compact add-note form sits at the **bottom of the Notes section**, always visible:

```
┌───────────────────────────────────────┐
│ Add a note...                         │
│                                 0/2000│
└───────────────────────────────────────┘
                                  [Add Note]
```

- A `<textarea>` with placeholder "Add a note…".
- A live character counter (`n/2000`) shown bottom-right of the textarea — counts down to
  0, turns red when over 1900 characters.
- An **Add Note** button, disabled while empty or over the 2000-char limit.
- Notes are **not in a modal** — the inline form keeps the interaction lightweight and
  emphasises that notes are part of the continuous audit thread, not a separate action.
- Because notes are immutable, a confirmation prompt ("Notes cannot be edited or deleted.
  Add anyway?") is shown on first submit per session (stored in `sessionStorage`). Repeat
  submits within the same session skip the prompt.

### 6. Request approval — modal

Clicking **Request Approval** opens a modal:

- A `<textarea>` for the description (required, max 500 chars, counter shown).
- A **Submit** button that calls `POST /api/projects/:projectId/jobs/:jobId/approvals`.
- On success, the modal closes and the new `PENDING` approval appears at the top of the
  Approvals section immediately (optimistic insert, confirmed on query invalidation).

**Pending indicator:** if the job has any `PENDING` approvals, a small orange badge appears
on the "Approvals (n)" section header showing the pending count. This makes the outstanding
requests visible without scrolling to the approvals section.

### 7. Approve / Reject — inline on approval card

Each approval card in the Approvals section shows:

```
┌─────────────────────────────────────────────┐
│ Need to purchase transformer — €800          │
│ Requested by Jane Doe · Mar 3, 09:15        │
│                          [Reject] [Approve] │
└─────────────────────────────────────────────┘
```

- **Approve** (green, primary) and **Reject** (red, secondary) buttons are shown inline on
  each `PENDING` card — only for OWNER and ADMIN.
- Clicking either opens a small **comment modal** with an optional `<textarea>` and
  confirmation button. The comment is optional (consistent with ADR-0010).
- Decided approvals (APPROVED / REJECTED) show the decision badge, approver name, date,
  and comment (if any). No action buttons.

### 8. Edit job — modal

An **Edit** button in the page header (OWNER and ADMIN only) opens the same modal used on
the job list page (ADR-0014 §3), pre-filled with current values. This avoids introducing a
second edit surface.

### 9. Delete job

A kebab menu (`⋮`) in the page header exposes **Delete Job** (OWNER and ADMIN only). Opens
a confirmation dialog before calling `DELETE /api/projects/:projectId/jobs/:jobId`. On
success, navigates back to the job list.

### 10. Optimistic updates

| Action | Optimistic? | Rationale |
|--------|-------------|-----------|
| Status change (Start, Complete, Reopen, Unblock) | **Yes** | Instant feedback for the most common action; server rarely rejects a valid transition |
| Block | No | Requires a round-trip to resolve/create the block reason server-side |
| Add note | No | Notes are permanent; better to confirm server write before showing |
| Request approval | No | `requestedAt` is set by the DB; optimistic insert would show a wrong timestamp |
| Approve / Reject | **Yes** | Card should flip state immediately; concurrent conflict (ADR-0010) reverts on error |

### 11. Permission guards

| UI element | MEMBER (own job) | OWNER / ADMIN |
|---|---|---|
| View detail page | ✓ | ✓ |
| Status buttons (Start, Complete) | ✓ | ✓ |
| Status button (Reopen) | ✗ | ✓ |
| Block button | ✓ | ✓ |
| Unblock button | ✓ | ✓ |
| Add note form | ✓ | ✓ |
| Request Approval button | ✓ | ✓ |
| Approve / Reject buttons | ✗ | ✓ |
| Edit job button | ✗ | ✓ |
| Delete job (kebab) | ✗ | ✓ |

The `useProjectRole(projectId)` hook from ADR-0013 drives all guards. The backend enforces
the same rules independently.

### 12. TanStack Query key conventions — notes and approvals

Extending the conventions from ADR-0014:

| Key | Usage |
|-----|-------|
| `['jobs', projectId, jobId, 'notes']` | Notes for a job |
| `['jobs', projectId, jobId, 'approvals']` | Approvals for a job |
| `['block-reasons', projectId]` | Project block reason list (for block modal) |

Mutations:
- Add note → invalidate `['jobs', projectId, jobId, 'notes']`
- Request approval → invalidate `['jobs', projectId, jobId, 'approvals']`
- Approve / Reject → invalidate `['jobs', projectId, jobId, 'approvals']`
- Status change (including block/unblock) → invalidate `['jobs', projectId, jobId]` and
  `['jobs', projectId]` (list badge counts change)

### 13. Feature folder structure

```
features/jobs/
├── JobListPage.tsx
├── JobDetailPage.tsx         # new
├── components/
│   ├── JobStatusBar.tsx      # status transition buttons
│   ├── BlockedBanner.tsx     # red blocked banner
│   ├── BlockModal.tsx        # block reason combobox + confirm
│   ├── NoteThread.tsx        # chronological note list + add-note form
│   ├── ApprovalList.tsx      # approval cards with inline approve/reject
│   ├── RequestApprovalModal.tsx
│   └── ApprovalDecisionModal.tsx
├── useJobs.ts                # existing; add useJob (single), useUpdateJobStatus
├── useNotes.ts               # useNotes, useAddNote
├── useApprovals.ts           # useApprovals, useRequestApproval, useDecideApproval
├── useBlockReasons.ts        # useBlockReasons
└── index.ts
```

---

## Alternatives Considered

### Alternative 1: Tabs for Notes and Approvals sections

Use a tabbed interface — "Notes" tab and "Approvals" tab — to keep the page vertically
compact.

**Pros:** Shorter page; each section gets full width.

**Cons:** Hides content by default — a MEMBER arriving at the job detail may not realise
there are pending approvals waiting. Tabs force an explicit click to reveal what is arguably
part of the job's primary state.

**Why rejected:** Accordions achieve the same vertical savings while keeping both section
headers (including the pending count badge) permanently visible on the page.

### Alternative 2: All sections always expanded, no accordion

Keep all four sections permanently expanded with no collapse affordance.

**Pros:** Simplest implementation; no expand/collapse state to manage.

**Cons:** As notes accumulate the page grows unbounded. A job with 30 notes and 10 approval
records forces the user to scroll past all of it to reach the action bar on a return visit.

**Why rejected:** Notes and Approvals are the only sections that grow without bound.
Making them collapsible is a low-complexity improvement with a clear pay-off as jobs age.
Info and the action bar remain always visible, so the primary interactions are never buried.

### Alternative 2: Dropdown for status transitions

Replace the discrete status buttons with a single "Change Status" dropdown listing all valid
next states.

**Pros:** Compact — one control regardless of how many transitions are available.

**Cons:** Hides available actions; requires two clicks (open dropdown, select). The action bar
already has a limited set of buttons (max 3: one status transition + Block + Request Approval);
space is not a concern.

**Why rejected:** Explicit buttons are clearer and faster. The allowed next state is always
exactly one (except `IN_PROGRESS`, which can go to `COMPLETED` or `BLOCKED`), making a
dropdown redundant in most cases.

### Alternative 3: Note submission without immutability warning

Skip the confirmation prompt on note submit; let the immutability constraint be a backend
concern that users learn about through the UI (no edit/delete controls).

**Pros:** Removes friction from the most common note-adding action.

**Cons:** New users may be confused when they realise they cannot fix a typo. One-time
per-session confirmation (stored in `sessionStorage`) adds friction only on first use.

**Why rejected:** Notes are permanent by design. The one-time prompt sets expectations
without becoming a repeated annoyance.

### Alternative 4: Approval decision inline (no modal)

Inline confirm/reject buttons on the approval card with no comment modal — click once to
approve.

**Pros:** Fewest clicks to decide.

**Cons:** Removes the optional comment — approvers frequently need to add context ("Approved,
order within budget", "Rejected, use alternative supplier"). Losing the comment field
reduces the audit value of the approval trail.

**Why rejected:** The comment is optional but high value. A modal adds one extra click and
preserves the comment field.

---

## Consequences

### Positive

- Single-scroll layout keeps notes and approvals always visible — the audit trail is never
  more than a scroll away
- Inline note form and explicit status buttons minimise click depth for the two most common
  actions
- Permission guards driven by `useProjectRole` are reused across all action controls —
  no new permission primitives needed
- Optimistic status change gives immediate visual feedback for the most frequent interaction

### Negative

- Single-scroll page grows if a job accumulates many notes and approvals; pagination of both
  sections is deferred to post-MVP
- One-time immutability confirmation for notes adds session state (`sessionStorage`) — a minor
  complexity not present elsewhere

### Neutral

- Block reason combobox shares the typeahead pattern from the member search in ADR-0013 but
  is scoped to `GET /block-reasons` — no new UI primitive needed
- `useBlockReasons` query is only fetched when the block modal is opened (lazy query) to
  avoid an unnecessary network request on every job detail load

---

## References

- [ADR-0005: Roles and Permissions Model](./0005-roles-and-permissions.md)
- [ADR-0007: Job Model and Status Flow](./0007-job-model-and-status-flow.md)
- [ADR-0008: Blocking Model](./0008-blocking-model.md)
- [ADR-0009: Notes Model](./0009-notes-model.md)
- [ADR-0010: Approval Model](./0010-approval-model.md)
- [ADR-0011: Frontend Architecture](./0011-frontend-architecture.md)
- [ADR-0013: Projects Screens](./0013-projects-screens.md)
- [ADR-0014: Jobs Screens](./0014-jobs-screens.md)
