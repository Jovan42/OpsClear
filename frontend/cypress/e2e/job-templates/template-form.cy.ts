// JOB-347 (E2E coverage push). Drives the template create/edit modal and the templates page
// states through the UI. `job-templates.cy.ts` covers the same feature through the API and the
// wildcard resolver, so nothing here repeats it: the form fields, the fixed-assignee picker, the
// edit flow, the wildcard help, the create/update error messages and the load-failure state.

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithFullAccess,
  createProjectAs,
  addMember,
  addProjectMember,
  createMilestoneAs,
  createJobTypeAs,
  createTemplateAs,
  listTemplatesAs,
} from '../../support/orgApi';

describe('Template form modal and templates page', () => {
  it('creates a fully populated template from the modal, edits it, and shows the saved fields on the row', { tags: '@smoke' }, () => {
    const email = uniqueEmail('tpl-form');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Tpl Form Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Tpl Form Project').then((projectId) => {
        const memberEmail = uniqueEmail('tpl-form-member');
        cy.createKeycloakUser(memberEmail, 'Maja', 'Member');
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, email, memberId, 'MEMBER');
          addProjectMember(projectId, email, memberId, 'MEMBER');
        });
        createMilestoneAs(email, projectId, 'Launch');
        createJobTypeAs(email, projectId, 'Bug', 'RED');

        cy.loginAs(email);
        cy.viewport(1000, 1200);
        cy.visit(`/projects/${projectId}/templates`);
        cy.contains('No templates yet').should('be.visible');
        cy.contains('button', 'Create first template').click();

        cy.get('[role="dialog"]').within(() => {
          cy.contains('button', 'Create').should('be.disabled');
          cy.get('input[placeholder="e.g. Weekly status report"]').type('Weekly report');
          cy.contains('button', 'Create').should('not.be.disabled');
          cy.get('input[placeholder^="e.g. Report for"]').type('Report for {{}{{}project}}');
          cy.get('input[placeholder="Client name"]').type('Acme Co');
          cy.contains('label', 'Priority').parent().find('select').select('High');
          cy.get('input[placeholder="e.g. 7"]').type('5');
          cy.contains('label', 'Milestone').parent().find('select').select('Launch');
          cy.contains('label', 'Default type').parent().find('select').select('Bug');

          // Fixed assignee: search narrows the list, picking shows the name, the x clears it
          cy.contains('label', 'Assignee mode').parent().find('select').select('Fixed — pre-fill assignee');
          cy.get('input[placeholder="Search member…"]').type('zzz-nobody');
          cy.contains('Maja').should('not.exist');
          cy.get('input[placeholder="Search member…"]').clear();
          cy.get('input[placeholder="Search member…"]').type('maja');
          cy.contains('button', 'Maja').click();
          cy.get('input[placeholder="Search member…"]').should('have.value', 'Maja Member');
          cy.get('input[placeholder="Search member…"]').parent().contains('button', '×').click();
          cy.get('input[placeholder="Search member…"]').should('have.value', '');
          cy.get('input[placeholder="Search member…"]').type('maja');
          cy.contains('button', 'Maja').click();

          // Wildcard help toggles open and closed
          cy.contains('button', 'Available wildcards').click();
          cy.contains('Project name').should('be.visible');
          cy.contains('button', 'Available wildcards').click();
          cy.contains('Project name').should('not.exist');

          cy.contains('button', 'Create').click();
        });

        cy.contains('p', 'Weekly report').should('be.visible');
        cy.contains('+5d deadline').should('be.visible');
        cy.contains('📍 Launch').should('be.visible');
        cy.contains('HIGH', { matchCase: false }).should('be.visible');
        listTemplatesAs(email, projectId).then((templates) => {
          const t = templates.find((x) => x.name === 'Weekly report')!;
          expect(t.milestoneName).to.equal('Launch');
        });

        // Edit: the modal opens pre-filled, a change saves and shows on the row
        cy.contains('button', 'Edit').click();
        cy.get('[role="dialog"]').within(() => {
          cy.contains('Edit Template').should('be.visible');
          cy.get('input[placeholder="e.g. Weekly status report"]').should('have.value', 'Weekly report');
          cy.get('input[placeholder="e.g. 7"]').should('have.value', '5');
          cy.get('input[placeholder="e.g. 7"]').clear();
          cy.get('input[placeholder="e.g. 7"]').type('9');
          cy.get('input[placeholder="e.g. Weekly status report"]').clear();
          cy.get('input[placeholder="e.g. Weekly status report"]').type('Monthly report');
          cy.contains('button', 'Save').click();
        });
        cy.contains('p', 'Monthly report').should('be.visible');
        cy.contains('+9d deadline').should('be.visible');
        cy.contains('p', 'Weekly report').should('not.exist');
      }),
    );
    cy.deleteKeycloakUser(email);
  });

  it('shows the create and update failure messages inside the modal and keeps it open', () => {
    const email = uniqueEmail('tpl-err');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Tpl Err Corp', uniqueSlug());
    createProjectAs(email, 'Tpl Err Project').then((projectId) => {
      createTemplateAs(email, projectId, { name: 'Existing' });
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/templates`);
      cy.contains('p', 'Existing').should('be.visible');

      cy.intercept('POST', '**/templates', { statusCode: 500, body: { message: 'boom' } }).as('create');
      cy.contains('button', '+ New Template').click();
      cy.get('[role="dialog"]').within(() => {
        cy.get('input[placeholder="e.g. Weekly status report"]').type('Will fail');
        cy.contains('button', 'Create').click();
        cy.wait('@create');
        cy.contains('Failed to create template. Please try again.').should('be.visible');
        cy.contains('button', 'Cancel').click();
      });
      cy.contains('Failed to create template').should('not.exist');

      cy.intercept('PUT', '**/templates/*', { statusCode: 500, body: { message: 'boom' } }).as('update');
      cy.contains('button', 'Edit').click();
      cy.get('[role="dialog"]').within(() => {
        cy.get('input[placeholder="e.g. Weekly status report"]').clear();
        cy.get('input[placeholder="e.g. Weekly status report"]').type('Renamed');
        cy.contains('button', 'Save').click({ force: true });
        cy.wait('@update');
        cy.contains('Failed to update template. Please try again.').should('be.visible');
      });
    });
    cy.deleteKeycloakUser(email);
  });

  it('shows an error page with a working retry when the template list fails to load', () => {
    const email = uniqueEmail('tpl-load');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Tpl Load Corp', uniqueSlug());
    createProjectAs(email, 'Tpl Load Project').then((projectId) => {
      createTemplateAs(email, projectId, { name: 'Reachable after retry' });
      cy.loginAs(email);
      cy.intercept('GET', '**/api/projects/*/templates', { statusCode: 500, body: { message: 'boom' } }).as('fail');
      cy.visit(`/projects/${projectId}/templates`);
      cy.contains('Failed to load templates.').should('be.visible');

      cy.intercept('GET', '**/api/projects/*/templates', (req) => req.continue()).as('ok');
      cy.contains('button', /retry|try again/i).click();
      cy.contains('p', 'Reachable after retry').should('be.visible');
    });
    cy.deleteKeycloakUser(email);
  });
});
