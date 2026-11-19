// JOB-347 (E2E coverage push). The "Org Templates" section on /org/settings, driven through the
// UI: create (org-mode form with a default type name and no fixed-assignee option), edit, delete,
// the page's navigation buttons and the unsaved-details Cancel. `job-templates.cy.ts` covers
// org templates through the API and the "start from template" flow, not this section.

import {
  uniqueEmail,
  uniqueSlug,
  createOrgWithFullAccess,
  createOrgTemplateAs,
} from '../../support/orgApi';

describe('Org settings: org templates and navigation', () => {
  it('creates, edits and deletes an org template from the settings page', { tags: '@smoke' }, () => {
    const email = uniqueEmail('org-tpl');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Org Tpl Corp', uniqueSlug());

    cy.loginAs(email);
    cy.viewport(1000, 1400);
    cy.visit('/org/settings');
    cy.contains('h2', 'Org Templates').should('be.visible');
    cy.contains('No org-level templates yet.').should('be.visible');

    cy.contains('button', 'Create one').click();
    cy.get('[role="dialog"]').within(() => {
      cy.contains('New Template').should('be.visible');
      // Org templates can't pin a member: no "Fixed" assignee mode, a type name instead of a type picker
      cy.contains('label', 'Assignee mode').parent().find('select option').should('have.length', 2);
      cy.get('input[placeholder="e.g. Weekly status report"]').type('Org weekly report');
      cy.get('input[placeholder="e.g. Bug, Feature, Maintenance"]').type('Bug');
      cy.get('input[placeholder="e.g. 7"]').type('3');
      cy.contains('label', 'Priority').parent().find('select').select('Critical');
      cy.contains('button', 'Create').click();
    });
    cy.contains('p', 'Org weekly report').should('be.visible');
    cy.contains('+3d').should('be.visible');

    cy.contains('button', 'Edit').click();
    cy.get('[role="dialog"]').within(() => {
      cy.get('input[placeholder="e.g. Bug, Feature, Maintenance"]').should('have.value', 'Bug');
      cy.get('input[placeholder="e.g. Weekly status report"]').clear();
      cy.get('input[placeholder="e.g. Weekly status report"]').type('Org monthly report');
      cy.contains('button', 'Save').click();
    });
    cy.contains('p', 'Org monthly report').should('be.visible');
    cy.contains('p', 'Org weekly report').should('not.exist');

    cy.contains('button', 'Delete').click();
    cy.get('[role="dialog"]').within(() => {
      cy.contains('Delete "Org monthly report"?').should('be.visible');
      cy.contains('button', 'Delete').click();
    });
    cy.contains('p', 'Org monthly report').should('not.exist');
    cy.contains('No org-level templates yet.').should('be.visible');

    cy.deleteKeycloakUser(email);
  });

  it('shows the server message when deleting an org template fails with a conflict', () => {
    const email = uniqueEmail('org-tpl-409');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Org Conflict Corp', uniqueSlug()).then((orgId) => {
      createOrgTemplateAs(email, orgId, { name: 'In use template' });
      cy.loginAs(email);
      cy.visit('/org/settings');
      cy.contains('p', 'In use template').should('be.visible');

      cy.intercept('DELETE', '**/api/organisations/*/templates/*', {
        statusCode: 409,
        body: { message: 'Template is used by an active schedule' },
      }).as('del');
      cy.contains('button', 'Delete').click();
      cy.get('[role="dialog"]').within(() => {
        cy.contains('button', 'Delete').click();
        cy.wait('@del');
        cy.contains('Template is used by an active schedule').should('be.visible');
      });
    });
    cy.deleteKeycloakUser(email);
  });

  it('the members and invites shortcuts navigate, and Cancel reverts unsaved details', () => {
    const email = uniqueEmail('org-nav');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Org Nav Corp', uniqueSlug());

    cy.loginAs(email);
    cy.visit('/org/settings');
    cy.get('#org-name').clear();
    cy.get('#org-name').type('Changed but not saved');
    cy.contains('button', 'Save changes').should('not.be.disabled');
    cy.contains('button', 'Cancel').click();
    cy.get('#org-name').should('have.value', 'Org Nav Corp');
    cy.contains('button', 'Save changes').should('be.disabled');

    cy.contains('button', 'Manage members').click();
    cy.location('pathname').should('eq', '/org/members');
    cy.go('back');
    cy.contains('button', 'Invites').click();
    cy.location('pathname').should('eq', '/org/invites');
    cy.deleteKeycloakUser(email);
  });
});
