// JOB-209: shared helpers for the Organisation Management specs — every one of them
// needs disposable users, unique org slugs (never cleaned up, so must never repeat
// across runs), and direct API calls to build fixture state faster than driving the
// UI for every setup step.

export const API = 'http://localhost:8080';
export const KEYCLOAK = 'http://localhost:8180';

export function uniqueEmail(label: string) {
  return `e2e-org-${label}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
}

let slugCounter = 0;
export function uniqueSlug(): string {
  const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let n = (Date.now() + slugCounter++) % 17576;
  const c3 = letters[n % 26];
  n = Math.floor(n / 26);
  const c2 = letters[n % 26];
  n = Math.floor(n / 26);
  const c1 = letters[n % 26];
  return `${c1}${c2}${c3}`;
}

export function tokenFor(email: string, password = 'password123') {
  return cy
    .request({
      method: 'POST',
      url: `${KEYCLOAK}/realms/opsclear/protocol/openid-connect/token`,
      form: true,
      body: { client_id: 'opsclear-frontend', grant_type: 'password', username: email, password, scope: 'openid' },
    })
    .then(({ body }: { body: { access_token: string } }) => body.access_token as string);
}

export function userIdFor(email: string) {
  return tokenFor(email).then((token) => {
    // The JWT's `sub` claim is the user's id — decode without a library since it's
    // just base64url JSON in the middle segment.
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    const userId = payload.sub as string;
    // UserSyncFilter only creates the backend `users` row on this user's first
    // authenticated request — a brand-new Keycloak user has no such row yet, so
    // anything referencing them by id (e.g. addMember) 404s "User not found" until
    // they've made one. Any authenticated GET does it; this one's harmless.
    return cy
      .request({ method: 'GET', url: `${API}/api/organisations/mine`, headers: { Authorization: `Bearer ${token}` } })
      .then(() => userId);
  });
}

// Slugs are only 2-3 letters (~18k possible values) and are never cleaned up by these
// specs (soft-deleted orgs' slugs DO become reusable per JOB-238, but these tests
// don't bother deleting), so across a full run of this suite's ~80+ org creations
// the birthday paradox makes an occasional collision on a "unique" generated slug
// a real, observed occurrence — not a bug in uniqueSlug() so much as an inherent
// property of a small, ever-shrinking keyspace. Retrying with a fresh slug on 409
// is the robust fix; regenerating harder is not.
function createOrgRequest(token: string, name: string, slug: string, attemptsLeft = 3): Cypress.Chainable<{ id: string }> {
  return cy
    .request({
      method: 'POST',
      url: `${API}/api/organisations`,
      headers: { Authorization: `Bearer ${token}` },
      body: { name, slug },
      failOnStatusCode: false,
    })
    .then((res) => {
      if (res.status === 201) return res.body as { id: string };
      if (res.status === 409 && attemptsLeft > 1) {
        return createOrgRequest(token, name, uniqueSlug(), attemptsLeft - 1);
      }
      throw new Error(`createOrgRequest failed: ${res.status} ${JSON.stringify(res.body)}`);
    });
}

/** Creates an org for `email` (via the real API) and returns its id. */
export function createOrgAs(email: string, name: string, slug: string) {
  return tokenFor(email).then((token) => createOrgRequest(token, name, slug).then((body) => body.id));
}

/** Creates an org for `email` and immediately gives it a real (non-Paddle)
 *  subscription, so its OWNER can render past SubscriptionWall onto the actual
 *  org-management pages — every settings/members/invites test needs this. */
export function createOrgWithSubscription(email: string, name: string, slug: string) {
  return tokenFor(email).then((token) =>
    createOrgRequest(token, name, slug).then((body) => cy.setUpOrgSubscription(body.id, token).then(() => body.id)),
  );
}

// JOB-210: every add-on-gated feature area (API Keys, Notes, Approvals, Dashboard,
// Milestones, Job Relationships, Job Templates, Recurring Scheduling, ...) is gated
// by real, active Paddle billing on both sides — OrgContext.hasAddon() on the
// frontend and RequiresAddonAspect on the backend. Both explicitly short-circuit to
// "fully unlocked" for `subscription.internal` orgs (see either one's own comments:
// "internal account", billing does not apply) — no live Paddle checkout needed, and
// no per-addon selection needed either, matching ADR-0049's own principle of
// preferring a fixture over a live external dependency for anything that isn't
// itself the subject under test. No API sets this — it's DB-only (queryDb task).
export function makeOrgInternal(orgId: string) {
  return cy.task('queryDb', {
    sql: 'UPDATE org_subscriptions SET is_internal = true WHERE org_id = $1',
    params: [orgId],
  });
}

/** Creates an org for `email` with full (internal, all-add-ons) access — for specs
 *  in an add-on-gated feature area whose actual subject isn't billing itself. */
export function createOrgWithFullAccess(email: string, name: string, slug: string) {
  return createOrgWithSubscription(email, name, slug).then((orgId) =>
    makeOrgInternal(orgId).then(() => orgId),
  );
}

/** Creates a project for `email` (via the real API, no add-on needed — projects are
 *  core) and returns its friendlyId. */
export function createProjectAs(email: string, name: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name },
      })
      .then(({ body }: { body: { friendlyId: string } }) => body.friendlyId),
  );
}

/** Adds `targetUserId` to project `projectId` with a project-level `role` (distinct
 *  from org role), acting as `ownerEmail`. */
export function addProjectMember(
  projectId: string,
  ownerEmail: string,
  targetUserId: string,
  role: 'OWNER' | 'ADMIN' | 'MEMBER',
) {
  return tokenFor(ownerEmail).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/projects/${projectId}/members`,
      headers: { Authorization: `Bearer ${token}` },
      body: { userId: targetUserId, role },
    }),
  );
}

