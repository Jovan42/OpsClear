// JOB-290 (ADR-0052): completion approvals in lists. The job list shows an "Awaiting completion
// approval" badge, approval lists label completion approvals "Completion", and the job's
// approvals section shows the cancelled and approved-not-applied states. Uses cy.loginAs() per
// docs/dev/process/E2E.md. Setup goes through the API: the gate is turned on with the settings
// endpoint (it needs the APPROVALS add-on, hence createOrgWithFullAccess) and the Member asks for
// completion with a COMPLETED status change (202). The request/decide/withdraw UI itself is
// covered by completion-approval.cy.ts.

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithFullAccess,
  createProjectAs,
  createJobAs,
  addMember,
  addProjectMember,
  updateJobStatusAs,
  updateProjectSettingsAs,
  requestApprovalAs,
  decideApprovalAs,
  listApprovalsByJobAs,
} from '../../support/orgApi';

const modal = () => cy.get('[role="dialog"][aria-modal="true"]');

/**
 * Owner + Member in an org with every add-on, a gated project, and an In Progress job assigned
 * to the Member, who has asked for completion. Yields the project and job friendly ids.
 */
function setUpCompletionRequest(ownerEmail: string, memberEmail: string, label: string) {
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
          updateJobStatusAs(memberEmail, projectId, jobId, 'COMPLETED').its('status').should('eq', 202);
          return cy.wrap({ projectId, jobId });
        });
      }),
    ),
  );
}

/** Opens the job's approvals section (the `#approvals` anchor expands it). */
function visitJobApprovals(projectId: string, jobId: string) {
  cy.visit(`/projects/${projectId}/jobs/${jobId}#approvals`);
  return cy.get('#approvals');
}

describe('Completion approvals in lists', () => {
  it('shows the awaiting badge in the job list and labels the request "Completion" in the queue', { tags: '@smoke' }, () => {
    const ownerEmail = uniqueEmail('completion-lists-owner');
    const memberEmail = uniqueEmail('completion-lists-member');
    setUpCompletionRequest(ownerEmail, memberEmail, 'Maple').then(({ projectId, jobId }) => {
      // A manual approval on the same job, which must not be labelled "Completion"
      requestApprovalAs(ownerEmail, projectId, jobId, 'Buy extra tiles').its('status').should('eq', 201);

      cy.loginAs(ownerEmail);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('tr', 'Maple job').within(() => {
        cy.contains('In Progress').should('be.visible');
        cy.contains('Awaiting completion approval').should('be.visible');
      });

      cy.visit(`/projects/${projectId}/approvals`);
      // No message was given, so the backend's default text shows (translated)
      cy.contains('p', 'Requesting to complete this job')
        .parent()
        .within(() => cy.get('[data-testid="approval-kind-completion"]').should('have.text', 'Completion'));
      cy.contains('p', 'Buy extra tiles')
        .parent()
        .find('[data-testid="approval-kind-completion"]')
        .should('not.exist');

      // Approving completes the job, and the badge is gone from the job list
      cy.contains('p', 'Requesting to complete this job').parent().contains('button', 'Approve').click();
      modal().within(() => cy.contains('button', 'Approve').click());
      cy.contains('p', 'Requesting to complete this job').should('not.exist');

      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('tr', 'Maple job').within(() => {
        cy.contains('Completed').should('be.visible');
        cy.contains('Awaiting completion approval').should('not.exist');
      });
    });
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('shows a request cancelled by a direct completion as "Cancelled by", not "Rejected by"', () => {
    const ownerEmail = uniqueEmail('completion-lists-owner');
    const memberEmail = uniqueEmail('completion-lists-member');
    setUpCompletionRequest(ownerEmail, memberEmail, 'Willow').then(({ projectId, jobId }) => {
      // The Owner completes the job directly, which cancels the pending request automatically
      updateJobStatusAs(ownerEmail, projectId, jobId, 'COMPLETED').its('status').should('eq', 200);

      cy.loginAs(memberEmail);
      visitJobApprovals(projectId, jobId).within(() => {
        cy.get('[data-testid="approval-kind-completion"]').should('be.visible');
        cy.contains('Cancelled').should('be.visible');
        cy.contains('Cancelled by E2E Owner').should('be.visible');
        cy.contains('The job was completed directly.').should('be.visible');
        cy.contains('Rejected by').should('not.exist');
      });
    });
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('shows an approval that could not complete the job as "Approved, not applied" with the reason', () => {
    const ownerEmail = uniqueEmail('completion-lists-owner');
    const memberEmail = uniqueEmail('completion-lists-member');
    setUpCompletionRequest(ownerEmail, memberEmail, 'Rowan').then(({ projectId, jobId }) => {
      updateJobStatusAs(memberEmail, projectId, jobId, 'BLOCKED', 'Waiting on supplier');
      listApprovalsByJobAs(ownerEmail, projectId, jobId).then((approvals) => {
        decideApprovalAs(ownerEmail, projectId, jobId, approvals[0].id, 'APPROVED')
          .its('body.completionApplied').should('eq', false);
      });

      cy.loginAs(memberEmail);
      visitJobApprovals(projectId, jobId).within(() => {
        cy.get('[data-testid="approval-kind-completion"]').should('be.visible');
        cy.contains('Approved, not applied').should('be.visible');
        cy.contains('Approved by E2E Owner').should('be.visible');
        cy.contains("The job wasn't completed because it was Blocked.").should('be.visible');
      });
    });
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});
