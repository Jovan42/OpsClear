// ADR-0051 (JOB-281): notification bell, dropdown and Notifications page. Uses
// cy.loginAs() per docs/dev/process/E2E.md.
//
// Notifications are created through real actions by real users (a Member requests an
// approval or moves a job, the Owner is notified), not inserted into the DB. Fan-out
// runs asynchronously after the action commits, so every test waits for the
// recipient's unread count via the API before visiting the app.
//
// Deliberately left out (per E2E.md's proportional bar): rendering of every one of the
// 16 types (most need system triggers such as the 06:00 deadline job or a Paddle
// webhook), pagination (needs 21+ notifications) and the 99+ badge cap. Those are on the
// PR's manual checklist.

import {
  API,
  addMember,
  addProjectMember,
  createJobAs,
  createOrgWithFullAccess,
  createProjectAs,
  requestApprovalAs,
  tokenFor,
  uniqueEmail,
  uniqueSlug,
  updateJobStatusAs,
  userIdFor,
} from '../../support/orgApi';

interface Fixture {
  ownerEmail: string;
  memberEmail: string;
  projectId: string;
  jobId: string;
}

/** Waits (up to ~10s) until `email` has exactly `expected` unread notifications. */
function waitForUnreadCount(email: string, expected: number, attemptsLeft = 50): Cypress.Chainable<unknown> {
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
function setUpOwnerAndMember(label: string): Cypress.Chainable<Fixture> {
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

function cleanUp(...emails: string[]) {
  emails.forEach((email) => cy.deleteKeycloakUser(email));
}

describe('Notification bell and Notifications page', () => {
  it('shows the Owner a badge after a Member requests approval; clicking the notification opens the job\'s approvals and decrements the badge', { tags: '@smoke' }, () => {
    setUpOwnerAndMember('bell').then(({ ownerEmail, memberEmail, projectId, jobId }) => {
      requestApprovalAs(memberEmail, projectId, jobId, 'Buy a new pump').its('status').should('eq', 201);
      requestApprovalAs(memberEmail, projectId, jobId, 'Hire a crane').its('status').should('eq', 201);
      waitForUnreadCount(ownerEmail, 2);

      cy.loginAs(ownerEmail);
      cy.visit('/projects');
      cy.get('[data-testid="notification-badge"]').should('have.text', '2');

      cy.get('[data-testid="notification-bell"]').click();
      cy.get('[data-testid="notification-dropdown"]').within(() => {
        cy.get('[data-testid="notification-item"]').should('have.length', 2);
        cy.get('[data-testid="notification-item"][data-read="false"]').should('have.length', 2);
        cy.get('[data-testid="notification-item"]')
          .first()
          .should('contain', 'Mia Member')
          .and('contain', `Requested approval on ${jobId} Replace pump`)
          .and('contain', 'Hire a crane')
          .click();
      });

      cy.url().should('include', `/projects/${projectId}/jobs/${jobId}`).and('include', '#approvals');
      cy.get('#approvals').should('contain', 'Hire a crane').and('contain', 'Buy a new pump');
      cy.get('[data-testid="notification-badge"]').should('have.text', '1');
      waitForUnreadCount(ownerEmail, 1);

      cleanUp(ownerEmail, memberEmail);
    });
  });

  it('"Mark all as read" in the dropdown clears the badge', () => {
    setUpOwnerAndMember('mark-all').then(({ ownerEmail, memberEmail, projectId, jobId }) => {
      requestApprovalAs(memberEmail, projectId, jobId, 'Order cables').its('status').should('eq', 201);
      updateJobStatusAs(memberEmail, projectId, jobId, 'IN_PROGRESS').its('status').should('eq', 200);
      waitForUnreadCount(ownerEmail, 2);

      cy.loginAs(ownerEmail);
      cy.visit('/projects');
      cy.get('[data-testid="notification-badge"]').should('have.text', '2');
      cy.get('[data-testid="notification-bell"]').click();
      cy.get('[data-testid="notification-dropdown"]').contains('button', 'Mark all as read').click();

      cy.get('[data-testid="notification-badge"]').should('not.exist');
      cy.get('[data-testid="notification-dropdown"] [data-testid="notification-item"][data-read="false"]').should('not.exist');
      waitForUnreadCount(ownerEmail, 0);

      cleanUp(ownerEmail, memberEmail);
    });
  });

  it('filters the Notifications page by Unread and by type, and toggles read state per item', () => {
    setUpOwnerAndMember('page').then(({ ownerEmail, memberEmail, projectId, jobId }) => {
      requestApprovalAs(memberEmail, projectId, jobId, 'Rent a generator').its('status').should('eq', 201);
      waitForUnreadCount(ownerEmail, 1);
      updateJobStatusAs(memberEmail, projectId, jobId, 'IN_PROGRESS').its('status').should('eq', 200);
      waitForUnreadCount(ownerEmail, 2);

      cy.loginAs(ownerEmail);
      cy.visit('/notifications');
      cy.get('[data-testid="notification-list"] [data-testid="notification-item"]').should('have.length', 2);

      // Mark the approval request read from the page, then the Unread filter hides it.
      cy.get('[data-testid="notification-item"][data-type="APPROVAL_REQUESTED"]')
        .contains('button', 'Mark as read')
        .click();
      cy.get('[data-testid="notification-item"][data-type="APPROVAL_REQUESTED"]').should('have.attr', 'data-read', 'true');

      cy.contains('button', 'Unread').click();
      cy.get('[data-testid="notification-item"]').should('have.length', 1);
      cy.get('[data-testid="notification-item"]')
        .should('have.attr', 'data-type', 'JOB_STATUS_CHANGED')
        .and('contain', `Moved ${jobId} Replace pump from New to In Progress`);

      cy.contains('button', /^All$/).click();
      cy.get('#notification-type-filter').select('Approval requested');
      cy.get('[data-testid="notification-item"]').should('have.length', 1);
      cy.get('[data-testid="notification-item"]').should('have.attr', 'data-type', 'APPROVAL_REQUESTED');

      // And back to unread from the page.
      cy.get('[data-testid="notification-item"]').contains('button', 'Mark as unread').click();
      cy.get('[data-testid="notification-item"]').should('have.attr', 'data-read', 'false');
      waitForUnreadCount(ownerEmail, 2);

      cleanUp(ownerEmail, memberEmail);
    });
  });

  it('shows empty states for a user with no notifications', () => {
    const email = uniqueEmail('notif-empty');
    cy.createKeycloakUser(email, 'Ella', 'Empty');
    createOrgWithFullAccess(email, 'Quiet Corp', uniqueSlug());

    cy.loginAs(email);
    cy.visit('/notifications');
    cy.contains('No notifications yet.').should('be.visible');
    cy.contains('button', 'Unread').click();
    cy.contains('No unread notifications.').should('be.visible');

    cy.get('[data-testid="notification-badge"]').should('not.exist');
    cy.get('[data-testid="notification-bell"]').click();
    cy.get('[data-testid="notification-dropdown"]').should('contain', 'No notifications yet.');

    cleanUp(email);
  });

  it('shows an error with a retry when notifications fail to load', () => {
    const email = uniqueEmail('notif-error');
    cy.createKeycloakUser(email, 'Erin', 'Error');
    createOrgWithFullAccess(email, 'Broken Corp', uniqueSlug());

    cy.intercept({ method: 'GET', pathname: '/api/notifications' }, { statusCode: 500, body: {} }).as('list');
    cy.loginAs(email);
    cy.visit('/notifications');
    cy.contains("Couldn't load notifications.").should('be.visible');

    // "Remove" the stub: a later intercept takes precedence, so this lets the real
    // request through. Retrying then loads the (empty) list.
    cy.intercept({ method: 'GET', pathname: '/api/notifications' }, (req) => req.continue()).as('listOk');
    cy.contains('button', 'Try again').click();
    cy.wait('@listOk').its('response.statusCode').should('eq', 200);
    cy.contains("Couldn't load notifications.").should('not.exist');
    cy.contains('No notifications yet.').should('be.visible');

    cleanUp(email);
  });
});