/** Creates a job in `projectId` for `email` and returns its friendlyId. */
export function createJobAs(
  email: string,
  projectId: string,
  body: {
    title: string;
    client?: string;
    assignedTo?: string;
    deadline?: string;
    priority?: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
    milestoneId?: string;
    typeId?: string;
  },
) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/jobs`,
        headers: { Authorization: `Bearer ${token}` },
        body,
      })
      .then(({ body: created }: { body: { friendlyId: string } }) => created.friendlyId),
  );
}

/** Transitions a job's status, acting as `email` — needed to seed IN_PROGRESS/BLOCKED/
 *  COMPLETED jobs directly rather than driving the status-change UI for fixture setup. */
export function updateJobStatusAs(
  email: string,
  projectId: string,
  jobId: string,
  status: 'NEW' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED',
  reason?: string,
) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PATCH',
      url: `${API}/api/projects/${projectId}/jobs/${jobId}/status`,
      headers: { Authorization: `Bearer ${token}` },
      body: { status, ...(reason ? { reason } : {}) },
    }),
  );
}

/** Creates a milestone in `projectId` for `email` (requires the MILESTONES add-on —
 *  callers should use an org created via createOrgWithFullAccess) and returns its id. */
export function createMilestoneAs(email: string, projectId: string, name: string, deadline?: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/milestones`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name, ...(deadline ? { deadline } : {}) },
      })
      .then(({ body }: { body: { id: string } }) => body.id),
  );
}

/** Creates a job type in `projectId` for `email` (requires the JOB_TYPES add-on —
 *  callers should use an org created via createOrgWithFullAccess) and returns its id. */
export function createJobTypeAs(email: string, projectId: string, name: string, color: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/job-types`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name, color },
      })
      .then(({ body }: { body: { id: string } }) => body.id),
  );
}

/** Marks `projectId` COMPLETED, acting as `email`. */
export function completeProjectAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PATCH',
      url: `${API}/api/projects/${projectId}/status`,
      headers: { Authorization: `Bearer ${token}` },
      body: { status: 'COMPLETED' },
    }),
  );
}

/** Adds `targetUserId` to `orgId` with `role`, acting as the org's owner. */
export function addMember(orgId: string, ownerEmail: string, targetUserId: string, role: 'MEMBER' | 'ADMIN') {
  return tokenFor(ownerEmail).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/organisations/${orgId}/members`,
      headers: { Authorization: `Bearer ${token}` },
      body: { userId: targetUserId, role },
    }),
  );
}

