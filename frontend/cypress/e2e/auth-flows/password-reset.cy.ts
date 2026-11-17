// ADR-0049 Appendix §1 (Auth Flows — Password Reset). Real Keycloak UI throughout.
// JOB-344 (ADR-0059): Keycloak sends real reset emails to Mailpit in dev and CI, so the
// happy path requests a reset, opens the link from the email, sets a new password, lands
// in the app and then logs in with the new password (the old one is rejected).
//
// The weak-password case reaches the same login-update-password.ftl screen a reset link
// lands on by giving a throwaway user the UPDATE_PASSWORD required action through the
// Keycloak Admin API, so it doesn't need an email round trip.

const KEYCLOAK = 'http://localhost:8180';
const REALM = 'opsclear';

function adminToken() {
  return cy
    .request({
      method: 'POST',
      url: `${KEYCLOAK}/realms/master/protocol/openid-connect/token`,
      form: true,
      body: { client_id: 'admin-cli', username: 'admin', password: 'admin', grant_type: 'password' },
    })
    .then(({ body }: { body: { access_token: string } }) => body.access_token);
}

/** Creates a throwaway, already-verified user that must set a new password on next
 *  login — the state a user lands in after following a reset link. */
function createUserRequiringPasswordUpdate(email: string, token: string) {
  return cy.request({
    method: 'POST',
    url: `${KEYCLOAK}/admin/realms/${REALM}/users`,
    headers: { Authorization: `Bearer ${token}` },
    body: {
      firstName: 'E2E',
      lastName: 'ResetUser',
      email,
      username: email,
      enabled: true,
      emailVerified: true,
      requiredActions: ['UPDATE_PASSWORD'],
      credentials: [{ type: 'password', value: 'password123', temporary: false }],
    },
  });
}

/** Password-grant login against the realm, the same request cy.loginAs() makes. */
function passwordLogin(email: string, password: string) {
  return cy.request({
    method: 'POST',
    url: `${KEYCLOAK}/realms/${REALM}/protocol/openid-connect/token`,
    form: true,
    failOnStatusCode: false,
    body: { client_id: 'opsclear-frontend', grant_type: 'password', username: email, password, scope: 'openid' },
  });
}

function logOut() {
  cy.visit('http://localhost:8180/realms/opsclear/protocol/openid-connect/logout?client_id=opsclear-frontend&post_logout_redirect_uri=http://localhost:5173');
  cy.location('pathname', { timeout: 10000 }).should('eq', '/');
}

describe('Password Reset', () => {
  beforeEach(() => {
    logOut();
  });

  it('requests a reset, opens the link from the email, sets a new password and can log in with it', { tags: '@smoke' }, () => {
    const email = `e2e-reset-${Date.now()}@example.com`;
    const newPassword = 'brand-new-password-123';
    cy.createKeycloakUser(email, 'E2E', 'ResetUser');

    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', { args: { email } }, ({ email }) => {
      cy.contains('a', 'Forgot Password?').click();
      cy.get('#username').type(email);
      cy.get('#kc-form-buttons').find('input[type=submit]').click();
      // Same generic message as for an unknown email (next test): no user enumeration.
      cy.contains('You should receive an email shortly with further instructions.').should('be.visible');
    });

    cy.mailpitLink(email, '/login-actions/action-token').then((link) => {
      cy.origin('http://localhost:8180', { args: { link, newPassword } }, ({ link, newPassword }) => {
        // Navigates the page itself, as clicking the link would.
        cy.window().then((win) => {
          win.location.href = link;
        });
        cy.get('#password-new').type(newPassword);
        cy.get('#password-confirm').type(newPassword);
        cy.get('#kc-form-buttons').find('input[type=submit]').click();
        cy.url().should('not.include', 'localhost:8180');
      });
    });
    cy.url({ timeout: 10000 }).should('include', 'localhost:5173');

    // Log in with the new password through Keycloak's token endpoint. A second UI login
    // would need a logout first, and with a live SSO session (and no id_token_hint) the
    // logout endpoint stops on a confirmation page.
    passwordLogin(email, newPassword).its('status').should('eq', 200);
    passwordLogin(email, 'password123').its('status').should('eq', 401);
    cy.deleteKeycloakUser(email);
  });

  it('an unknown email shows the generic "check your email" success message', () => {
    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', () => {
      cy.contains('a', 'Forgot Password?').click();
      cy.get('#username').type('nobody-here@example.com');
      cy.get('#kc-form-buttons').find('input[type=submit]').click();
      cy.contains('You should receive an email shortly with further instructions.').should('be.visible');
    });
  });

  it('update-password screen: a new password below the realm policy minimum shows an inline error', () => {
    const email = `e2e-reset-weak-${Date.now()}@example.com`;
    adminToken().then((token) => {
      createUserRequiringPasswordUpdate(email, token);
    });
    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', { args: { email } }, ({ email }) => {
      cy.get('#username').type(email);
      cy.get('#password').type('password123');
      cy.get('#kc-login').click();
      cy.get('#password-new').type('short');
      cy.get('#password-confirm').type('short');
      cy.get('#kc-form-buttons').find('input[type=submit]').click();
      cy.contains('Invalid password: minimum length 8.').should('be.visible');
      cy.url().should('include', '/login-actions/');
    });
    cy.deleteKeycloakUser(email);
  });
});
