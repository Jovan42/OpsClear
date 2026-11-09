// Shared fixtures for the notification specs (ADR-0051: JOB-281, JOB-282). Notifications
// are created through real actions by real users, not inserted into the DB.

import {
  API,
  addMember,
  addProjectMember,
  createJobAs,
  createOrgWithFullAccess,
  createProjectAs,
  tokenFor,
  uniqueEmail,
  uniqueSlug,
  userIdFor,
} from './orgApi';

export interface NotificationFixture {
  ownerEmail: string;
  memberEmail: string;
  projectId: string;
  jobId: string;
}

/** Waits (up to ~10s) until `email` has exactly `expected` unread notifications. */
export function waitForUnreadCount(email: string, expected: number, attemptsLeft = 50): Cypress.Chainable<unknown> {
  return tokenFor(email).then((token) =>
    cy
      .request({
        url: `${API}/api/notifications/unread-count`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then(({ body }: { body: { count: number } }) => {
        if (body.count === expected) return;
        if (attemptsLeft <= 1) {
          throw new Error(`Expected ${expected} unread notification(s) for ${email}, got ${body.count}`);
        }
        // Fan-out is async on the backend; there's no UI or route to wait on instead.
        // eslint-disable-next-line cypress/no-unnecessary-waiting
        cy.wait(200);
        waitForUnreadCount(email, expected, attemptsLeft - 1);
      }),
  );
}

/** Owner + a Member assigned to one job, in a fresh org with every add-on. */
export function setUpOwnerAndMember(label: string): Cypress.Chainable<NotificationFixture> {
  const ownerEmail = uniqueEmail(`notif-${label}-owner`);
  const memberEmail = uniqueEmail(`notif-${label}-member`);
  cy.createKeycloakUser(ownerEmail, 'Olga', 'Owner');
  cy.createKeycloakUser(memberEmail, 'Mia', 'Member');
  return createOrgWithFullAccess(ownerEmail, 'Notify Corp', uniqueSlug()).then((orgId) =>
    createProjectAs(ownerEmail, 'Notify Project').then((projectId) =>
      userIdFor(memberEmail).then((memberId) => {
        addMember(orgId, ownerEmail, memberId, 'MEMBER');
        addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
        return createJobAs(ownerEmail, projectId, { title: 'Replace pump', assignedTo: memberId }).then(
          (jobId) => ({ ownerEmail, memberEmail, projectId, jobId }),
        );
      }),
    ),
  );
}

export function cleanUpUsers(...emails: string[]) {
  emails.forEach((email) => cy.deleteKeycloakUser(email));
}