/** Sends an invite for `email` from `orgId`, acting as its owner, and returns the
 *  new invite's id. */
export function sendInvite(orgId: string, ownerEmail: string, inviteeEmail: string) {
  return tokenFor(ownerEmail).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/organisations/${orgId}/invites`,
        headers: { Authorization: `Bearer ${token}` },
        body: { email: inviteeEmail },
      })
      .then(({ body }: { body: { id: string } }) => body.id),
  );
}

/** No API exposes an invite's raw token (only returned nowhere at all, not even at
 *  creation, per POST .../invites' own response shape) — reads it straight from
 *  organisation_invites via the queryDb task (cypress.config.ts). */
export function inviteTokenFor(inviteId: string) {
  return cy
    .task('queryDb', { sql: 'SELECT token FROM organisation_invites WHERE id = $1', params: [inviteId] })
    .then((rows) => (rows as Array<{ token: string }>)[0].token);
}

/** Backdates an invite's expires_at so it reads as already-expired — the only way to
 *  exercise the 7-day expiry window without a real 7-day wait. */
export function expireInvite(inviteId: string) {
  return cy.task('queryDb', {
    sql: "UPDATE organisation_invites SET expires_at = NOW() - INTERVAL '1 day' WHERE id = $1",
    params: [inviteId],
  });
}

/** Creates a project-scoped job template in `projectId` for `email` (requires the
 *  JOB_TEMPLATES add-on — callers should use an org created via
 *  createOrgWithFullAccess) and returns its id. */
export function createTemplateAs(
  email: string,
  projectId: string,
  body: {
    name: string;
    title?: string;
    description?: string;
    client?: string;
    priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    assigneeMode?: 'NONE' | 'FIXED' | 'ASK';
    assigneeId?: string;
    milestoneId?: string;
    defaultTypeId?: string;
    defaultTypeName?: string;
    deadlineOffsetDays?: number;
  },
) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/templates`,
        headers: { Authorization: `Bearer ${token}` },
        body,
      })
      .then(({ body: created }: { body: { id: string } }) => created.id),
  );
}

export interface TemplateListEntry {
  id: string;
  name: string;
  scope: 'PROJECT' | 'ORG';
  occurrenceCount: number;
  defaultTypeId: string | null;
  defaultTypeName: string | null;
  milestoneId: string | null;
}

/** Lists project-scoped job templates for `email` in `projectId` (combined with any
 *  visible org-scoped templates, per the real endpoint's own behavior). */
export function listTemplatesAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/templates`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as TemplateListEntry[]),
  );
}

/** Lists org-scoped job templates for `email` in `orgId`. */
export function listOrgTemplatesAs(email: string, orgId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/organisations/${orgId}/templates`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as TemplateListEntry[]),
  );
}

/** Creates an org-scoped job template for `email` in `orgId` and returns its id. */
export function createOrgTemplateAs(
  email: string,
  orgId: string,
  body: {
    name: string;
    title?: string;
    description?: string;
    client?: string;
    priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    assigneeMode?: 'NONE' | 'FIXED' | 'ASK';
    defaultTypeName?: string;
    deadlineOffsetDays?: number;
  },
) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/organisations/${orgId}/templates`,
        headers: { Authorization: `Bearer ${token}` },
        body,
      })
      .then(({ body: created }: { body: { id: string } }) => created.id),
  );
}

/** Records usage of a template (project- or org-scoped, resolved via the project path),
 *  acting as `email`. `failOnStatusCode: false` since this also exercises 403 cases. */
export function recordTemplateUsageAs(email: string, projectId: string, templateId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/projects/${projectId}/templates/${templateId}/use`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Deletes a project-scoped template, acting as `email`. `failOnStatusCode: false`
 *  since this also exercises 403/404/409 validation cases. */
