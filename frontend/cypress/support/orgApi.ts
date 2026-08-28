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
