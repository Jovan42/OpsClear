# ADR-0051: In-App Notification Centre

**Status:** Accepted
**Date:** 2026-11-04
**Author:** Jovan Manojlovic

## Context

Users have no way to know that something relevant happened (a new approval request, a status change, a note) without navigating to check. That is exactly the "constant status calls" noise OpsClear exists to remove. Time-sensitive admin events (a recurring schedule missing a run, a subscription going past-due) are also only visible to someone who happens to open the right page.

Promoted from JOB-001 (Future Consideration, PRJ-002) into PRJ-012 / MIL-035. This ADR is JOB-246.

### Facts from the existing system that shape this design

- **No async or event infrastructure exists.** The only background mechanism is `@Scheduled` (`SchedulingConfig`, `SchedulerPoller`). Asynchronous fan-out (settled in JOB-246) needs new, small infrastructure.
- **Production runs a single backend instance** (`docker-compose.prod.yml`), so open SSE connections can be tracked in memory. No broker is needed for V1.
- **The frontend authenticates with an `Authorization: Bearer` header** (`frontend/src/api/client.ts`). The browser's native `EventSource` cannot send custom headers, so the SSE client must be `fetch`-based.
- **nginx buffers proxied responses and uses default timeouts** on `/api/` (`frontend/nginx.conf`). That would hold back or cut SSE streams, so the stream needs its own `location` block.
- **User preferences (ADR-0023) live in `localStorage`.** They can't drive server-side decisions such as muting. The `users.preferences JSONB` column (V001) exists but is unused, so it can hold notification settings without a migration.
- **Members only see jobs assigned to them** (ADR-0017 §5). A notification must never point a user at a job they can't open.
- **Paddle sends `subscription.past_due`**, handled in `PaddleWebhookService`. There's no separate payment-failed event, so "past-due / payment failed" is one trigger: the subscription entering `PAST_DUE`.
- **Jobs can be created already assigned**: manually, from a template (`assigneeMode`), or by a recurring schedule that rotates through its assignees (`ScheduleJobCreator`). Schedule-created jobs have no human actor.
- **Job relationships** include `BLOCKED_BY` (ADR-0022, `JobRelationshipType`), so "the job I'm waiting on is done" is detectable.
- **Deadlines are effectively dates** (stored as midnight UTC). The Dashboard marks a job overdue from the start of its deadline day (noted in ADR-0050).

## Decision

Add a notification centre: notifications are stored per recipient in a new `notifications` table, created asynchronously after the triggering transaction commits, and pushed live over **Server-Sent Events**. The UI is a **bell with an unread badge and a dropdown** in the header, plus a **full Notifications page**. Users can **mute individual types**. Notifications are pruned automatically (read after 30 days, unread after 90).

## Product decisions

### Event types and recipients

One rule applies to every type: **the person who performed the action is never notified about it.** If the same person qualifies through several rules (e.g. assignee and project Admin), they get **one** notification.

| Type | Trigger | Recipients |
|---|---|---|
**Job events**

| Type | Trigger | Recipients |
|---|---|---|
| `APPROVAL_REQUESTED` | Approval request created | Project Owners/Admins (they can decide it) |
| `APPROVAL_DECIDED` | Approval approved or rejected | The person who requested it |
| `JOB_ASSIGNED` | Job assigned or reassigned, **or created already assigned** (manually, from a template, or by a recurring schedule) | The new assignee |
| `JOB_STATUS_CHANGED` | Any status change **except to Blocked** (incl. Completed, reopen) | Assignee + project Owners/Admins |
| `JOB_BLOCKED` | Job moved to Blocked | Assignee + project Owners/Admins. Shown with a distinct, urgent style. |
| `BLOCKER_CLEARED` | A job that other jobs are `BLOCKED_BY` is completed | Assignee of each waiting job that is not Completed |
| `JOB_UPDATED` | Someone changes a job's **deadline or priority** (other field edits don't notify) | Assignee |
| `JOB_DELETED` | Job deleted | Assignee |
| `NOTE_ADDED` | Note added to a job | Assignee + **thread participants** (anyone who has already written a note on that job) |
| `JOB_DEADLINE_SOON` | Daily check: deadline is **tomorrow** | Assignee |
| `JOB_OVERDUE` | Daily check: deadline day **has passed** and the job isn't Completed | Assignee + project Owners/Admins. Urgent style. |