export function deleteTemplateAs(email: string, projectId: string, templateId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'DELETE',
      url: `${API}/api/projects/${projectId}/templates/${templateId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Lists active (non-deleted) block reasons for `projectId`, acting as `email`. */
export function listBlockReasonsAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/block-reasons`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as Array<{ id: string; reason: string }>),
  );
}

/** Deletes a block reason, acting as `email`. `failOnStatusCode: false` since this is
 *  also used to exercise the 403/404 validation cases. */
export function deleteBlockReasonAs(email: string, projectId: string, reasonId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'DELETE',
      url: `${API}/api/projects/${projectId}/block-reasons/${reasonId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Adds a note to `jobId` in `projectId`, acting as `email`, and returns its id. */
export function createNoteAs(email: string, projectId: string, jobId: string, content: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/jobs/${jobId}/notes`,
        headers: { Authorization: `Bearer ${token}` },
        body: { content },
      })
      .then(({ body }: { body: { id: string } }) => body.id),
  );
}

/** Fetches a single job's full response (incl. relationships), acting as `email`. */
export function getJobAs(email: string, projectId: string, jobId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/jobs/${jobId}`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body),
  );
}

/** Creates a relationship from `jobId` to `targetJobId`, acting as `email`, and
 *  returns its id. `targetJobId` may be a friendlyId or a raw UUID — the request
 *  body field itself only accepts a raw UUID (no friendlyId resolution server-side,
 *  unlike path segments elsewhere), so a friendlyId is resolved via a GET first. */
export function createRelationshipAs(
  email: string,
  projectId: string,
  jobId: string,
  targetJobId: string,
  type: 'BLOCKED_BY' | 'RELATED_TO' | 'DUPLICATES',
) {
  const isFriendlyId = /^[A-Za-z]{2,6}-\d+$/.test(targetJobId);
  return tokenFor(email).then((token) =>
    (isFriendlyId ? getJobAs(email, projectId, targetJobId).then((j: { id: string }) => j.id) : cy.wrap(targetJobId)).then(
      (resolvedTargetId) =>
        cy
          .request({
            method: 'POST',
            url: `${API}/api/projects/${projectId}/jobs/${jobId}/relationships`,
            headers: { Authorization: `Bearer ${token}` },
            body: { targetJobId: resolvedTargetId, type },
          })
          .then(({ body }: { body: { id: string } }) => body.id),
    ),
  );
}

/** Deletes a relationship from `jobId`'s perspective, acting as `email`.
 *  `failOnStatusCode: false` since this also exercises 403/404 validation cases. */
export function deleteRelationshipAs(email: string, projectId: string, jobId: string, relationshipId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'DELETE',
      url: `${API}/api/projects/${projectId}/jobs/${jobId}/relationships/${relationshipId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Fetches a job's status history (oldest-first), acting as `email`. `failOnStatusCode: false`
 *  since this is also used to exercise the 403/404 validation cases. */
export function getJobHistoryAs(email: string, projectId: string, jobId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'GET',
      url: `${API}/api/projects/${projectId}/jobs/${jobId}/history`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Lists a project's members (id, userId, role), acting as `email`. */
export function listProjectMembersAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/members`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as Array<{ id: string; userId: string; role: string }>),
  );
}

/** Creates a recurring schedule (requires the RECURRING_SCHEDULING add-on and a
 *  templateId — see createTemplateAs), acting as `email`, and returns its id. */
export function createScheduleAs(
  email: string,
  projectId: string,
  body: {
    name: string;
    templateId: string;
    cronExpression: string;
    timezone: string;
    assigneeIds?: string[];
    pausedUntil?: string | null;
    expiresAt?: string | null;
  },
) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/schedules`,
        headers: { Authorization: `Bearer ${token}` },
        body,
      })
      .then(({ body: created }: { body: { id: string } }) => created.id),
  );
}

/** A recurring schedule as returned by the API — the fields this suite's tests read. */
export interface ScheduleResponse {
  id: string;
  templateId: string;
  templateName: string | null;
  name: string;
  cronExpression: string;
  timezone: string;
  pausedUntil: string | null;
  expiresAt: string | null;
  nextRunAt: string;
  lastRunAt: string | null;
  currentRotationIndex: number;
  assignees: Array<{ userId: string; userName: string; order: number }>;
  status: 'ACTIVE' | 'PAUSED' | 'PAUSED_NO_ASSIGNEES' | 'EXPIRED';
}

