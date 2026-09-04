// ADR-0049 Appendix §1 (Auth Flows — Session). Realm settings (confirmed via Admin
// API): accessTokenLifespan=300s, ssoSessionIdleTimeout=1800s (30min).
//
// Per the user's decision on this job: the silent-refresh case is worth testing for
// real, over a real ~5 minute wait, rather than mocking Keycloak's token endpoint —
// mocking would only prove the app calls updateToken(), not that a real expired
// access token actually gets silently replaced. The 30-minute idle-timeout case is
// deliberately NOT covered here (also the user's decision) — a real 30+ minute test
// is impractical for a suite that runs on every PR, and there's no reliable way to
// fast-forward Keycloak's own session clock from the browser.
//
// Neither test here is tagged @smoke (JOB-258): this file's real 5+ minute wait was,
// until this job, exactly the kind of thing silently blowing up e2e-smoke's runtime
// on every PR (it ran the whole untagged suite). auth-flows/login.cy.ts's tagged
// test already covers the "login works" happy path for smoke purposes.

describe('Session', () => {
  beforeEach(() => {
    cy.visit('http://localhost:8180/realms/opsclear/protocol/openid-connect/logout?client_id=opsclear-frontend&post_logout_redirect_uri=http://localhost:5173');
    cy.location('pathname', { timeout: 10000 }).should('eq', '/');
  });

  // "Keycloak unreachable on boot" doesn't actually reach AuthContext's initError
  // fallback: the app's check-sso config has no `silentCheckSsoRedirectUri`, so
  // keycloak-js's initial unauthenticated check is a REAL top-level browser
  // navigation to Keycloak's /auth endpoint with prompt=none (confirmed by
  // intercepting and logging every request keycloak-js makes on a fresh
  // unauthenticated load — there's exactly one, and it's a full navigation, not a
  // fetch/XHR). If Keycloak is genuinely unreachable at that point, the browser's own
  // native connection-error page shows — there's no app JS running yet to catch it,
  // so no in-app fallback is possible for that specific moment.
  // initError IS reachable, and does show this fallback correctly, for the failure
  // that can actually happen through a catchable fetch/XHR: the token endpoint POST
  // that exchanges Keycloak's auth code for tokens after a user has already
  // authenticated on Keycloak's real hosted page. Verified below.
  it('Keycloak becoming unreachable during the post-login token exchange shows a retry screen', () => {
    cy.visit('/');
    cy.intercept('http://localhost:8180/realms/opsclear/protocol/openid-connect/token', { forceNetworkError: true });
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', () => {
      cy.get('#username').type('testuser@example.com');
      cy.get('#password').type('password123');
      cy.get('#kc-login').click();
    });
    cy.contains('Authentication service is temporarily unavailable.', { timeout: 10000 }).should('be.visible');
    cy.contains('button', 'Try again').should('be.visible');
  });

  it('an access token nearing/past its 5-minute expiry is silently refreshed, no re-login prompt', () => {
    cy.visit('/');
    cy.contains('Log in').click();
    cy.origin('http://localhost:8180', () => {
      cy.get('#username').type('testuser@example.com');
      cy.get('#password').type('password123');
      cy.get('#kc-login').click();
    });
    cy.url({ timeout: 10000 }).should('not.include', 'localhost:8180');
    cy.visit('/projects');
    cy.contains('OpsClear').should('be.visible');

    // Real wait past the realm's 300s access token lifespan, not a mocked clock —
    // the point of this test is proving a genuinely expired token gets replaced.
    // eslint-disable-next-line cypress/no-unnecessary-waiting
    cy.wait(310_000);

    // Any API call after this point goes through apiClient's request interceptor
    // (updateToken(30)), which must silently refresh before the request goes out.
    cy.visit('/projects');
    cy.url().should('include', 'localhost:5173/projects');
    cy.url().should('not.include', 'localhost:8180');
    cy.contains('OpsClear').should('be.visible');
  });
});
