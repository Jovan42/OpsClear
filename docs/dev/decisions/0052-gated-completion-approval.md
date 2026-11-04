# ADR-0052: Gated Completion Requiring Approval

**Status:** Proposed
**Date:** 2026-11-04
**Author:** Jovan Manojlovic

## Context

Approvals today (ADR-0010) are manual: a Member asks for a decision, an Owner/Admin approves or rejects, and nothing happens automatically. Some owners want hard enforcement: **no job is marked Completed without their sign-off.** Today a Member assigned to a job can complete it directly.

Promoted from JOB-006 (Future Consideration, PRJ-002) into PRJ-012 / MIL-036. This ADR is JOB-247.

### Facts from the existing system that shape this design

- **Approval statuses** are `PENDING`, `APPROVED`, `REJECTED` (`chk_approval_status`, V001). There's no way to withdraw a request.
- **Who can request**: Owners/Admins on any job; Members only on jobs assigned to them (`ApprovalService.requireCanRequestApproval`). **Only Owners/Admins decide.**
- **Who can complete**: the assignee or an Owner/Admin. The only legal transition into Completed is `IN_PROGRESS → COMPLETED` (`JobService.validateTransition`). Reopen (`COMPLETED → IN_PROGRESS`) is Owner/Admin only.
- **Approval endpoints are gated** by the `APPROVALS` add-on (`@RequiresAddon`, ADR-0031).
- **There is no per-project settings storage**: `projects` has only name/description/owner, and `UpdateProjectRequest` only name/description. Block reasons have their own table. ADR for JOB-248 (section visibility) will also need per-project settings.
- **Notifications** (ADR-0051) already cover `APPROVAL_REQUESTED`, `APPROVAL_DECIDED` and `JOB_STATUS_CHANGED`, which this feature reuses.

## Decision

Add a per-project **"Require approval to complete jobs"** toggle. On a gated project, when a **Member** tries to complete a job, the job stays In Progress and a **completion approval** is created automatically. When an Owner/Admin **approves** it, the job is **completed automatically** if that's still a legal transition. Owners/Admins complete jobs directly; their action is the sign-off. Members (or Owners/Admins) can **withdraw** a pending completion approval. Manual approvals keep working exactly as today.

## Product decisions

### The gate setting

- One toggle per project: **"Require approval to complete jobs"**, in Project settings. Off by default.
- Only project **Owners/Admins** can change it.
- Any project Owner/Admin can approve a completion, same as existing approvals. No named approvers.
- **Requires the `APPROVALS` add-on.** Without it the toggle is shown locked, and the gate is **not enforced** (Members complete directly), because nobody could decide the approvals.

### Who is gated