/** Fetches a single recurring schedule, acting as `email`. */
export function getScheduleAs(email: string, projectId: string, scheduleId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/schedules/${scheduleId}`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as ScheduleResponse),
  );
}

/** Lists a project's recurring schedules, acting as `email`. */
export function listSchedulesAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/schedules`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as ScheduleResponse[]),
  );
}

/** Updates (full-replace PUT) a recurring schedule, acting as `email`.
 *  `failOnStatusCode: false` since this also exercises validation/permission cases. */
export function updateScheduleAs(
  email: string,
  projectId: string,
  scheduleId: string,
  body: {
    name: string;
    templateId: string;
    cronExpression: string;
    timezone: string;
    pausedUntil?: string | null;
    expiresAt?: string | null;
    assigneeIds: string[];
  },
) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PUT',
      url: `${API}/api/projects/${projectId}/schedules/${scheduleId}`,
      headers: { Authorization: `Bearer ${token}` },
      body,
      failOnStatusCode: false,
    }),
  );
}

/** Deletes a recurring schedule, acting as `email`.
 *  `failOnStatusCode: false` since this also exercises permission/404 cases. */
export function deleteScheduleAs(email: string, projectId: string, scheduleId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'DELETE',
      url: `${API}/api/projects/${projectId}/schedules/${scheduleId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Pauses a recurring schedule, acting as `email`. `until` omitted/null pauses indefinitely.
 *  `failOnStatusCode: false` since this also exercises permission cases. */
export function pauseScheduleAs(email: string, projectId: string, scheduleId: string, until?: string | null) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/projects/${projectId}/schedules/${scheduleId}/pause`,
      headers: { Authorization: `Bearer ${token}` },
      body: { until: until ?? null },
      failOnStatusCode: false,
    }),
  );
}

/** Resumes a recurring schedule, acting as `email`.
 *  `failOnStatusCode: false` since this also exercises permission cases. */
export function resumeScheduleAs(email: string, projectId: string, scheduleId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/projects/${projectId}/schedules/${scheduleId}/resume`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Lists a schedule's missed runs, acting as `email`. */
export function listMissedRunsAs(email: string, projectId: string, scheduleId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/schedules/${scheduleId}/missed-runs`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as Array<{ id: string; expectedAt: string }>),
  );
}

/** Inserts a `schedule_missed_runs` row directly via the DB task — the only way to produce
 *  one without waiting for the real 60s poller to detect actual downtime. */
export function insertMissedRunAs(scheduleId: string, expectedAt: string) {
  return cy
    .task('queryDb', {
      sql: 'INSERT INTO schedule_missed_runs (id, schedule_id, expected_at) VALUES (gen_random_uuid(), $1, $2) RETURNING id',
      params: [scheduleId, expectedAt],
    })
    .then((rows) => (rows as Array<{ id: string }>)[0].id);
}

/** Materializes a missed run into a real job, acting as `email`.
 *  `failOnStatusCode: false` since this also exercises permission/404 cases. */
