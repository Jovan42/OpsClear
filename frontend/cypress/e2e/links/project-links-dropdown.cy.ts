// JOB-347 (E2E coverage push). The project nav "Links" dropdown through the UI: add, edit
// (save and cancel), delete, cancel an add, and close on an outside click. `links.cy.ts` covers
// the job-level panel and the API rules, so only the dropdown's own controls are driven here.

import {
  uniqueEmail,
  uniqueSlug,
  createOrgWithFullAccess,
  createProjectAs,
  createProjectLinkAs,
} from '../../support/orgApi';

describe('Project links dropdown', () => {
  it('adds, edits, cancels and deletes project links, and closes on an outside click', { tags: '@smoke' }, () => {
    const email = uniqueEmail('proj-links');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Dropdown Corp', uniqueSlug());
    createProjectAs(email, 'Dropdown Project').then((projectId) => {
      createProjectLinkAs(email, projectId, 'https://seeded.example.com', 'Seeded link');

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.get('nav').contains('button', 'Links').click();
      cy.contains('Project Links').should('be.visible');
      cy.contains('a', 'Seeded link').should('be.visible');

      // Add: Save is disabled until a URL is typed; Cancel discards the form
      cy.contains('button', '+ Add link').click();
      cy.contains('button', 'Save').should('be.disabled');
      cy.get('input[placeholder="https://…"]').type('https://github.com/acme/repo');
      cy.contains('button', 'Save').should('not.be.disabled');
      cy.contains('button', 'Cancel').click();
      cy.get('input[placeholder="https://…"]').should('not.exist');

      // A recognised service fills the label
      cy.contains('button', '+ Add link').click();
      cy.get('input[placeholder="https://…"]').type('https://github.com/acme/repo');
      cy.get('input[placeholder="https://…"]').parent().find('input').eq(1).should('have.value', 'GitHub');
      cy.contains('button', 'Save').click();
      cy.contains('a', 'GitHub').should('be.visible');

      // Edit: cancel keeps the old value, save replaces it
      cy.get('button[title="Edit link"]').first().click();
      cy.contains('button', 'Cancel').click();
      cy.contains('a', 'Seeded link').should('be.visible');
      cy.get('button[title="Edit link"]').first().click();
      cy.get('input[placeholder="https://…"]').clear();
      cy.get('input[placeholder="https://…"]').type('https://renamed.example.com');
      cy.contains('button', 'Save').click();
      cy.contains('a', 'Seeded link').should('have.attr', 'href').and('contain', 'renamed.example.com');

      // Delete one through the confirm modal
      cy.get('button[title="Delete link"]').first().click();
      cy.get('[role="dialog"]').within(() => cy.contains('button', 'Delete').click());
      cy.contains('a', 'Seeded link').should('not.exist');
      cy.contains('a', 'GitHub').should('be.visible');

      // Outside click closes the panel
      cy.get('main').click('center', { force: true });
      cy.contains('Project Links').should('not.exist');
    });
    cy.deleteKeycloakUser(email);
  });
});