**Project, organisation and account events**

| Type | Trigger | Recipients |
|---|---|---|
| `PROJECT_MEMBER_ADDED` | User added to a project | The added user (payload includes their role) |
| `SCHEDULE_RUN_MISSED` | Recurring schedule records a missed run | Project Owners/Admins |
| `INVITE_ACCEPTED` | Someone accepts an org invite | The inviter (`invited_by`). If that's empty or they've left the org, the org's Owners/Admins. |
| `FEEDBACK_RESOLVED` | Super admin credits or declines a feedback submission | The submitter (payload: outcome, credit amount if any) |
| `SUBSCRIPTION_PAST_DUE` | Org subscription enters `PAST_DUE` | Organisation Owners/Admins |

- **Blocked is its own type, not a status change.** A move to Blocked creates only `JOB_BLOCKED`.
- **System-triggered events** (schedule-created jobs, deadline checks, missed runs, Paddle webhooks, super-admin actions) have no actor from the org. The "never notify the actor" rule simply doesn't remove anyone, and the UI shows "OpsClear" as the source.
- **One action, one notification per person.** Completing a job notifies its watchers with `JOB_STATUS_CHANGED` and the assignees of jobs waiting on it with `BLOCKER_CLEARED`. Those are different people or different jobs, so neither replaces the other. If a single update changes both the assignee and the deadline, the new assignee gets only `JOB_ASSIGNED` (the payload carries the deadline).
- **Deleted jobs**: `JOB_DELETED` is shown from the payload snapshot and isn't clickable.

### Deadline alerts

- A daily job runs at **06:00 UTC**.
- `JOB_DEADLINE_SOON`: open (not Completed), non-deleted jobs whose deadline date is **tomorrow (UTC)**.
- `JOB_OVERDUE`: open, non-deleted jobs whose deadline date is **before today (UTC)**, i.e. the whole deadline day has passed. This is deliberately later than the Dashboard's overdue badge (which turns on at the start of the deadline day): sending "you're overdue" on the day something is due would be wrong.
- Each alert is sent **once per job per deadline value**. If the deadline is moved, the job can alert again for the new date. Jobs in completed projects are skipped.
- **Visibility filter:** a recipient for a job-related type must currently be able to see the job: a project Owner/Admin, or its current assignee. A Member who wrote notes on a job and was later unassigned stops receiving `NOTE_ADDED` for it.
- **Unassigned jobs**: the assignee part of a rule simply yields no one.
- Features behind add-ons only produce notifications when the add-on is active, because the triggering action can't happen otherwise (e.g. `NOTE_ADDED` needs `NOTES`).

### UI

- **Bell icon in the app header** with an unread count badge (shows `99+` above 99).
- **Dropdown panel**: the 10 most recent notifications, with unread ones highlighted. Clicking one marks it read and navigates to its target: the job, the job's approvals section, the project (member added), the schedules page, org members (invite accepted), feedback page, or billing. Includes a "Mark all as read" action and a "View all" link.
- **Notifications page** (`/notifications`): full history with filters (All / Unread, and by type), per-item read/unread toggle, "Mark all as read". Paginated.
- **Text is rendered on the frontend** from the type and stored data (job friendly ID, title, actor name, etc.), so it follows the viewer's language (en/sr).
- **Urgent styling** for `JOB_BLOCKED`, `JOB_OVERDUE` and `SUBSCRIPTION_PAST_DUE`.
- **Empty states** via `EmptyState` (ADR-0041).

### Read / unread

- Per notification (settled), plus a "mark all as read" convenience action.
- Marking read in one tab updates the badge in other open tabs (pushed over SSE).

### Muting (in scope for V1)

- **Account settings → Notifications**: one on/off toggle per type. All on by default.
- Muted types are **not created** for that user (not hidden after the fact), so they don't count toward the badge or show up later.
- **`SUBSCRIPTION_PAST_DUE` can't be muted.** Billing problems affect the whole organisation.
- Settings are stored on the server (they drive fan-out) and apply across devices.