export function materializeMissedRunAs(email: string, projectId: string, scheduleId: string, missedRunId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/projects/${projectId}/schedules/${scheduleId}/missed-runs/${missedRunId}/materialize`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Dismisses a single missed run (no job created), acting as `email`.
 *  `failOnStatusCode: false` since this also exercises permission/404 cases. */
export function dismissMissedRunAs(email: string, projectId: string, scheduleId: string, missedRunId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'DELETE',
      url: `${API}/api/projects/${projectId}/schedules/${scheduleId}/missed-runs/${missedRunId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Dismisses every missed run for a schedule in one call, acting as `email`.
 *  `failOnStatusCode: false` since this also exercises permission cases. */
export function dismissAllMissedRunsAs(email: string, projectId: string, scheduleId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'DELETE',
      url: `${API}/api/projects/${projectId}/schedules/${scheduleId}/missed-runs`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Calls the stateless cron-preview endpoint (auth required, no project scope), acting as `email`.
 *  `failOnStatusCode: false` since this also exercises validation cases. */
export function previewCronAs(email: string, cronExpression: string, timezone: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/schedules/preview`,
      headers: { Authorization: `Bearer ${token}` },
      body: { cronExpression, timezone },
      failOnStatusCode: false,
    }),
  );
}

/** Fetches a single project's full response (incl. links), acting as `email`. */
export function getProjectAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body),
  );
}

/** Adds a link to `jobId` in `projectId`, acting as `email`, and returns its id.
 *  `failOnStatusCode: false` since this is also used to exercise validation cases. */
export function createJobLinkAs(email: string, projectId: string, jobId: string, url: string, label?: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/jobs/${jobId}/links`,
        headers: { Authorization: `Bearer ${token}` },
        body: { url, label },
        failOnStatusCode: false,
      })
      .then((res) => res),
  );
}

/** Adds a link to `projectId` directly, acting as `email`, and returns its id.
 *  `failOnStatusCode: false` since this is also used to exercise validation cases. */
export function createProjectLinkAs(email: string, projectId: string, url: string, label?: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/projects/${projectId}/links`,
        headers: { Authorization: `Bearer ${token}` },
        body: { url, label },
        failOnStatusCode: false,
      })
      .then((res) => res),
  );
}

/** Lists job types for `projectId`, acting as `email`. */
export function listJobTypesAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/job-types`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as Array<{ id: string; name: string; color: string; displayOrder: number }>),
  );
}

/** Updates a job type (full replace — name/color/displayOrder all required), acting
 *  as `email`. `failOnStatusCode: false` since this also exercises validation cases. */
export function updateJobTypeAs(
  email: string,
  projectId: string,
  typeId: string,
  body: { name: string; color: string; displayOrder: number },
) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PUT',
      url: `${API}/api/projects/${projectId}/job-types/${typeId}`,
      headers: { Authorization: `Bearer ${token}` },
      body,
      failOnStatusCode: false,
    }),
  );
}

/** Deletes a job type, acting as `email`. `failOnStatusCode: false` since this is
 *  also used to exercise the 409/403/404 validation cases. */
export function deleteJobTypeAs(email: string, projectId: string, typeId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'DELETE',
      url: `${API}/api/projects/${projectId}/job-types/${typeId}`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Requests approval on `jobId` in `projectId`, acting as `email`. `failOnStatusCode:
 *  false` since this is also used to exercise validation/permission cases. */
export function requestApprovalAs(email: string, projectId: string, jobId: string, description: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/projects/${projectId}/jobs/${jobId}/approvals`,
      headers: { Authorization: `Bearer ${token}` },
      body: { description },
      failOnStatusCode: false,
    }),
  );
}

/** Approves or rejects `approvalId` on `jobId`, acting as `email`. `failOnStatusCode:
 *  false` since this is also used to exercise the concurrent-decision-race and
 *  permission/validation cases. */
export function decideApprovalAs(
  email: string,
  projectId: string,
  jobId: string,
  approvalId: string,
  status: 'APPROVED' | 'REJECTED',
  comment?: string,
) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PATCH',
      url: `${API}/api/projects/${projectId}/jobs/${jobId}/approvals/${approvalId}/status`,
      headers: { Authorization: `Bearer ${token}` },
      body: { status, ...(comment ? { comment } : {}) },
      failOnStatusCode: false,
    }),
  );
}

/** Lists all approvals (any status) on `jobId`, acting as `email`. */
export function listApprovalsByJobAs(email: string, projectId: string, jobId: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/projects/${projectId}/jobs/${jobId}/approvals`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as Array<{
        id: string; jobId: string; requesterId: string; approverId: string | null;
        description: string; status: 'PENDING' | 'APPROVED' | 'REJECTED';
        comment: string | null; requestedAt: string; decidedAt: string | null;
      }>),
  );
}

/** Lists all pending approvals across `projectId` (OWNER/ADMIN only), acting as
 *  `email`. `failOnStatusCode: false` since this is also used to exercise the
 *  MEMBER-403 permission case. */
