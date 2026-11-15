// JOB-302 (ADR-0053): the "Job screen" section of Project settings. Owners/Admins choose which
// optional job-detail sections show on this project's jobs; a section whose add-on the org lacks
// is locked with "Not in your plan"; Members see it read-only. The ADR-0052 completion-gate
// toggle lives in the same section. Every test creates its own org, project and users.
// Uses cy.loginAs() per docs/dev/process/E2E.md.

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
  getProjectAs,
} from '../../support/orgApi';

const SECTIONS = ['NOTES', 'APPROVALS', 'RELATIONSHIPS', 'LINKS', 'STATUS_HISTORY'] as const;

const jobScreen = () => cy.get('[data-testid="job-screen-section"]');
const sectionToggle = (section: (typeof SECTIONS)[number]) =>
  cy.get(`[data-testid="job-section-toggle-${section}"]`);
/** A job-detail section's header (accordions are <button>s, Relationships a div role=button). */
const jobSectionHeader = (name: string) =>
  cy.contains('button span, [role="button"] span', new RegExp(`^${name}$`));

describe('Project settings: Job screen section', () => {
  it('an Owner hides Notes, it stays hidden after a reload and the project\'s jobs no longer show Notes', { tags: '@smoke' }, () => {
    const email = uniqueEmail('jobscreen-owner');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithFullAccess(email, 'Tidy Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Tidy Project').then((projectId) =>
        createJobAs(email, projectId, { title: 'Tidy job' }).then((jobId) => {
          cy.loginAs(email);
          cy.visit(`/projects/${projectId}/settings`);

          jobScreen().within(() => {
            cy.contains('h2', 'Job screen').should('be.visible');
            cy.contains('Sections shown on jobs').should('be.visible');
            cy.contains("Hide sections this project doesn't use.").should('be.visible');
          });
          // All on by default, and editable
          SECTIONS.forEach((s) =>
            sectionToggle(s).should('have.attr', 'aria-checked', 'true').and('not.have.attr', 'aria-disabled'),
          );
          cy.get('[data-testid="job-sections-setting"]').should('not.contain.text', 'Not in your plan');

          cy.intercept('PATCH', `**/api/projects/${projectId}/settings`).as('saveSettings');
          sectionToggle('NOTES').click();
          cy.wait('@saveSettings').its('request.body').should('deep.equal', { hiddenJobSections: ['NOTES'] });
          cy.contains('Notes now hidden on jobs').should('be.visible');
          sectionToggle('NOTES').should('have.attr', 'aria-checked', 'false');

          cy.reload();
          sectionToggle('NOTES').should('have.attr', 'aria-checked', 'false');
          sectionToggle('APPROVALS').should('have.attr', 'aria-checked', 'true');

          cy.visit(`/projects/${projectId}/jobs/${jobId}`);
          cy.contains('h1', 'Tidy job').should('be.visible');
          jobSectionHeader('Approvals').should('be.visible');
          jobSectionHeader('Notes').should('not.exist');

          // Turning it back on sends an empty list (every section shown)
          cy.visit(`/projects/${projectId}/settings`);
          sectionToggle('NOTES').should('not.have.attr', 'aria-disabled');
          sectionToggle('NOTES').click();
          cy.wait('@saveSettings').its('request.body').should('deep.equal', { hiddenJobSections: [] });
          cy.contains('Notes now shown on jobs').should('be.visible');
          getProjectAs(email, projectId).its('hiddenJobSections').should('deep.equal', []);
        }),
      ),
    );
    cy.deleteKeycloakUser(email);
  });

  it('sections not in the plan show disabled with "Not in your plan" and clicking them saves nothing', () => {
    const email = uniqueEmail('jobscreen-plan');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    // A plain subscription without add-ons: none of the five sections is in the plan
    createOrgWithSubscription(email, 'Basic Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Basic Project').then((projectId) => {
        cy.intercept('PATCH', `**/api/projects/${projectId}/settings`).as('saveSettings');
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/settings`);

        SECTIONS.forEach((s) => {
          cy.get(`[data-testid="job-section-locked-${s}"]`)
            .should('contain.text', 'Not in your plan')
            .find('svg')
            .should('exist');
          sectionToggle(s).should('have.attr', 'aria-disabled', 'true').and('have.attr', 'aria-checked', 'false');
        });
        sectionToggle('NOTES').click();
        sectionToggle('NOTES').should('have.attr', 'aria-checked', 'false');
        cy.get('@saveSettings.all').should('have.length', 0);
      }),
    );
    cy.deleteKeycloakUser(email);
  });

  it("a Member sees the toggles read-only and can't change them", () => {
    const ownerEmail = uniqueEmail('jobscreen-owner');
    const memberEmail = uniqueEmail('jobscreen-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');
    createOrgWithFullAccess(ownerEmail, 'Steady Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Steady Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');
          cy.intercept('PATCH', `**/api/projects/${projectId}/settings`).as('saveSettings');

          cy.loginAs(memberEmail);
          cy.visit(`/projects/${projectId}/settings`);
          cy.get('[data-testid="job-sections-read-only"]')
            .should('contain.text', 'Only project Owners and Admins can change this.');
          SECTIONS.forEach((s) =>
            sectionToggle(s).should('have.attr', 'aria-disabled', 'true').and('have.attr', 'aria-checked', 'true'),
          );
          sectionToggle('LINKS').click();
          sectionToggle('LINKS').should('have.attr', 'aria-checked', 'true');
          cy.get('@saveSettings.all').should('have.length', 0);
        }),
      ),
    );
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('the completion-gate toggle sits in the Job screen section and still saves', () => {
    const email = uniqueEmail('jobscreen-gate');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithFullAccess(email, 'Gateway Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Gateway Project').then((projectId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/settings`);

        jobScreen().within(() => {
          cy.contains('Require approval to complete jobs').should('be.visible');
          cy.get('[data-testid="completion-approval-toggle"]')
            .should('have.attr', 'aria-checked', 'false')
            .click();
        });
        cy.contains('Jobs now need approval to complete').should('be.visible');

        cy.reload();
        jobScreen().find('[data-testid="completion-approval-toggle"]').should('have.attr', 'aria-checked', 'true');
        // Saving the gate leaves the section choices alone
        SECTIONS.forEach((s) => sectionToggle(s).should('have.attr', 'aria-checked', 'true'));
        getProjectAs(email, projectId).then((project) => {
          expect(project.requireCompletionApproval).to.equal(true);
          expect(project.hiddenJobSections).to.deep.equal([]);
        });
      }),
    );
    cy.deleteKeycloakUser(email);
  });
});
