// ADR-0049 Appendix §1 (Auth Flows — Login). Real Keycloak UI throughout — this is the
// one area where UI-driven auth is the point of the test (ADR-0049 §3). Deliberately
// no cy.loginAs() anywhere in this file: mixing it with the real logout-endpoint visit
// below (needed to guarantee a fresh unauthenticated start before each test) left
// cy.loginAs()'s token request 400ing on the next call — real UI login throughout
// avoids that interaction entirely.

function submitLoginForm(email: string, password: string) {
  cy.contains('Log in').click();
  cy.origin('http://localhost:8180', { args: { email, password } }, ({ email, password }) => {
    cy.get('#username').type(email);
    cy.get('#password').type(password);
    cy.get('#kc-login').click();
  });
}

/** For the happy-path cases — waits for Keycloak's post-login redirect to actually
 *  leave its origin before returning, so the next command doesn't race an in-flight
 *  cross-origin redirect. Not used for the failure-case tests, which correctly stay
 *  on Keycloak's own error page. */
function loginViaUi(email: string, password: string) {
  cy.visit('/');
  submitLoginForm(email, password);
  cy.url().should('not.include', 'localhost:8180');
}

describe('Login', () => {
  // A real UI-driven login sets a genuine Keycloak SSO session cookie on Keycloak's
  // own origin, which Cypress's default test isolation doesn't reach — without this,
  // a previous test's real login silently carries into the next test's check-sso.
  beforeEach(() => {
    cy.visit('http://localhost:8180/realms/opsclear/protocol/openid-connect/logout?client_id=opsclear-frontend&post_logout_redirect_uri=http://localhost:5173');
    // Wait for the logout's own redirect back to `/` to fully settle before a test
    // issues its own cy.visit() — otherwise the two navigations race and the logout
    // redirect can win, landing a test's cy.visit('/org/settings') back on `/`.
    cy.location('pathname', { timeout: 10000 }).should('eq', '/');
  });

  it('logs in a seeded user and redirects back to the app authenticated', () => {
    loginViaUi('testuser@example.com', 'password123');
    cy.url().should('include', 'localhost:5173');
  });

  it('session persists across a page reload, no re-login prompt', () => {
    loginViaUi('testuser@example.com', 'password123');
    cy.reload();
    // Still on the app, never bounced back to Keycloak's hosted page.
    cy.url().should('include', 'localhost:5173');
    cy.contains('OpsClear').should('be.visible');
  });

  it('logout clears the session; a protected route afterward redirects to login again', () => {
    loginViaUi('testuser@example.com', 'password123');
    cy.visit('/projects');
    // Wait for the app shell to actually be settled before opening the user menu —
    // clicking the toggle immediately after cy.visit() was occasionally flaky in CI
    // (the button exists but the click doesn't register while the page is still
    // finishing its initial render), causing "Sign out" to never appear.
    cy.contains('OpsClear').should('be.visible');
    cy.get('[aria-haspopup="true"]').click();
    // Self-healing retry: if the menu didn't actually open (same underlying
    // flakiness), one more click reliably does.
    cy.get('body').then(($body) => {
      if (!$body.text().includes('Sign out')) {
        cy.get('[aria-haspopup="true"]').click();
      }
    });
    cy.contains('Sign out').click();
    cy.visit('/projects');
    cy.url().should('include', 'localhost:5173/');
    cy.url().should('not.include', '/projects');
  });

  it('wrong password shows a generic error and stays on the login page', () => {
    cy.visit('/');
    submitLoginForm('alice@example.com', 'wrong-password');
    cy.origin('http://localhost:8180', () => {
      cy.get('#input-error').should('be.visible').and('contain.text', 'Invalid username or password.');
      cy.url().should('include', '/realms/opsclear');
    });
  });

  it('a non-existent account shows the same generic error — no user enumeration', () => {
    cy.visit('/');
    submitLoginForm('nobody-here@example.com', 'whatever123');
    cy.origin('http://localhost:8180', () => {
      cy.get('#input-error').should('be.visible').and('contain.text', 'Invalid username or password.');
    });
  });

  // ADR-0049 Appendix §1: a deep link visited while unauthenticated should land back
  // on that exact path after login, not a generic home page. Was broken (JOB-237,
  // fixed in PR #417) — RequireAuth now saves the intended path to sessionStorage
  // before bouncing through Keycloak's real login, and LandingPage reads it back.
  it('deep-link path is restored after login', () => {
    cy.visit('/org/settings');
    submitLoginForm('testuser@example.com', 'password123');
    cy.url().should('include', 'localhost:5173/org/settings');
  });

  // Deferred: brute-force lockout screen / unbranded-theme fallback (ADR-0049
  // Appendix §1 edge case). Confirmed the realm has bruteForceProtected: true,
  // failureFactor: 5, but 6 rapid failed attempts against a real user in this
  // environment still returned the standard "Invalid username or password." error,
  // not a lockout-specific page/message - the actual lockout trigger/message text
  // needs further investigation (timing, whether it's IP- or user-scoped, etc.)
  // before this can be asserted on reliably. Locking a shared demo user's account to
  // investigate further would also strand it for other specs relying on
  // cy.loginAs() - left for a follow-up rather than guessing at an assertion.
});