export function listPendingApprovalsAs(email: string, projectId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'GET',
      url: `${API}/api/projects/${projectId}/approvals/pending`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Creates an org for `email` with a real (non-internal) subscription that has
 *  `addonKey` granted, then marks the subscription PAST_DUE via a direct DB write.
 *  `RequiresAddonAspect`'s PAST_DUE write-block is skipped entirely for internal
 *  orgs (see `makeOrgInternal`'s own comment), so `createOrgWithFullAccess` can't be
 *  used to test it — this grants exactly one real addon instead, non-internal.
 *  `OrgSubscriptionRepository.hasRealBilling()` additionally requires a non-null
 *  `paddle_subscription_id`, which the non-Paddle `PUT .../subscription` endpoint
 *  (used by `setUpOrgSubscription`/`createOrgWithSubscription`) never sets — every
 *  prior addon-off test happened to pass regardless, since a missing addon and
 *  missing real billing both 403 with the same message, but PAST_DUE specifically
 *  needs `hasRealBilling()` to actually be true. Backfills a fake, non-null
 *  `paddle_subscription_id` via the same direct DB write for exactly that reason. */
export function createOrgWithAddonPastDue(email: string, name: string, slug: string, addonKey: string) {
  return tokenFor(email).then((token) =>
    createOrgRequest(token, name, slug).then((body) => {
      const orgId = body.id;
      return cy
        .request({
          method: 'GET',
          url: `${API}/api/subscriptions/catalog`,
          headers: { Authorization: `Bearer ${token}` },
        })
        .then(({ body: catalog }: { body: { tiers: Array<{ id: string }>; addons: Array<{ id: string; key: string }> } }) => {
          const addon = catalog.addons.find((a) => a.key === addonKey)!;
          return cy
            .request({
              method: 'PUT',
              url: `${API}/api/organisations/${orgId}/subscription`,
              headers: { Authorization: `Bearer ${token}` },
              body: { tierId: catalog.tiers[0].id, billingCycle: 'MONTHLY', addonIds: [addon.id] },
            })
            .then(() =>
              cy
                .task('queryDb', {
                  sql: "UPDATE org_subscriptions SET subscription_status = 'PAST_DUE', paddle_subscription_id = $2 WHERE org_id = $1",
                  params: [orgId, `sub_e2e_pastdue_${orgId}`],
                })
                .then(() => orgId as string),
            );
        });
    }),
  );
}

/** Fetches the public subscription catalog (tiers + add-ons) — no auth needed, but
 *  a token is still required to reach `tokenFor`'s Keycloak round-trip for parity
 *  with every other helper here. */
export function getCatalogAs(email: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'GET',
        url: `${API}/api/subscriptions/catalog`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }) => body as {
        tiers: Array<{ id: string; maxMembers: number; maxProjects: number | null; priceMonthly: number; priceAnnual: number; currency: string; paddlePriceIdMonthly: string | null; paddlePriceIdAnnual: string | null }>;
        addons: Array<{ id: string; key: string; name: string; priceMonthly: number; priceAnnual: number; available: boolean; paddlePriceIdMonthly: string | null; paddlePriceIdAnnual: string | null }>;
      }),
  );
}

/** Fetches `orgId`'s current subscription record, acting as `email`.
 *  `failOnStatusCode: false` since this is also used to exercise the 404-when-none-
 *  staged-yet case. */
export function getOrgSubscriptionAs(email: string, orgId: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'GET',
      url: `${API}/api/organisations/${orgId}/subscription`,
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    }),
  );
}

/** Stages a tier + specific add-on selection via the free `PUT .../subscription`
 *  upsert, acting as `email` — no Paddle involved, so `paddle_subscription_id`/
 *  `subscription_status` stay null and `hasRealBilling()` stays false. This is
 *  exactly the "staged-but-unpaid" state JOB-200 closed the gap on (a selection
 *  alone was never supposed to grant access) — `failOnStatusCode: false` since this
 *  is also used to exercise the owner/tier/billingCycle/downgrade validation cases. */