### Retention

- **Read** notifications are deleted **30 days after creation**.
- **Unread** notifications are deleted **90 days after creation**.
- A nightly job does the cleanup. The job, its notes, approvals and status history remain the permanent record; notifications are only pointers.

### Out of scope for V1

- Email, mobile push and browser (OS-level) notifications.
- Digest/batching ("5 status changes on PRJ-012").
- Per-project muting or per-job "watch/unwatch".
- Notifying the **previous** assignee when a job is reassigned away from them.
- Notifications for: edits to title/description/client, links, milestones, job types, templates, block-reason lists, project status changes, org role changes or removal, subscription cancellation/downgrade, API keys.
- Deadline alerts in each user's own timezone (V1 uses UTC dates; `users.timezone` exists but isn't set from the UI yet).

## Technical design

### Database

New migration `V041__notifications.sql`:

```sql
CREATE TABLE notifications (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID        NOT NULL REFERENCES organisations(id),
    recipient_id UUID        NOT NULL REFERENCES users(id),
    type         VARCHAR(40) NOT NULL,
    actor_id     UUID        REFERENCES users(id),
    project_id   UUID        REFERENCES projects(id),
    job_id       UUID        REFERENCES jobs(id),
    approval_id  UUID        REFERENCES approvals(id),
    payload      JSONB       NOT NULL DEFAULT '{}',
    read_at      TIMESTAMP,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_notifications_type CHECK (type IN (
        'APPROVAL_REQUESTED','APPROVAL_DECIDED','JOB_ASSIGNED','JOB_STATUS_CHANGED',
        'JOB_BLOCKED','BLOCKER_CLEARED','JOB_UPDATED','JOB_DELETED','NOTE_ADDED',
        'JOB_DEADLINE_SOON','JOB_OVERDUE','PROJECT_MEMBER_ADDED','SCHEDULE_RUN_MISSED',
        'INVITE_ACCEPTED','FEEDBACK_RESOLVED','SUBSCRIPTION_PAST_DUE'))
);

CREATE INDEX idx_notifications_recipient_created ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread  ON notifications(recipient_id) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_created           ON notifications(created_at);

-- Remembers which deadline alerts were already sent, independent of notification retention.
CREATE TABLE job_deadline_alerts (
    job_id   UUID        NOT NULL REFERENCES jobs(id),
    kind     VARCHAR(20) NOT NULL CHECK (kind IN ('DEADLINE_SOON','OVERDUE')),
    deadline TIMESTAMP   NOT NULL,
    sent_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (job_id, kind, deadline)
);
```

- `job_deadline_alerts` exists because notifications are pruned (30/90 days): using them to de-duplicate would re-alert a still-overdue job once its old notification is deleted. Keying on the deadline value means a moved deadline alerts again.

- `org_id` follows the naming of newer org-scoped tables (`org_settings`, `org_credits`, …), not the older `organisation_id` columns on `users`/`projects`.
- `payload` stores a **snapshot** needed for display: job friendly ID and title, project friendly ID and name, actor name, from/to status, block reason, approval decision, schedule name, etc. Notifications still read correctly after a job is renamed.
- No soft delete: notifications are hard-deleted by retention.
- Mute settings go in the existing `users.preferences` JSONB under `notifications.muted` (array of types). No migration needed.

### API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/notifications?unread=true&type=…&page=…&size=…` | Caller's notifications, newest first, paginated |
| `GET` | `/api/notifications/unread-count` | `{ "count": 7 }` |
| `PATCH` | `/api/notifications/{id}` | Body `{ "read": true \| false }` |
| `POST` | `/api/notifications/read-all` | Mark all caller's unread as read |
| `GET` | `/api/notifications/stream` | SSE stream (`text/event-stream`) |
| `GET` | `/api/user/notification-settings` | `{ "muted": ["JOB_STATUS_CHANGED"] }` |
| `PUT` | `/api/user/notification-settings` | Replace muted list. `SUBSCRIPTION_PAST_DUE` → 400. |

