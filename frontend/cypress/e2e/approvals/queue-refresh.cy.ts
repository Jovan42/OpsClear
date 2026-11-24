// JOB-417: the pending approval queues (project Approvals page and Overview) refresh when
// someone else acts on a request, without a reload. Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// - A new request arrives as an APPROVAL_REQUESTED notification over the live stream, which
//   refreshes the queues. These tests wait for the bell's `data-live="true"` first.
// - A decision by another admin notifies only the requester, so an admin's open queue
//   learns about it on window focus (or the 60s poll, not exercised here: too slow). The
//   Member is the requester throughout, so the Owner never gets a notification that would
//   refresh the queue for another reason.
// - A 409 "already decided" on decide refreshes the queues, so the stale card leaves.
//
// Withdrawn completion requests take the same focus/poll path as decisions and aren't
// repeated here; the refresh logic itself is unit tested (approvalRefresh.test.ts).

import {
  addMember,
  addProjectMember,
  createJobAs,
  createOrgWithFullAccess,
  createProjectAs,
  decideApprovalAs,
  requestApprovalAs,
  uniqueEmail,
  uniqueSlug,
  userIdFor,
} from '../../support/orgApi';

interface Fixture {
  ownerEmail: string;
  adminEmail: string;
  memberEmail: string;
  projectId: string;
  jobId: string;
}

const bell = () => cy.get('[data-testid="notification-bell"]');

/** Owner, a project Admin and a Member assigned to one job, in a fresh org with every add-on. */
function setUp(label: string): Cypress.Chainable<Fixture> {
  const ownerEmail = uniqueEmail(`queue-${label}-owner`);
  const adminEmail = uniqueEmail(`queue-${label}-admin`);
  const memberEmail = uniqueEmail(`queue-${label}-member`);
  cy.createKeycloakUser(ownerEmail, 'Olga', 'Owner');
  cy.createKeycloakUser(adminEmail, 'Adam', 'Admin');
  cy.createKeycloakUser(memberEmail, 'Mia', 'Member');
  return createOrgWithFullAccess(ownerEmail, 'Queue Corp', uniqueSlug()).then((orgId) =>
    createProjectAs(ownerEmail, 'Queue Project').then((projectId) =>
      userIdFor(adminEmail).then((adminId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, adminId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, adminId, 'ADMIN');
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          return createJobAs(ownerEmail, projectId, { title: 'Replace pump', assignedTo: memberId }).then(
            (jobId) => ({ ownerEmail, adminEmail, memberEmail, projectId, jobId }),
          );
        }),
      ),
    ),
  );
}

function cleanUp({ ownerEmail, adminEmail, memberEmail }: Fixture) {
  [ownerEmail, adminEmail, memberEmail].forEach((email) => cy.deleteKeycloakUser(email));
}

/** What the browser fires when the user comes back to the tab (TanStack Query's focus refetch). */
function returnToTab() {
  cy.window().then((win) => win.dispatchEvent(new Event('visibilitychange')));
}

describe('Approval queue refresh', () => {
  it('a new request shows on the open Approvals page without a reload', { tags: '@smoke' }, () => {
    setUp('new').then((fx) => {
      cy.loginAs(fx.ownerEmail);
      cy.visit(`/projects/${fx.projectId}/approvals`);
      cy.contains('no pending approvals').should('be.visible');
      bell().should('have.attr', 'data-live', 'true');

      requestApprovalAs(fx.memberEmail, fx.projectId, fx.jobId, 'Buy a new pump').its('status').should('eq', 201);

      cy.contains('h2', 'Replace pump').should('be.visible');
      cy.contains('Buy a new pump').should('be.visible');

      cleanUp(fx);
    });
  });

  it('a new request shows on the open Overview without a reload', () => {
    setUp('overview').then((fx) => {
      cy.loginAs(fx.ownerEmail);
      cy.visit('/overview');
      bell().should('have.attr', 'data-live', 'true');
      cy.contains('Hire a crane').should('not.exist');

      requestApprovalAs(fx.memberEmail, fx.projectId, fx.jobId, 'Hire a crane').its('status').should('eq', 201);

      cy.contains('Hire a crane').should('be.visible');

      cleanUp(fx);
    });
  });

  it('a request another admin decided leaves the queue when the user returns to the tab', () => {
    setUp('decided').then((fx) => {
      requestApprovalAs(fx.memberEmail, fx.projectId, fx.jobId, 'Order spare parts').then((res) => {
        const approvalId = res.body.id as string;
        cy.loginAs(fx.ownerEmail);
        cy.visit(`/projects/${fx.projectId}/approvals`);
        cy.contains('Order spare parts').should('be.visible');

        decideApprovalAs(fx.adminEmail, fx.projectId, fx.jobId, approvalId, 'APPROVED').its('status').should('eq', 200);

        returnToTab();
        cy.contains('Order spare parts').should('not.exist');
        cy.contains('no pending approvals').should('be.visible');
      });

      cleanUp(fx);
    });
  });

  it('after a 409 "already decided" the stale card leaves the queue', () => {
    setUp('conflict').then((fx) => {
      requestApprovalAs(fx.memberEmail, fx.projectId, fx.jobId, 'Rent a generator').then((res) => {
        const approvalId = res.body.id as string;
        cy.loginAs(fx.ownerEmail);
        cy.visit(`/projects/${fx.projectId}/approvals`);
        cy.contains('Rent a generator').parent().contains('button', 'Approve').click();
        cy.get('.z-50:visible').should('be.visible');

        decideApprovalAs(fx.adminEmail, fx.projectId, fx.jobId, approvalId, 'REJECTED').its('status').should('eq', 200);

        cy.get('.z-50:visible').within(() => {
          cy.contains('button', 'Approve').click();
          cy.contains('already decided by another user').should('be.visible');
          cy.contains('button', 'Cancel').click();
        });
        cy.contains('Rent a generator').should('not.exist');
        cy.contains('no pending approvals').should('be.visible');
      });

      cleanUp(fx);
    });
  });
});
