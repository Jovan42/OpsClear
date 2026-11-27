// ADR-0064 (JOB-434): the support address in the public page footers and the user menu.
// A mailto link can't be followed in Cypress, so the specs check the href instead.
// The signed-in tests create their own user and org: CI's E2E database has no seed.sh demo orgs.

import { uniqueEmail, uniqueSlug, createOrgWithSubscription } from '../../support/orgApi';

const SUPPORT_HREF = 'mailto:support@opsclear.app';

describe('Support contact', () => {
  it('the landing page footer links to the support address', { tags: '@smoke' }, () => {
    cy.visit('/');
    cy.get('footer [data-testid=support-link]')
      .should('be.visible')
      .and('have.attr', 'href', SUPPORT_HREF)
      .and('have.text', 'support@opsclear.app');
  });

  it('the features page and the logged-out guide show the support link', () => {
    cy.visit('/features');
    cy.get('footer [data-testid=support-link]').should('have.attr', 'href', SUPPORT_HREF);

    cy.visit('/guide');
    cy.get('footer [data-testid=support-link]').should('have.attr', 'href', SUPPORT_HREF);
  });

  it('the user menu shows Contact support right after Feedback', () => {
    const email = uniqueEmail('support-menu');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Support Menu Org', uniqueSlug()).then(() => {
      cy.loginAs(email);
      cy.visit('/projects');
      cy.get('button[aria-label="User menu"]').click();
      cy.contains('button', 'Feedback')
        .next()
        .should('have.prop', 'tagName', 'A')
        .and('have.text', 'Contact support')
        .and('have.attr', 'href', SUPPORT_HREF);
    });
  });

  it('the signed-in guide has no public footer', () => {
    const email = uniqueEmail('support-guide');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Support Guide Org', uniqueSlug()).then(() => {
      cy.loginAs(email);
      cy.visit('/guide');
      cy.get('button[aria-label="User menu"]').should('be.visible');
      cy.get('[data-testid=support-link]').should('not.exist');
    });
  });
});