- All endpoints act only on the caller's own notifications. Another user's ID → 404, not 403, so existence isn't revealed.
- The list endpoint also applies the **visibility filter** at read time: notifications are only returned while the caller is still a member of the notification's organisation, and project-scoped ones only while the caller is still a member of that project and the project isn't deleted. The unread count, mark read and mark all read apply the same filter.
- **Not add-on gated.** The notification centre is core: it's a delivery channel for actions that are themselves gated. *Open to change in review* if it should be sold as an add-on.

Notification response shape:

```json
{
  "id": "…",
  "type": "JOB_BLOCKED",
  "read": false,
  "createdAt": "2026-11-04T10:15:00Z",
  "actor": { "id": "…", "name": "Alice Example" },
  "projectFriendlyId": "PRJ-012",
  "jobFriendlyId": "JOB-245",
  "approvalId": null,
  "payload": { "jobTitle": "…", "projectName": "…", "blockReason": "Waiting for client" }
}
```

### SSE stream

- **Events**:
  - `notification`: a new notification (same shape as above).
  - `read-state`: `{ ids: [...], read: true|false }` or `{ all: true }`, so other tabs update their badge and list.
  - `heartbeat`: a comment line every **25 s**, to keep proxies from closing idle connections and to detect dead clients.
- **No replay.** The stream carries only live events. On every (re)connect the client re-fetches the unread count and the first page, so nothing missed while disconnected is lost. This removes the need for `Last-Event-ID` handling and per-connection buffers.
- **Connection lifetime**: the server closes each stream after **30 minutes**. The client then reconnects with a fresh token, so a long-lived stream never outlives the Keycloak access token it was opened with.
- **Limits**: at most **5 open streams per user** (oldest closed when exceeded). No per-connection queue: sends are written straight to the emitter, and a failed send drops that emitter.
- **Client reconnect**: exponential backoff starting at 1 s, doubling to a 30 s cap, with ±20 % jitter. Reset after a stream has stayed open for 60 s. Pause while the tab is hidden for more than 5 minutes; reconnect on visibility.

### Backend

