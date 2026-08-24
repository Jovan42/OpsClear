// Proves the Cypress runner itself works, headless (`cypress run`) and
// interactively (`cypress open`), against a real running dev server — no app-specific
// assertions, that's what the feature-area specs are for (JOB-208 onward).
describe('Cypress runner smoke test', () => {
  it('loads the app and finds the OpsClear landing page', () => {
    cy.visit('/');
    cy.contains('OpsClear').should('be.visible');
  });
});
