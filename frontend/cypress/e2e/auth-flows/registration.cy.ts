// ADR-0049 Appendix §1 (Auth Flows — Registration). Real Keycloak UI throughout.
// Runtime form uses Keycloak's dynamic user-profile registration form
// (register-user-profile), not the static register.ftl file in this repo's custom
// theme — field ids (#email/#password/#password-confirm/#firstName/#lastName) match,
// but validation/error rendering differs (client-side "Please specify X" messages for
// blank required fields, not the theme's #input-error-* spans) — asserting the actual
// runtime behavior, confirmed empirically, not what the static template implies.

describe('Registration', () => {
  beforeEach(() => {
    cy.visit('http://localhost:8180/realms/opsclear/protocol/openid-connect/logout?client_id=opsclear-frontend&post_logout_redirect_uri=http://localhost:5173');
  });

  it('self-registers with a unique email/password, redirected into the app authenticated', { tags: '@smoke' }, () => {
    const email = `e2e-newuser-${Date.now()}@example.com`;
    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', { args: { email } }, ({ email }) => {
      cy.contains('a', 'Register').click();
      cy.get('#email').type(email);
      cy.get('#password').type('password123');
      cy.get('#password-confirm').type('password123');
      cy.get('#firstName').type('E2E');
      cy.get('#lastName').type('Newuser');
      cy.get('#kc-form-buttons').find('input[type=submit]').click();
      cy.url().should('not.include', 'localhost:8180');
    });
    // First-login user with no org membership is auto-redirected to create one
    // (OrgRequiredRoute) — never lands directly on a project/job page (ADR-0049
    // Appendix §1 happy path); also implicitly proves UserSyncService.syncFromJwt
    // upserted a users row on first login, since OrgRequiredRoute's own check
    // requires a successful authenticated API call to get this far.
    cy.url().should('include', 'localhost:5173/onboarding');
    cy.deleteKeycloakUser(email);
  });

  it('registering an email already in use shows an inline duplicate-account error', () => {
    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', () => {
      cy.contains('a', 'Register').click();
      cy.get('#email').type('alice@example.com');
      cy.get('#password').type('password123');
      cy.get('#password-confirm').type('password123');
      cy.get('#firstName').type('Test');
      cy.get('#lastName').type('User');
      cy.get('#kc-form-buttons').find('input[type=submit]').click();
      cy.get('#input-error-email').should('be.visible').and('contain.text', 'Email already exists.');
    });
  });

  it('a password below the realm policy minimum shows an inline validation error', () => {
    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', () => {
      cy.contains('a', 'Register').click();
      cy.get('#email').type(`e2e-shortpw-${Date.now()}@example.com`);
      cy.get('#password').type('short');
      cy.get('#password-confirm').type('short');
      cy.get('#firstName').type('Test');
      cy.get('#lastName').type('User');
      cy.get('#kc-form-buttons').find('input[type=submit]').click();
      cy.get('#input-error-password').should('be.visible').and('contain.text', 'minimum length 8');
    });
  });

  it('required fields left blank block submission with inline field errors', () => {
    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', () => {
      cy.contains('a', 'Register').click();
      cy.get('#kc-form-buttons').find('input[type=submit]').click();
      cy.contains('Please specify email.').should('be.visible');
      cy.contains('Please specify password.').should('be.visible');
      // Blocked client-side — never left the registration page.
      cy.url().should('include', '/login-actions/registration');
    });
  });
});