- **Domain events**: services publish small events via `ApplicationEventPublisher` at the point of each trigger:
  - `ApprovalService`: create, decide.
  - `JobService`: create with assignee, assign/reassign, status change, block, completion (also used for blocker-cleared), deadline/priority change, delete.
  - `JobTemplateService` / `ScheduleJobCreator`: job created with an assignee (if they don't go through `JobService.create`).
  - `NoteService`: create.
  - `ProjectMemberService`: member added.
  - `OrgInviteService`: invite accepted.
  - `FeedbackService`: credited (`markCreditedForOrg`), declined.
  - `SchedulerPoller` / `RecurringScheduleService`: missed run recorded.
  - `PaddleWebhookService`: transition into `PAST_DUE`.
- **`DeadlineAlertJob`**: `@Scheduled(cron = "0 0 6 * * *", zone = "UTC")`. Finds jobs due tomorrow and jobs whose deadline day has passed, skips those already in `job_deadline_alerts` for the current deadline, inserts the alert rows and publishes `JOB_DEADLINE_SOON` / `JOB_OVERDUE` events.
- **`NotificationEventListener`**: `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("notificationExecutor")`. It never runs for a rolled-back action and never slows the triggering request.
- **`notificationExecutor`**: a dedicated, bounded `ThreadPoolTaskExecutor` (core 2, max 4, queue 1,000). On rejection it logs a warning and drops the event: notifications are best-effort, and the triggering action has already succeeded. `@EnableAsync` is added in a new `AsyncConfig`.
- **`NotificationService`**: resolves recipients per the table above, removes the actor, de-duplicates, applies the visibility filter and each recipient's mutes, builds the payload snapshot, batch-inserts rows, then hands each saved notification to the SSE registry. It also implements list / count / mark read / mark all.
- **`NotificationStreamRegistry`**: in-memory `ConcurrentHashMap<UUID userId, List<SseEmitter>>`. Responsibilities: register/unregister on completion, timeout or error, the 5-stream cap, the 25 s heartbeat (a single `@Scheduled` task for all emitters) and send-to-user.
- **`NotificationController`** and **`NotificationSettingsController`**: endpoints above. `/stream` returns an `SseEmitter` with a 30-minute timeout.
- **`NotificationCleanupJob`**: `@Scheduled(cron = "0 30 3 * * *")`. Deletes `read_at IS NOT NULL AND created_at < now() - 30 days`, then `created_at < now() - 90 days`.
- **Security**: `/api/notifications/stream` uses the normal JWT filter chain. The fetch-based client sends the `Authorization` header, so no token in the URL. API-key callers may use the REST endpoints; the stream is meant for the browser.

### nginx

Add a dedicated block before `location /api/` in `frontend/nginx.conf`:

```nginx
location /api/notifications/stream {
    proxy_pass         http://backend:8080/api/notifications/stream;
    proxy_http_version 1.1;
    proxy_set_header   Connection        "";
    proxy_set_header   Host              $host;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_buffering    off;
    proxy_cache        off;
    proxy_read_timeout 35m;
}
```

The backend also sets `X-Accel-Buffering: no` on the stream response.

### Frontend

- **`NotificationBell`** in the header (`AppLayout`): badge from `useUnreadCount`, dropdown with the latest 10 from `useNotifications`.
- **`NotificationsPage`** at `/notifications`, with filters, pagination and per-item toggles.
- **`useNotificationStream`**: a single app-level hook. It uses `fetch` + `ReadableStream` with a small SSE line parser (no new dependency) and implements the reconnect policy above. On `notification` and `read-state` events it updates the TanStack Query caches for the count and list. On reconnect it invalidates them.
- **`NotificationItem`**: one renderer per type that builds text from `type` + `payload` via i18n keys, picks an icon and urgency style, and links to the target route.
- **Account settings → Notifications** section: per-type toggles (`SUBSCRIPTION_PAST_DUE` shown as locked on).
- **i18n**: new `notifications.json` namespace for `en` and `sr`.

### Constraints & edge cases

- **Job deleted after notifying**: the list still shows it from the payload snapshot. Clicking it lands on the existing "not found" handling.
- **Recipient removed from the project**: their project-scoped notifications are hidden by the read-time visibility filter and pruned by retention.
- **Job reassigned between event and fan-out**: recipients are resolved at fan-out time (after commit), which is effectively immediate.
- **Bulk actions in future**: each job produces its own events (same principle as ADR-0028).
- **Backend restart**: all streams drop. Clients reconnect with backoff and re-fetch, so no data is lost.
- **Multiple backend instances (future)**: the in-memory registry only reaches users connected to the same instance. Moving to Postgres `LISTEN/NOTIFY` or a broker would be a follow-up ADR. Nothing in V1 prevents it.

## Alternatives considered

### WebSocket
Rejected (settled in JOB-246). Traffic is server-to-client only. WebSocket adds a bidirectional protocol, a different proxy setup and STOMP-style tooling for no benefit.

### Polling the unread count
Rejected (settled in JOB-246) in favour of real push. Re-fetching on reconnect still gives polling's robustness without the constant requests.

### Native `EventSource` with the token in the query string
Rejected. Access tokens would end up in nginx logs and browser history. A `fetch`-based reader keeps the normal `Authorization` header.

### Synchronous fan-out inside the triggering transaction
Rejected (settled: async). It would make note creation, status changes and approvals slower and couple their success to notification writes.

### Replay via `Last-Event-ID` and per-connection buffers
Rejected. It needs per-connection queues, exactly the unbounded-memory risk JOB-246's key constraint warns about. Re-fetching on reconnect is simpler and loses nothing because notifications are persisted.

### Store mute settings in `localStorage` (ADR-0023)
Rejected. The server decides who gets a notification, so it must know the mutes. Using the existing `users.preferences` column avoids a migration.

### Generate and store rendered text
Rejected. Text stored in one language wouldn't follow the viewer's language. The frontend renders from type + payload.

### Notify all project members on status changes / blocks
Rejected. Too noisy for projects with many Members, and Members can't see jobs not assigned to them anyway.

### Keep notifications forever
Rejected (decided: read 30 days, unread 90). Notifications are pointers, not the record. The table would otherwise grow without limit.

### Make the notification centre an add-on
Not chosen for V1. Notifications deliver events from features that are already gated. *Open to change in review.*

## Consequences

### Positive
- Users learn about relevant changes without checking, which directly serves the product's "no status calls" goal.
- Admin-critical events (missed runs, past-due) surface proactively.
- Persisted notifications plus re-fetch-on-reconnect make delivery robust without complex streaming state.
- Introduces reusable async event infrastructure (`AsyncConfig`, domain events) for future features.

### Negative
- The first long-lived-connection feature: needs its own nginx config and careful emitter cleanup.
- Owners/Admins get every status change on their projects by design. Muting is the pressure valve; digests may be needed later.
- Best-effort delivery: if the executor queue overflows, some notifications are dropped (logged).
- Single-instance assumption for live push (persisted notifications are unaffected).

### Neutral
- New services publish events from several existing services, so those services gain an `ApplicationEventPublisher` dependency.
- The pricing page doesn't change (core feature).

## Implementation order

1. **DB**: `V041__notifications.sql` (`notifications` table, check constraint, indexes, `job_deadline_alerts`).
2. **Backend 1: notification store and REST API**: `NotificationService` (list, count, mark read/all, visibility filter), `NotificationController`, settings endpoints in `users.preferences`, `NotificationCleanupJob`. Unit + integration tests. *Blocked by DB.*
3. **Backend 2–5: event fan-out** (split into four jobs): `AsyncConfig` + `notificationExecutor`, `NotificationEventListener`, recipient rules, de-duplication, mutes and payload snapshots, then the events per trigger. Integration tests per type (actor exclusion, de-duplication, Member visibility, mutes).
   - **Backend 2** (JOB-276): fan-out core, approval and note events. *Blocked by Backend 1.*
   - **Backend 3** (JOB-277): job lifecycle events (assigned, status changed, blocked, blocker cleared, updated, deleted). *Blocked by Backend 2.*
   - **Backend 4** (JOB-278): `DeadlineAlertJob` (with `job_deadline_alerts`), schedule-created assigned jobs, missed runs. *Blocked by Backend 2 and 3.*
   - **Backend 5** (JOB-279): project member added, invite accepted, feedback resolved, subscription past-due. *Blocked by Backend 2.*
4. **Backend 6: SSE stream** (JOB-280): `NotificationStreamRegistry`, `/stream` endpoint, heartbeat, 30-minute timeout, 5-stream cap, `read-state` events, nginx block. Tests for registry behaviour and the stream endpoint. *Blocked by Backend 2.*
5. **Frontend 1: bell, dropdown and notifications page**: REST-driven (count, list, mark read/all), `NotificationItem` renderers for all 16 types, i18n, empty states, E2E. *Blocked by Backend 1.*
6. **Frontend 2: live updates**: `useNotificationStream` (fetch-based SSE, reconnect/backoff, cache updates, cross-tab read state), E2E. *Blocked by Backend 6 and Frontend 1.*
7. **Frontend 3: notification settings**: Account settings toggles, E2E. *Blocked by Backend 1 and Frontend 1.*

## API Changes Checklist

- [ ] Update Postman collection (`api/postman/OpsClear.postman_collection.json`) with the notification and settings endpoints
- [ ] Add example requests (list unread, mark read, mark all, update mutes)
- [ ] Update environment variables if needed
- [ ] Test the flow manually before marking complete, including the SSE stream through nginx

## References

- ADR-0010: Approval model (request/decide roles)
- ADR-0017: Dashboard (MEMBER visibility rule)
- ADR-0022: Job relationships (`BLOCKED_BY`, used for blocker-cleared)
- ADR-0023: User preferences (`localStorage`, why mutes can't live there)
- ADR-0028: Job status change history (one event per job)
- ADR-0031: Feature gating
- ADR-0034: Recurring jobs (missed runs)
- ADR-0041: Polished empty states
- ADR-0044: Paddle billing (`subscription.past_due`)
- JOB-001 (Future Consideration, PRJ-002): original idea
- JOB-246 (PRJ-012 / MIL-035): this ADR's job and its settled decisions
