// JOB-301 (ADR-0053): per-project job-detail section visibility. A section shows only when the
// org has its add-on AND the project doesn't hide it; hidden sections are fully invisible (no
// placeholder, no locked "upgrade" row entry, no data request). The completion gate (ADR-0052)
// stays independent of the Approvals section. Sections are hidden through the settings API in
// setup (the settings toggles are Frontend 2's job). Uses cy.loginAs() per
// docs/dev/process/E2E.md.

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  createJobAs,
  addMember,
  addProjectMember,
  updateJobStatusAs,
  updateProjectSettingsAs,
} from '../../support/orgApi';

type Section = 'NOTES' | 'APPROVALS' | 'RELATIONSHIPS' | 'LINKS' | 'STATUS_HISTORY';

/** A job-detail section's header (accordions are <button>s, Relationships a div role=button). */
const sectionHeader = (name: string) =>
  cy.contains('button span, [role="button"] span', new RegExp(`^${name}$`));
const lockedRow = () => cy.get('[data-testid="locked-section-row"]');
const jobTitle = (title: string) => cy.contains('h1', title).should('be.visible');

function hideSections(email: string, projectId: string, sections: Section[]) {
  updateProjectSettingsAs(email, projectId, { hiddenJobSections: sections }).then((res) => {
    expect(res.status).to.equal(200);
    expect(res.body.hiddenJobSections).to.deep.equal(sections);
  });
}

describe('Job section visibility per project', () => {
  it('a project that hides Notes shows no Notes on its jobs, while another project still does', { tags: '@smoke' }, () => {
    const ownerEmail = uniqueEmail('sections-notes');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    createOrgWithFullAccess(ownerEmail, 'Quiet Corp', uniqueSlug()).then(() =>
      createProjectAs(ownerEmail, 'Quiet Project').then((hidingProject) =>
        createProjectAs(ownerEmail, 'Chatty Project').then((otherProject) =>
          createJobAs(ownerEmail, hidingProject, { title: 'Quiet job' }).then((hidingJob) =>
            createJobAs(ownerEmail, otherProject, { title: 'Chatty job' }).then((otherJob) => {
              hideSections(ownerEmail, hidingProject, ['NOTES']);
              cy.intercept('GET', `**/api/projects/${hidingProject}/jobs/${hidingJob}/notes`).as('hiddenNotes');

              cy.loginAs(ownerEmail);
              cy.visit(`/projects/${hidingProject}/jobs/${hidingJob}`);
              jobTitle('Quiet job');
              // The rest of the page is there, Notes is gone without a trace
              sectionHeader('Approvals').should('be.visible');
              sectionHeader('Links').should('be.visible');
              sectionHeader('Notes').should('not.exist');
              lockedRow().should('not.exist');
              cy.get('@hiddenNotes.all').should('have.length', 0);

              cy.visit(`/projects/${otherProject}/jobs/${otherJob}`);
              jobTitle('Chatty job');
              sectionHeader('Notes').should('be.visible');
            }),
          ),
        ),
      ),
    );
    cy.deleteKeycloakUser(ownerEmail);
  });

  it('a project that hides Approvals shows neither the Approvals section nor the Request Approval button', () => {
    const ownerEmail = uniqueEmail('sections-approvals');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    createOrgWithFullAccess(ownerEmail, 'Lean Corp', uniqueSlug()).then(() =>
      createProjectAs(ownerEmail, 'Lean Project').then((projectId) =>
        createJobAs(ownerEmail, projectId, { title: 'Lean job' }).then((jobId) => {
          hideSections(ownerEmail, projectId, ['APPROVALS']);
          cy.intercept('GET', `**/api/projects/${projectId}/jobs/${jobId}/approvals`).as('hiddenApprovals');

          cy.loginAs(ownerEmail);
          cy.visit(`/projects/${projectId}/jobs/${jobId}`);
          jobTitle('Lean job');
          sectionHeader('Notes').should('be.visible');
          sectionHeader('Approvals').should('not.exist');
          cy.contains('button', 'Request Approval').should('not.exist');
          cy.get('@hiddenApprovals.all').should('have.length', 0);
        }),
      ),
    );
    cy.deleteKeycloakUser(ownerEmail);
  });

  it('a hidden section whose add-on the org lacks is left out of the locked upgrade row', () => {
    const ownerEmail = uniqueEmail('sections-locked');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    // A subscription with no add-ons: every optional section is locked
    createOrgWithSubscription(ownerEmail, 'Basic Corp', uniqueSlug()).then(() =>
      createProjectAs(ownerEmail, 'Basic Project').then((projectId) =>
        createJobAs(ownerEmail, projectId, { title: 'Basic job' }).then((jobId) => {
          hideSections(ownerEmail, projectId, ['NOTES']);

          cy.loginAs(ownerEmail);
          cy.visit(`/projects/${projectId}/jobs/${jobId}`);
          jobTitle('Basic job');
          lockedRow()
            .should('contain.text', 'Approvals')
            .and('contain.text', 'Links')
            .and('not.contain.text', 'Notes');
          sectionHeader('Notes').should('not.exist');
        }),
      ),
    );
    cy.deleteKeycloakUser(ownerEmail);
  });

  it('on a gated project with Approvals hidden, a Member can still request completion', () => {
    const ownerEmail = uniqueEmail('sections-gate-owner');
    const memberEmail = uniqueEmail('sections-gate-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');
    createOrgWithFullAccess(ownerEmail, 'Gate Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Gate Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          updateProjectSettingsAs(ownerEmail, projectId, {
            requireCompletionApproval: true,
            hiddenJobSections: ['APPROVALS'],
          }).its('status').should('eq', 200);
          createJobAs(ownerEmail, projectId, { title: 'Gate job', assignedTo: memberId }).then((jobId) => {
            updateJobStatusAs(memberEmail, projectId, jobId, 'IN_PROGRESS');

            cy.loginAs(memberEmail);
            cy.visit(`/projects/${projectId}/jobs/${jobId}`);
            jobTitle('Gate job');
            sectionHeader('Approvals').should('not.exist');
            cy.contains('button', 'Request Approval').should('not.exist');

            cy.contains('button', 'Request completion').click();
            cy.get('[role="dialog"][aria-modal="true"]').within(() =>
              cy.contains('button', 'Request completion').click(),
            );
            cy.get('[data-testid="completion-request-banner"]')
              .should('contain.text', 'Completion requested by E2E Member');
            cy.contains('button', 'Approval requested').should('be.disabled');
            sectionHeader('Approvals').should('not.exist');
          });
        }),
      ),
    );
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});
