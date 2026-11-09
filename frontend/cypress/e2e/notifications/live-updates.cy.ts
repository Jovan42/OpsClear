// ADR-0051 (JOB-282): live notification updates over the SSE stream. Uses cy.loginAs()
// per docs/dev/process/E2E.md.
//
// Each test waits for the bell's `data-live="true"` (the stream is open) before acting,
// then triggers changes through the real API as another user, or as the same user from
// "another tab" (a direct API call). Nothing reloads or navigates afterwards, and the
// window-focus refetch is off while the stream is open, so the badge can only change
// because of a pushed event.
//
// Deliberately left out (long waits, on the PR's manual checklist instead): the full
// 1s → 30s backoff, the 30-minute server close and pausing after 5 minutes hidden. The
// backoff maths, including eviction under the 5-stream cap, is unit tested (backoff.test.ts).

import { API, requestApprovalAs, tokenFor } from '../../support/orgApi';
import { cleanUpUsers, setUpOwnerAndMember, waitForUnreadCount } from '../../support/notifications';

const bell = (options?: Partial<Cypress.Timeoutable>) => cy.get('[data-testid="notification-bell"]', options);
const badge = () => cy.get('[data-testid="notification-badge"]');

/** IDs of `email`'s notifications, newest first. */
function notificationIdsFor(email: string): Cypress.Chainable<string[]> {
  return tokenFor(email).then((token) =>
    cy
      .request({ url: `${API}/api/notifications`, headers: { Authorization: `Bearer ${token}` } })
      .then(({ body }: { body: { items: { id: string }[] } }) => body.items.map((i) => i.id)),
  );
}

function markReadAs(email: string, id: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'PATCH',
      url: `${API}/api/notifications/${id}`,
      headers: { Authorization: `Bearer ${token}` },
      body: { read: true },
    }),
  );
}

function markAllReadAs(email: string) {
  return tokenFor(email).then((token) =>
    cy.request({
      method: 'POST',
      url: `${API}/api/notifications/read-all`,
      headers: { Authorization: `Bearer ${token}` },
    }),
  );
}

describe('Live notification updates', () => {
  it('updates the badge and dropdown without a reload when another user acts', { tags: '@smoke' }, () => {
    setUpOwnerAndMember('live').then(({ ownerEmail, memberEmail, projectId, jobId }) => {
      cy.loginAs(ownerEmail);
      cy.visit('/projects');
      bell().should('have.attr', 'data-live', 'true');
      badge().should('not.exist');

      requestApprovalAs(memberEmail, projectId, jobId, 'Buy a new pump').its('status').should('eq', 201);

      badge().should('have.text', '1');
      bell().click();
      cy.get('[data-testid="notification-dropdown"] [data-testid="notification-item"]')
        .should('have.length', 1)
        .first()
        .should('have.attr', 'data-read', 'false')
        .and('contain', `Requested approval on ${jobId} Replace pump`);

      cleanUpUsers(ownerEmail, memberEmail);
    });
  });

  it('updates the badge live when notifications are marked read in another tab', () => {
    setUpOwnerAndMember('read-state').then(({ ownerEmail, memberEmail, projectId, jobId }) => {
      requestApprovalAs(memberEmail, projectId, jobId, 'Order cables').its('status').should('eq', 201);
      requestApprovalAs(memberEmail, projectId, jobId, 'Hire a crane').its('status').should('eq', 201);
      waitForUnreadCount(ownerEmail, 2);

      cy.loginAs(ownerEmail);
      cy.visit('/projects');
      bell().should('have.attr', 'data-live', 'true');
      badge().should('have.text', '2');

      // "Another tab" marks one read: this tab gets a read-state {ids, read} event.
      notificationIdsFor(ownerEmail).then((ids) => markReadAs(ownerEmail, ids[0]));
      badge().should('have.text', '1');

      // …then marks all read: a read-state {all: true} event.
      markAllReadAs(ownerEmail);
      badge().should('not.exist');

      cleanUpUsers(ownerEmail, memberEmail);
    });
  });

  it('reconnects with backoff after the stream fails and refetches what it missed', () => {
    setUpOwnerAndMember('reconnect').then(({ ownerEmail, memberEmail, projectId, jobId }) => {
      // The first 4 attempts get a 503 (at ~0s, then backoff 1s, 2s and 4s); the 5th comes
      // 8s after the 4th, so ≥ 12s after the first even with -20% jitter. That's ample
      // time to create the notification while disconnected. A 503, not
      // `forceNetworkError`: Chrome silently retries a GET after a network error, so one
      // attempt would use up an unpredictable number of `times`. Limited with `times`
      // rather than replaced by a pass-through handler, because Cypress buffers the
      // response of a handled request, which would hold back the stream.
      cy.intercept(
        { method: 'GET', pathname: '/api/notifications/stream', times: 4 },
        { statusCode: 503, body: '' },
      ).as('streamDown');

      cy.loginAs(ownerEmail);
      cy.visit('/projects');
      cy.wait('@streamDown');
      bell().should('have.attr', 'data-live', 'false');
      badge().should('not.exist');

      // Created while disconnected: the server doesn't replay it, so only the refetch on
      // reconnect can show it (focus refetch isn't triggered and nothing navigates).
      requestApprovalAs(memberEmail, projectId, jobId, 'Rent a generator').its('status').should('eq', 201);
      waitForUnreadCount(ownerEmail, 1);
      // Still disconnected now that it exists, so it can't arrive as a pushed event.
      bell().should('have.attr', 'data-live', 'false');
      badge().should('not.exist');

      bell({ timeout: 40000 }).should('have.attr', 'data-live', 'true');
      badge().should('have.text', '1');
      cy.get('@streamDown.all').should('have.length', 4);

      cleanUpUsers(ownerEmail, memberEmail);
    });
  });
});
