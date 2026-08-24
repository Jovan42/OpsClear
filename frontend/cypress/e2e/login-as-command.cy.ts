// Verifies cy.loginAs() itself (JOB-204) — not a feature-area spec (those start at
// JOB-208), this is infrastructure coverage for the auth bypass every other spec
// will rely on.
const DEMO_USERS = [
  'testuser@example.com',
  'alice@example.com',
  'bob@example.com',
  'carol@example.com',
  'dave@example.com',
];

describe('cy.loginAs()', () => {
  for (const email of DEMO_USERS) {
    it(`authenticates as ${email} with no visible login redirect`, () => {
      cy.loginAs(email);
      cy.visit('/projects');

      // RequireAuth redirects an unauthenticated visit to `/` (same host, so a
      // host-only check wouldn't catch a silent auth failure) — assert the path
      // itself stayed on the protected route instead.
      cy.url().should('include', '/projects');
      cy.contains('OpsClear').should('be.visible');
    });
  }
});
