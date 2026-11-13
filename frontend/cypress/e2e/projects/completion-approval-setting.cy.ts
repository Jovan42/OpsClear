// JOB-288 (ADR-0052): the "Require approval to complete jobs" toggle in Project settings.
// Uses cy.loginAs() per docs/dev/process/E2E.md. The toggle needs a usable APPROVALS
// add-on to turn on, so the editable cases use an internal (all add-ons) org and the
// locked case a plain subscription without add-ons.

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithSubscription,
  createOrgWithFullAccess,
  createProjectAs,
  addMember,
  addProjectMember,
} from '../../support/orgApi';

const toggle = () => cy.get('[data-testid="completion-approval-toggle"]');

describe('Project settings: completion approval toggle', () => {
  it('an Owner turns the toggle on and it stays on after a reload', { tags: '@smoke' }, () => {
    const email = uniqueEmail('gate-owner');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Gatekeep Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Gatekeep Rollout').then((projectId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/settings`);

        cy.contains('Require approval to complete jobs').should('be.visible');
        toggle().should('have.attr', 'aria-checked', 'false').and('not.have.attr', 'aria-disabled');
        toggle().click();
        cy.contains('Jobs now need approval to complete').should('be.visible');
        toggle().should('have.attr', 'aria-checked', 'true');

        cy.reload();
        toggle().should('have.attr', 'aria-checked', 'true');
      }),
    );
    cy.deleteKeycloakUser(email);
  });

  it('a Member sees the setting read-only and clicking it changes nothing', () => {
    const ownerEmail = uniqueEmail('gate-owner');
    const memberEmail = uniqueEmail('gate-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Quarry Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Quarry Upgrade').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          cy.loginAs(memberEmail);
          cy.visit(`/projects/${projectId}/settings`);
          toggle().should('have.attr', 'aria-disabled', 'true').and('have.attr', 'aria-checked', 'false');
          cy.get('[data-testid="completion-approval-read-only"]')
            .should('contain.text', 'Only project Owners and Admins can change this.');
          toggle().click();
          toggle().should('have.attr', 'aria-checked', 'false');
        }),
      ),
    );
    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('an org without APPROVALS sees it locked, but a gate that is already on can still be turned off', () => {
    const email = uniqueEmail('gate-locked');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Lantern Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(email, 'Lantern Refit').then((projectId) => {
        cy.loginAs(email);
        cy.visit(`/projects/${projectId}/settings`);
        cy.get('[data-testid="completion-approval-locked"]')
          .should('contain.text', 'Requires an active Approvals add-on.')
          .and('contain.text', 'Upgrade in org settings');
        toggle().should('have.attr', 'aria-disabled', 'true').and('have.attr', 'aria-checked', 'false');
        toggle().click();
        toggle().should('have.attr', 'aria-checked', 'false');

        // The add-on lapsed after the gate was turned on: the API refuses to turn it on
        // without the add-on, so set it straight in the database.
        cy.task('queryDb', {
          sql: 'UPDATE projects SET require_completion_approval = true WHERE organisation_id = $1 AND UPPER(friendly_id) = UPPER($2)',
          params: [orgId, projectId],
        });
        cy.reload();
        cy.get('[data-testid="completion-approval-locked"]').should('contain.text', 'Not enforced');
        toggle().should('have.attr', 'aria-checked', 'true').and('not.have.attr', 'aria-disabled');
        toggle().click();
        cy.contains('Jobs no longer need approval to complete').should('be.visible');
        toggle().should('have.attr', 'aria-checked', 'false').and('have.attr', 'aria-disabled', 'true');
      }),
    );
    cy.deleteKeycloakUser(email);
  });
});
