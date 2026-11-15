// JOB-289 (ADR-0052): the completion request flow on the job detail page. On a project with
// "Require approval to complete jobs" on, a Member's Complete creates a completion approval
// (the job stays In Progress) that an Owner/Admin approves (auto-completes), rejects, or that
// the requester withdraws. Uses cy.loginAs() per docs/dev/process/E2E.md. The gate is turned
// on through the settings API in setup (the toggle UI is covered by
// projects/completion-approval-setting.cy.ts); it needs the APPROVALS add-on, hence
// createOrgWithFullAccess.

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithFullAccess,
  createProjectAs,
  createJobAs,
  addMember,
  addProjectMember,
  getJobAs,
  updateJobStatusAs,
  updateProjectSettingsAs,
} from '../../support/orgApi';

const banner = () => cy.get('[data-testid="completion-request-banner"]');
// The shared Modal (aria-modal), not the notification bell's non-modal popover dialog
const modal = () => cy.get('[role="dialog"][aria-modal="true"]');

/**
 * Owner + Member in an org with every add-on, a gated project, and an In Progress job assigned
 * to the Member. Yields the project and job friendly ids.
 */
function setUpGatedJob(ownerEmail: string, memberEmail: string, label: string) {
  cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
  cy.createKeycloakUser(memberEmail, 'E2E', 'Member');
  return createOrgWithFullAccess(ownerEmail, `${label} Corp`, uniqueSlug()).then((orgId) =>
    createProjectAs(ownerEmail, `${label} Project`).then((projectId) =>
      userIdFor(memberEmail).then((memberId) => {
        addMember(orgId, ownerEmail, memberId, 'MEMBER');
        addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
        updateProjectSettingsAs(ownerEmail, projectId, { requireCompletionApproval: true })
          .its('status').should('eq', 200);
        return createJobAs(ownerEmail, projectId, { title: `${label} job`, assignedTo: memberId }).then((jobId) => {
          updateJobStatusAs(memberEmail, projectId, jobId, 'IN_PROGRESS');
          return cy.wrap({ projectId, jobId });
        });
      }),
    ),
  );
}

/** The Member asks for completion through the API (202, job stays In Progress). */
function requestCompletionAs(email: string, projectId: string, jobId: string) {
  return updateJobStatusAs(email, projectId, jobId, 'COMPLETED').its('status').should('eq', 202);
}

describe('Completion approval on the job detail page', () => {
  it('a Member requests completion and the job stays In Progress; an Owner approves and it is Completed', { tags: '@smoke' }, () => {
    const ownerEmail = uniqueEmail('completion-owner');
    const memberEmail = uniqueEmail('completion-member');
    setUpGatedJob(ownerEmail, memberEmail, 'Harbor').then(({ projectId, jobId }) => {
      cy.loginAs(memberEmail);
      cy.visit(`/projects/${projectId}/jobs/${jobId}`);
      cy.contains('button', 'Complete').should('not.exist');
      cy.contains('button', 'Request completion').click();
      modal().within(() => {
        cy.contains('needs an Owner or Admin to approve').should('be.visible');
        cy.get('textarea').type('All checks done');
        cy.contains('button', 'Request completion').click();
      });

      banner().should('contain.text', 'Completion requested by E2E Member').and('contain.text', '"All checks done"');
      // The Member can't decide their own request, only withdraw it
      banner().contains('button', 'Approve').should('not.exist');
      banner().contains('button', 'Withdraw').should('be.visible');
      cy.contains('button', 'Approval requested').should('be.disabled');
      getJobAs(memberEmail, projectId, jobId).its('status').should('eq', 'IN_PROGRESS');

      cy.loginAs(ownerEmail);
      cy.visit(`/projects/${projectId}/jobs/${jobId}`);
      banner().contains('button', 'Approve').click();
      modal().within(() => cy.contains('button', 'Approve').click());

      banner().should('not.exist');
      cy.contains('button', 'Reopen').should('be.visible');
      getJobAs(ownerEmail, projectId, jobId).then((job) => {
        expect(job.status).to.equal('COMPLETED');
        expect(job.pendingCompletionApproval).to.equal(null);
      });
    });
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('an Owner rejects a completion request: the job stays In Progress and the banner is gone', () => {
    const ownerEmail = uniqueEmail('completion-owner');
    const memberEmail = uniqueEmail('completion-member');
    setUpGatedJob(ownerEmail, memberEmail, 'Cedar').then(({ projectId, jobId }) => {
      requestCompletionAs(memberEmail, projectId, jobId);

      cy.loginAs(ownerEmail);
      cy.visit(`/projects/${projectId}/jobs/${jobId}`);
      banner().contains('button', 'Reject').click();
      modal().within(() => {
        cy.get('textarea').type('Needs another review');
        cy.contains('button', 'Reject').click();
      });

      banner().should('not.exist');
      // Owners complete directly, so the normal Complete button is back
      cy.contains('button', 'Complete').should('be.visible');
      getJobAs(ownerEmail, projectId, jobId).then((job) => {
        expect(job.status).to.equal('IN_PROGRESS');
        expect(job.pendingCompletionApproval).to.equal(null);
      });
    });
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a Member withdraws their completion request and can request again', () => {
    const ownerEmail = uniqueEmail('completion-owner');
    const memberEmail = uniqueEmail('completion-member');
    setUpGatedJob(ownerEmail, memberEmail, 'Birch').then(({ projectId, jobId }) => {
      requestCompletionAs(memberEmail, projectId, jobId);

      cy.loginAs(memberEmail);
      cy.visit(`/projects/${projectId}/jobs/${jobId}`);
      banner().contains('button', 'Withdraw').click();
      modal().within(() => cy.contains('button', 'Withdraw').click());

      banner().should('not.exist');
      getJobAs(memberEmail, projectId, jobId).its('status').should('eq', 'IN_PROGRESS');

      cy.contains('button', 'Request completion').click();
      modal().within(() => cy.contains('button', 'Request completion').click());
      banner().should('contain.text', 'Completion requested by E2E Member');
      cy.contains('button', 'Approval requested').should('be.disabled');
    });
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('approving after the job was blocked records the approval but leaves the job Blocked, with a toast', () => {
    const ownerEmail = uniqueEmail('completion-owner');
    const memberEmail = uniqueEmail('completion-member');
    setUpGatedJob(ownerEmail, memberEmail, 'Alder').then(({ projectId, jobId }) => {
      requestCompletionAs(memberEmail, projectId, jobId);
      updateJobStatusAs(memberEmail, projectId, jobId, 'BLOCKED', 'Waiting on supplier');

      cy.loginAs(ownerEmail);
      cy.visit(`/projects/${projectId}/jobs/${jobId}`);
      cy.contains('Waiting on supplier').should('be.visible');
      banner().contains('button', 'Approve').click();
      modal().within(() => cy.contains('button', 'Approve').click());

      cy.contains("Approved, but the job wasn't completed because it is Blocked.").should('be.visible');
      banner().should('not.exist');
      cy.contains('button', 'Unblock').should('be.visible');
      getJobAs(ownerEmail, projectId, jobId).its('status').should('eq', 'BLOCKED');
    });
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});
