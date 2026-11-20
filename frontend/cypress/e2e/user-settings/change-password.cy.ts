// JOB-404: Settings > Security > Change password opens Keycloak's own update-password page
// (the UPDATE_PASSWORD application-initiated action), so OpsClear never handles the password.
// Real Keycloak UI throughout, like the Auth Flows suite: the user signs in through the
// hosted login page first, so Keycloak has a fresh session and goes straight to the
// update-password form. Each test uses a throwaway user, so no seeded password changes.
//
// The new password is checked with a password-grant login (the request cy.loginAs() makes),
// as in auth-flows/password-reset.cy.ts: a second UI login would need a logout, and with a
// live session Keycloak's logout endpoint stops on a confirmation page.
//
// Not covered: the re-authentication prompt Keycloak shows when the sign-in is more than
// 5 minutes old (Keycloak's own behaviour, needs a real wait), and the `error` outcome
// (Keycloak only returns it when the action can't run, which the realm doesn't allow to set up).

import { KEYCLOAK, createOrgWithSubscription, uniqueEmail, uniqueSlug } from '../../support/orgApi';

const OLD_PASSWORD = 'password123';
const NEW_PASSWORD = 'changed-password-456';

/** Password-grant login against the realm; returns the response so a test can check the status. */
function passwordLogin(email: string, password: string) {
  return cy.request({
    method: 'POST',
    url: `${KEYCLOAK}/realms/opsclear/protocol/openid-connect/token`,
    form: true,
    failOnStatusCode: false,
    body: { client_id: 'opsclear-frontend', grant_type: 'password', username: email, password, scope: 'openid' },
  });
}

/** A throwaway user in its own subscribed org (Settings sits behind the org gate). */
function createUser(label: string) {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'E2E', 'Password', OLD_PASSWORD);
  createOrgWithSubscription(email, 'Password Corp', uniqueSlug());
  return cy.wrap(email);
}

/** Signs in through Keycloak's hosted login page and opens Settings. */
function signInAndOpenSettings(email: string) {
  cy.visit('/');
  cy.contains('Log in').click();
  cy.origin(KEYCLOAK, { args: { email, password: OLD_PASSWORD } }, ({ email, password }) => {
    cy.get('#username').type(email);
    cy.get('#password').type(password);
    cy.get('#kc-login').click();
  });
  cy.url().should('not.include', 'localhost:8180');
  cy.visit('/settings');
  cy.get('#security').should('contain.text', 'Security');
}

/** Back on Settings, with Keycloak's `kc_action_status` already removed from the URL. */
function assertBackOnSettings() {
  cy.location('pathname', { timeout: 10000 }).should('eq', '/settings');
  cy.location('search').should('not.include', 'kc_action_status');
  cy.location('hash').should('not.include', 'kc_action_status');
}

describe('Change password from Settings', () => {
  it('changes the password on Keycloak\'s page, confirms it on Settings, and the new password works', { tags: '@smoke' }, () => {
    createUser('pw-change').then((email) => {
      signInAndOpenSettings(email);

      cy.contains('button', 'Change password').click();
      cy.origin(KEYCLOAK, { args: { newPassword: NEW_PASSWORD } }, ({ newPassword }) => {
        cy.get('h1').should('contain.text', 'Update password');
        cy.get('#password-new').type(newPassword);
        cy.get('#password-confirm').type(newPassword);
        cy.get('#kc-form-buttons').find('input[type=submit]').click();
        cy.url().should('not.include', 'localhost:8180');
      });

      assertBackOnSettings();
      cy.get('[data-testid="password-change-status"]')
        .should('be.visible')
        .and('have.text', 'Your password has been changed.');

      passwordLogin(email, NEW_PASSWORD).its('status').should('eq', 200);
      passwordLogin(email, OLD_PASSWORD).its('status').should('eq', 401);

      // Shown once: a reload doesn't bring the message back.
      cy.reload();
      cy.get('#security').should('be.visible');
      cy.get('[data-testid="password-change-status"]').should('not.exist');

      cy.deleteKeycloakUser(email);
    });
  });

  it('cancelling on Keycloak\'s page returns to Settings and keeps the old password', () => {
    createUser('pw-cancel').then((email) => {
      signInAndOpenSettings(email);

      cy.contains('button', 'Change password').click();
      cy.origin(KEYCLOAK, () => {
        cy.get('#password-new').should('be.visible');
        cy.get('button[name="cancel-aia"]').click();
        cy.url().should('not.include', 'localhost:8180');
      });

      assertBackOnSettings();
      cy.get('[data-testid="password-change-status"]')
        .should('be.visible')
        .and('have.text', 'Password change cancelled. Your password stays the same.');

      passwordLogin(email, OLD_PASSWORD).its('status').should('eq', 200);

      cy.deleteKeycloakUser(email);
    });
  });
});