| Who clicks Complete | Gated project | Ungated project |
|---|---|---|
| Member (assignee) | Completion approval created; job stays In Progress | Completes directly (today's behaviour) |
| Owner / Admin | **Completes directly** (their action is the sign-off) | Completes directly |

### Completion approval lifecycle

1. **Request**: a Member clicks Complete on an In Progress job in a gated project. A small confirm dialog says approval is required, with an optional message. A completion approval is created (`kind = COMPLETION`, description = the message or a default "Requesting to complete this job"). The job stays **In Progress**.
2. **One at a time**: a job has at most one pending completion approval. Clicking Complete again shows the existing request instead of creating a new one.
3. **While pending** the job can still be edited, blocked and unblocked, and get notes or manual approvals, as normal.
4. **Approve** (Owner/Admin):
   - The approval is recorded as **Approved**.
   - In the same transaction the job is re-checked. If it is **In Progress**, it moves to **Completed** automatically. The status history entry is attributed to the approver, and the requester's request is visible in the approval.
   - If the job is **not In Progress** (e.g. it was blocked meanwhile), the approval is still recorded as Approved, but **the job is not completed**. The approval is marked "approved, not applied" with the reason (e.g. "job was Blocked"). The approver sees this immediately in the UI, and the requester is told via the `APPROVAL_DECIDED` notification (ADR-0051), whose payload carries `completionApplied: false` and the reason. The job can then be completed normally once it's back In Progress: a Member requests again, or an Owner/Admin completes it directly.
5. **Reject** (Owner/Admin): the approval is Rejected with an optional comment. **The job stays at its current status** (settled; no revert).
6. **Withdraw**: the **requester** or any project **Owner/Admin** can withdraw a pending completion approval. It becomes **Cancelled**; the job stays In Progress. If someone other than the requester withdraws it, the requester gets an `APPROVAL_DECIDED` notification (payload `decision: CANCELLED`).
7. **Owner/Admin completes the job directly while a request is pending**: the pending completion approval is cancelled automatically (comment: "Completed directly by <name>").
8. **Job deleted** while a request is pending: the approval is cancelled automatically.

### Manual approvals

- **Coexist unchanged.** Members and Owners/Admins can still request approvals for any other reason.
- On gated projects, **only the completion approval can complete a job**. Approving a manual request never changes job status.
- Withdrawing stays **completion-only** for V1. Manual approvals keep today's behaviour.

### Turning the gate off

- Pending completion approvals **stay valid**: they can still be approved (and auto-complete), rejected or withdrawn.
- Turning the gate off only changes what happens on the **next** Complete click.

### How it looks

- **Job detail banner** (same pattern as `BlockedBanner`): "Completion requested by <name> · <time>" with the optional message. **Approve / Reject** for Owners/Admins, **Withdraw** for the requester.
- **Status bar** (`JobStatusBar`): on gated projects, a Member's Complete button reads **"Request completion"**. While a request is pending it shows a disabled **"Approval requested"** state.
- **Job list**: a small **"Awaiting completion approval"** badge next to the status.
- **Approval lists** (job's approvals section, project approval queue, cross-project queue on `/overview`, Dashboard pending approvals): completion approvals are labelled **"Completion"** so they're distinct from manual ones. Approved-not-applied and Cancelled approvals show their state clearly.
- **Project settings**: the toggle with a one-line explanation ("Members' jobs need Owner/Admin approval before they're completed. Owners and Admins complete directly.").

## Technical design

### Database

New migration (next free number at implementation time; `V042` if ADR-0051's `V041` has landed):

```sql
-- Project gate
ALTER TABLE projects
    ADD COLUMN require_completion_approval BOOLEAN NOT NULL DEFAULT false;

-- Approval kind, cancelled status, completion outcome
ALTER TABLE approvals
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN completion_applied BOOLEAN,
    ADD COLUMN completion_not_applied_reason VARCHAR(50),
    ADD CONSTRAINT chk_approval_kind CHECK (kind IN ('MANUAL', 'COMPLETION'));

ALTER TABLE approvals DROP CONSTRAINT chk_approval_status;
ALTER TABLE approvals ADD CONSTRAINT chk_approval_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

-- At most one pending completion approval per job
CREATE UNIQUE INDEX uq_approvals_one_pending_completion
    ON approvals(job_id) WHERE kind = 'COMPLETION' AND status = 'PENDING';
```

- `completion_applied` is set only for approved completion approvals (`true` / `false`). `completion_not_applied_reason` holds the job status at decision time (e.g. `BLOCKED`, `COMPLETED`).
- Existing rows become `kind = 'MANUAL'` via the default. No backfill needed.
- `CANCELLED` is only ever set on completion approvals in V1 (enforced in the service, not the DB, so manual withdrawal can be added later without a migration).

### API

| Method | Path | Change |
|---|---|---|
| `PATCH` | `/api/projects/{id}/settings` | **New.** Body `{ "requireCompletionApproval": true }`. Owner/Admin only. Turning it **on** requires the `APPROVALS` add-on (403 otherwise). A small settings endpoint (not `PUT /api/projects/{id}`) so later per-project settings (e.g. JOB-248) can join it without changing the name/description update. |
| `GET` | `/api/projects`, `/api/projects/{id}` | `requireCompletionApproval` added to `ProjectResponse`. |
| `PATCH` | `/api/projects/{projectId}/jobs/{jobId}/status` | Body gains optional `message` (used only for completion requests). On a gated project, a Member's `COMPLETED` returns **202 Accepted** with the job (still `IN_PROGRESS`) and its `pendingCompletionApproval`. If one is already pending → **409**. |
| `PATCH` | `/api/projects/{projectId}/jobs/{jobId}/approvals/{approvalId}/status` | Unchanged request. For completion approvals the response adds `completionApplied` and `completionNotAppliedReason`. |
| `POST` | `/api/projects/{projectId}/jobs/{jobId}/approvals/{approvalId}/cancel` | **New.** Requester or Owner/Admin. Only `kind = COMPLETION` and `PENDING`; otherwise 409 (or 403 for others). `@RequiresAddon(APPROVALS)`. |
| — | `ApprovalResponse` | Adds `kind`, `completionApplied`, `completionNotAppliedReason`. `status` may be `CANCELLED`. |
| — | `JobResponse` / job list items | Add `pendingCompletionApproval: { id, requesterId, requesterName, requestedAt } \| null`. |

### Backend

- **`JobService.updateStatus`**: when `to == COMPLETED` and the project has `require_completion_approval = true`, the `APPROVALS` add-on is active, and the caller is a `MEMBER`: validate the transition as today, then call `ApprovalService.createCompletionApproval(...)` instead of changing status, and return the job with its pending approval (controller maps this to 202). Owner/Admin path unchanged, plus auto-cancel of any pending completion approval.
- **`ApprovalService`**:
  - `createCompletionApproval`: creates a `kind = COMPLETION` approval. The unique partial index guards against races; a conflict maps to 409.
  - `decide`: for `kind = COMPLETION` + `APPROVED`, re-reads the job **with a row lock** (`SELECT … FOR UPDATE`). If it's `IN_PROGRESS`, it performs the transition through the same internal method `JobService` uses (status update + `job_status_history` row with `changed_by = approver`) and sets `completion_applied = true`. Otherwise it sets `completion_applied = false` and the reason. All in one transaction.
  - `cancel`: requester or Owner/Admin, completion + pending only.
  - Cancels pending completion approvals when the job is completed directly or deleted.
  - This is a service-to-service call between `ApprovalService` and `JobService`'s transition logic. To avoid a circular dependency and stay within the "no service-to-service" rule (ADR-0017 §4), the shared transition logic moves into a small **`JobStatusTransitioner`** component (validate + update + history row) that both services use.
- **`ProjectService.updateSettings`** + `ProjectSettingsController` (or a method on `ProjectController`).
- **Events for ADR-0051**: completion approvals publish the same `APPROVAL_REQUESTED` / `APPROVAL_DECIDED` events as manual ones, with `kind` in the payload. Auto-completion publishes `JOB_STATUS_CHANGED` with the approver as actor. Cancellation by someone other than the requester publishes `APPROVAL_DECIDED` with `decision: CANCELLED`. **No new notification types and no change to ADR-0051's check constraint.** If ADR-0051 isn't implemented yet when this lands, publishing these events is simply a no-op until it is.

### Frontend

- **Project settings page**: toggle + explanation, locked when the `APPROVALS` add-on is missing (same locked pattern as other add-on features).
- **`JobStatusBar`**: "Request completion" for Members on gated projects, opening a confirm dialog with an optional message. "Approval requested" disabled state while pending.
- **`CompletionRequestBanner`** (new, mirrors `BlockedBanner`): requester, time, message; Approve / Reject (reusing `ApprovalDecisionModal`) for Owners/Admins, Withdraw for the requester.
- **Job list**: "Awaiting completion approval" badge.
- **Approval lists** (`ApprovalList`, `ApprovalQueuePage`, Dashboard, `/overview`): "Completion" label; "Approved, not applied" and "Cancelled" states.
- **Toasts**: after approving, if `completionApplied === false`, the approver sees "Approved, but the job wasn't completed because it is <status>."
- **i18n**: en + sr strings in the existing approvals/jobs namespaces.

### Constraints & edge cases

- **Race: two Members / double click** → unique partial index; the second request gets 409 and the UI shows the existing request.
- **Race: approve while someone blocks the job** → row lock in `decide`; whichever commits first wins, and the other sees the updated status (approve → "not applied", or block → fails as an illegal transition from Completed).
- **Project completed** (read-only) → existing `requireProjectNotCompleted` blocks requests, decisions and cancellations alike.
- **`APPROVALS` add-on lapses** while requests are pending → approval endpoints are locked, so pending requests can't be decided or withdrawn until the add-on returns. Members can complete directly in the meantime (gate not enforced), and doing so cancels their pending request automatically.
- **Requester removed from the project** → the request stays pending; Owners/Admins can still decide or withdraw it.
- **Reopen** (`COMPLETED → IN_PROGRESS`) is unaffected; it's already Owner/Admin only.

## Alternatives considered

### Job holds a reference to its pending completion approval (`jobs.pending_completion_approval_id`)
Proposed in JOB-247. Rejected in favour of an approval **`kind`** column plus a unique partial index. Both remove the "which approval authorizes completion?" ambiguity. But `kind` also keeps the history: after a decision you can still see that an approval was a completion request (for the "Completion" label and "approved, not applied"). A job column only knows about the *pending* one and needs clearing on every outcome.

### Owners/Admins are gated too
Rejected. With self-approval forbidden, a project with a single Owner/Admin could never complete anything. With self-approval allowed, it's an extra click that proves nothing. The Owner/Admin's own action is the sign-off.

### Named approvers per project
Rejected for V1. Approvals already default to "any Owner/Admin"; a separate approver list is a new concept with its own management UI.

### Block-and-prompt (Complete fails with "request approval first")
Rejected (settled in JOB-247). Two steps for the user, and it reintroduces the ambiguity of which approval authorizes completion.

### Gate any transition, not just Completed
Rejected (settled). Completed is the real pain point; gating arbitrary transitions is a much larger surface.

### Revert the job on rejection
Rejected (settled). The job never left In Progress, so there's nothing to revert.

### Allow withdrawing any approval
Considered. Kept completion-only for V1 to stay in scope. The DB allows `CANCELLED` generally, so extending later is service + UI only.

### Auto-cancel pending requests when the gate is turned off
Rejected (decided). The requests were valid when made, and leaving them lets Owners/Admins still approve them.

### Store the gate in a new `project_settings` table
Considered, since JOB-248 also needs per-project settings. Rejected for this ADR: one boolean doesn't justify a new table, and JOB-248's ADR can decide its own storage. The new `PATCH /api/projects/{id}/settings` endpoint gives both features a common API regardless of storage.

### A new notification type for "approved, not applied"
Rejected. `APPROVAL_DECIDED` with `completionApplied: false` in the payload covers it without changing ADR-0051's type list.

## Consequences

### Positive
- Owners get real enforcement of "nothing is done until I say so", per project.
- Members express intent once. The job completes the moment it's approved, with no second click.
- No ambiguity between completion and manual approvals, and the history stays readable.
- Reuses approvals, notifications and existing UI patterns (`BlockedBanner`, `ApprovalDecisionModal`).

### Negative
- `PATCH …/status` now has two outcomes (200 completed / 202 requested), which clients must handle.
- Extracting `JobStatusTransitioner` touches `JobService`, a central class, so it needs careful regression testing.
- "Approved, not applied" is a new state users must understand (mitigated by clear labelling and the toast/notification).

### Neutral
- `PATCH /api/projects/{id}/settings` is a new, deliberately small endpoint that JOB-248 can extend.
- The Postman collection gains the settings and cancel endpoints and the 202 status example.

## Implementation order

1. **DB**: migration: `projects.require_completion_approval`, approval `kind` / `completion_applied` / `completion_not_applied_reason`, `CANCELLED` status, unique partial index.
2. **Backend 1: gate setting and transition refactor**: `PATCH /api/projects/{id}/settings`, `requireCompletionApproval` in project responses, extract `JobStatusTransitioner` from `JobService` with no behaviour change (regression tests). *Blocked by DB.*
3. **Backend 2: completion approvals**: gated path in status update (202, 409), `createCompletionApproval`, auto-complete on approve with row lock and "not applied" handling, reject, cancel endpoint, auto-cancel on direct completion/deletion, response fields, ADR-0051 events. Unit + integration tests for every lifecycle path and race guard. *Blocked by Backend 1.*
4. **Frontend 1: gate toggle**: Project settings toggle (locked without `APPROVALS`), E2E. *Blocked by Backend 1.*
5. **Frontend 2: completion request flow**: status bar states, confirm dialog, `CompletionRequestBanner` (approve/reject/withdraw), job list badge, "Completion" labels and new states in all approval lists, toasts, E2E for request → approve (auto-complete), reject, withdraw, approve-not-applied. *Blocked by Backend 2 and Frontend 1.*

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`): project settings, completion request (202), cancel, decision response fields
- [ ] Add example requests for each lifecycle path
- [ ] Update environment variables if needed
- [ ] Test the flow manually before marking complete

## References

- ADR-0010: Approval model
- ADR-0016: Approvals screens
- ADR-0017: Dashboard (§4 service-to-service exception)
- ADR-0028: Job status change history
- ADR-0031: Feature gating (`APPROVALS` add-on)
- ADR-0046: Cross-project approval queue
- ADR-0051: In-app notification centre (`APPROVAL_REQUESTED`, `APPROVAL_DECIDED`, `JOB_STATUS_CHANGED`)
- JOB-006 (Future Consideration, PRJ-002): original idea
- JOB-247 (PRJ-012 / MIL-036): this ADR's job and its settled decisions
