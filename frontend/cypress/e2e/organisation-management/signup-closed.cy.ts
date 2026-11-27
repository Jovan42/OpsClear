// ADR-0066 (JOB-442): /onboarding while sign-up is closed shows "We're not open yet" instead of
// the create form. CI runs with sign-up open, so "closed" is a stubbed GET /api/public/signup,
// or a stubbed 403 from POST /api/organisations. The super-user check and the "Join the list"
// sign-up are real. The backend's 403 and its super-user exemption are covered by
// SignupClosedIntegrationTest.

import { freeSlug, makeSuperUser, uniqueEmail } from '../../support/orgApi';

const STATUS_URL = '**/api/public/signup';

describe('Create organisation while sign-up is closed', () => {
  it('shows the "not open yet" card, and Join the list puts the account email on the list', () => {
    const email = uniqueEmail('signup-closed');
    cy.createKeycloakUser(email, 'E2E', 'Closed');
    cy.intercept('GET', STATUS_URL, { body: { open: false } });
    cy.intercept('POST', '**/api/public/signups').as('signUp');
    cy.loginAs(email);
    cy.visit('/onboarding');

    cy.get('[data-testid="signup-closed-card"]').within(() => {
      cy.contains('h1', "We're not open yet").should('be.visible');
      cy.contains("We'll email you at launch.").should('be.visible');
      cy.get('[data-testid="signup-fixed-email"]').should('have.text', email);
      cy.contains('button', 'Log out').should('be.visible');

      cy.contains('button', 'Join the list').click();
      cy.contains('Tick the box so we can email you at launch').should('be.visible');
      cy.contains('label', 'Email me when OpsClear launches').find('input[type="checkbox"]').check();
      cy.contains('button', 'Join the list').click();
    });
    cy.wait('@signUp').its('request.body').should('deep.include', { list: 'early-access', email, consent: true });
    cy.get('[data-testid="signup-thanks"]').should('be.visible');
    cy.get('#org-name').should('not.exist');

    cy.task('queryDb', { sql: 'DELETE FROM email_signups WHERE lower(email) = lower($1)', params: [email] });
    cy.deleteKeycloakUser(email);
  });

  it('a super user still gets the create form', () => {
    const email = uniqueEmail('signup-closed-super');
    cy.createKeycloakUser(email, 'E2E', 'Super');
    makeSuperUser(email).then(() => {
      cy.intercept('GET', STATUS_URL, { body: { open: false } });
      cy.loginAs(email);
      cy.visit('/onboarding');
      cy.get('#org-name').should('be.visible');
      cy.get('[data-testid="signup-closed-card"]').should('not.exist');
    });
    cy.deleteKeycloakUser(email);
  });

  it('a 403 from creating the organisation shows the same card, with no error toast', () => {
    const email = uniqueEmail('signup-closed-403');
    cy.createKeycloakUser(email, 'E2E', 'Forbidden');
    cy.intercept('POST', '**/api/organisations', {
      statusCode: 403,
      body: { error: 'Forbidden', message: 'Sign-up is closed until launch' },
    }).as('createOrg');
    cy.loginAs(email);
    cy.visit('/onboarding');

    cy.get('#org-name').type('Too Early Ltd');
    freeSlug().then((slug) => cy.get('#org-slug').type(slug));
    cy.contains('button', 'Create organisation').click();
    cy.wait('@createOrg');

    cy.contains('h1', "We're not open yet").should('be.visible');
    cy.get('[data-sonner-toast]').should('not.exist');
    cy.deleteKeycloakUser(email);
  });
});