export function upsertSubscriptionAs(
  email: string,
  orgId: string,
  body: { tierId: string; billingCycle: string; addonIds?: string[] },
) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PUT',
      url: `${API}/api/organisations/${orgId}/subscription`,
      headers: { Authorization: `Bearer ${token}` },
      body,
      failOnStatusCode: false,
    }),
  );
}

/** Creates an org for `email` with `addonKey` STAGED (selected via the free upsert)
 *  but never actually paid for — `paddle_subscription_id`/`subscription_status` stay
 *  null, so `hasRealBilling()` is false despite the addon showing as "selected".
 *  This is the exact scenario the JOB-200 gap-closure guards against: a direct API
 *  call bypassing the UI should still 403 on the gated endpoint. Returns the org id
 *  and the staged addon's own id (callers need both). */
export function createOrgWithStagedAddon(email: string, name: string, slug: string, addonKey: string) {
  return tokenFor(email).then((token) =>
    createOrgRequest(token, name, slug).then((body) => {
      const orgId = body.id;
      return getCatalogAs(email).then((catalog) => {
        const addon = catalog.addons.find((a) => a.key === addonKey)!;
        return cy
          .request({
            method: 'PUT',
            url: `${API}/api/organisations/${orgId}/subscription`,
            headers: { Authorization: `Bearer ${token}` },
            body: { tierId: catalog.tiers[0].id, billingCycle: 'MONTHLY', addonIds: [addon.id] },
          })
          .then(() => ({ orgId: orgId as string, addonId: addon.id }));
      });
    }),
  );
}

/** Creates an org for `email` with a real, ACTIVE Paddle subscription (a fake but
 *  realistic `paddle_subscription_id` and `paddle_customer_id`, DB-seeded directly —
 *  same reasoning as `createOrgWithAddonPastDue`: `hasRealBilling()` requires a
 *  non-null `paddle_subscription_id`, which the free `PUT .../subscription` endpoint
 *  never sets). `tierIndex` (default 0, the cheapest catalog tier) selects which tier
 *  to start on — callers that need to downgrade off of it (e.g. a mixed-change or
 *  downgrade test that must move a slider *down*) should pass a higher index so
 *  there's room to move. Returns `{ orgId, paddleSubscriptionId, paddleCustomerId }` —
 *  both ids are useful to callers that need to identify this exact subscription
 *  (e.g. by matching it in a signed Paddle webhook body, once JOB-266 unblocks
 *  webhook simulation in this environment). */
export function createOrgWithActivePaddleSubscription(email: string, name: string, slug: string, tierIndex = 0) {
  return tokenFor(email).then((token) =>
    createOrgRequest(token, name, slug).then((body) => {
      const orgId = body.id;
      return cy
        .request({
          method: 'GET',
          url: `${API}/api/subscriptions/catalog`,
          headers: { Authorization: `Bearer ${token}` },
        })
        .then(({ body: catalog }: { body: { tiers: Array<{ id: string }> } }) =>
          cy
            .request({
              method: 'PUT',
              url: `${API}/api/organisations/${orgId}/subscription`,
              headers: { Authorization: `Bearer ${token}` },
              body: { tierId: catalog.tiers[tierIndex].id, billingCycle: 'MONTHLY', addonIds: [] },
            })
            .then(() => {
              const paddleSubscriptionId = `sub_e2e_active_${orgId}`;
              const paddleCustomerId = `ctm_e2e_${orgId}`;
              return cy
                .task('queryDb', {
                  sql: "UPDATE org_subscriptions SET subscription_status = 'ACTIVE', paddle_subscription_id = $2 WHERE org_id = $1",
                  params: [orgId, paddleSubscriptionId],
                })
                .then(() =>
                  cy.task('queryDb', {
                    sql: 'UPDATE organisations SET paddle_customer_id = $2 WHERE id = $1',
                    params: [orgId, paddleCustomerId],
                  }),
                )
                .then(() => ({ orgId: orgId as string, paddleSubscriptionId, paddleCustomerId }));
            }),
        );
    }),
  );
}
