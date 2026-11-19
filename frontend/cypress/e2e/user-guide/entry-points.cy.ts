// ADR-0062 (JOB-356): the ways into the user guide. The public nav and footer, the
// "How does this work?" links on first-run empty states, and the /features cross-links.

import { uniqueEmail, uniqueSlug, createOrgWithSubscription, createProjectAs } from '../../support/orgApi';

describe('User guide entry points', () => {
  beforeEach(() => {
    cy.viewport(1280, 800);
  });

  it('the public nav and the landing footer link to the guide', () => {
    cy.visit('/');
    cy.get('footer').contains('a', 'Guide').should('have.attr', 'href', '/guide');
    cy.get('nav').contains('a', 'Guide').click();
    cy.location('pathname').should('eq', '/guide');
    cy.contains('h1', 'Getting started with OpsClear').should('be.visible');
  });

  it('empty states for no projects, no jobs and no teammates link to the matching guide page', { tags: '@smoke' }, () => {
    const email = uniqueEmail('guide-empty');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Guide Corp', uniqueSlug());
    cy.loginAs(email);

    cy.visit('/projects');
    cy.get('[data-testid=empty-state-help-link]').should('contain', 'How does this work?').click();
    cy.location('pathname').should('eq', '/guide/first-project');
    cy.contains('h1', 'Create a project and add members').should('be.visible');

    cy.visit('/org/members');
    cy.contains("You're the only member so far.").should('be.visible');
    cy.get('[data-testid=empty-state-help-link]').click();
    cy.location('pathname').should('eq', '/guide/invite-team');

    createProjectAs(email, 'Guide Project').then((projectId) => {
      cy.visit(`/projects/${projectId}/jobs`);
      cy.get('[data-testid=empty-state-help-link]').should('be.visible').click();
      cy.location('pathname').should('eq', '/guide/create-job');
      cy.contains('h1', 'Create and assign a job').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('/features cards link to their guide page and the guide links back', () => {
    cy.visit('/features');
    cy.get('[data-testid=feature-card-milestones]').contains('a', 'How to use it').click();
    cy.location('pathname').should('eq', '/guide/milestones');

    cy.visit('/guide');
    cy.get('[data-testid=guide-feature-link]').click();
    cy.location('pathname').should('eq', '/features');
    cy.location('hash').should('eq', '#job-tracking');
    cy.get('[data-testid=feature-card-job-tracking]').should('be.visible');
  });

  it('the Feedback & Credits card links to the Feedback page and the page links back', () => {
    cy.visit('/features');
    cy.get('[data-testid=feature-card-feedback-credits]').contains('a', 'How to use it').click();
    cy.location('pathname').should('eq', '/guide/feedback');
    cy.contains('h1', 'Feedback and credits').should('be.visible');

    cy.get('[data-testid=guide-feature-link]').click();
    cy.location('pathname').should('eq', '/features');
    cy.location('hash').should('eq', '#feedback-credits');
    cy.get('[data-testid=feature-card-feedback-credits]').should('be.visible');
  });
});
