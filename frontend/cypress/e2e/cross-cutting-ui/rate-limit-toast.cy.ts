// JOB-339 (ADR-0058, MIL-042): a 429 from either rate-limiting layer (backend per API key,
// nginx per IP) shows a translated "Too many requests" toast with the server's message as
// the description. Both layers send the same body, so a stubbed reply stands in for both.
//
// The stub is on a mutation (POST /api/projects) on purpose: mutations aren't retried, so
// one stubbed reply produces exactly one toast. The real per-key limit only applies to
// API-key traffic, which the browser never sends, so it can't be triggered from the UI.

import { uniqueEmail, uniqueSlug, createOrgWithSubscription } from '../../support/orgApi';

const RATE_LIMIT_MESSAGE = 'Rate limit exceeded. Try again in 7 second(s).';

function stubRateLimitedProjectCreate() {
  cy.intercept('POST', '**/api/projects', {
    statusCode: 429,
    headers: { 'Retry-After': '7' },
    body: {
      error: 'Too Many Requests',
      message: RATE_LIMIT_MESSAGE,
      timestamp: new Date().toISOString(),
    },
  }).as('createProject');
}

describe('Rate limit (429) error toast', () => {
  it('shows "Too many requests" with the server message when an API call is rate limited', { tags: '@smoke' }, () => {
    const email = uniqueEmail('rate-limit-toast');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Rate Limit Corp', uniqueSlug()).then(() => {
      // Start in English regardless of what an earlier spec left in localStorage.
      cy.clearLocalStorage();
      stubRateLimitedProjectCreate();
      cy.loginAs(email);
      cy.visit('/projects');

      cy.contains('button', '+ New Project').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Website Redesign"]').type('Throttled Project');
        cy.contains('button', 'Create project').click();
      });
      cy.wait('@createProject');

      cy.get('[data-sonner-toast]').should('be.visible').and('contain.text', 'Too many requests');
      cy.get('[data-sonner-toast]').should('contain.text', RATE_LIMIT_MESSAGE);
    });

    cy.deleteKeycloakUser(email);
  });

  it('translates the category in Serbian and keeps the server message as is', () => {
    const email = uniqueEmail('rate-limit-toast-sr');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Rate Limit SR Corp', uniqueSlug()).then(() => {
      cy.clearLocalStorage();
      stubRateLimitedProjectCreate();
      cy.loginAs(email);

      cy.visit('/settings');
      cy.contains('button', 'Srpski').click();

      cy.visit('/projects');
      cy.contains('button', '+ Novi projekat').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input').first().type('Throttled Project');
        cy.contains('button', /Kreiraj|Create/).click();
      });
      cy.wait('@createProject');

      cy.get('[data-sonner-toast]').should('be.visible').and('contain.text', 'Previše zahteva');
      // The detailed message stays English (ADR-0037's deliberate cut).
      cy.get('[data-sonner-toast]').should('contain.text', RATE_LIMIT_MESSAGE);
    });

    cy.deleteKeycloakUser(email);
  });
});
