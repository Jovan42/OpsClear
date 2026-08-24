import { E2E_TOKEN_KEY, E2E_REFRESH_TOKEN_KEY, E2E_ID_TOKEN_KEY } from '../../src/auth/e2eAuth';

const KEYCLOAK_URL = Cypress.env('KEYCLOAK_URL') ?? 'http://localhost:8180';
const KEYCLOAK_REALM = Cypress.env('KEYCLOAK_REALM') ?? 'opsclear';
const KEYCLOAK_CLIENT_ID = Cypress.env('KEYCLOAK_CLIENT_ID') ?? 'opsclear-frontend';

interface TokenResponse {
  access_token: string;
  refresh_token: string;
  id_token: string;
}

// Tokens fetched by the most recent cy.loginAs() this test — seeded into every
// subsequent cy.visit() via the override below, so a spec doesn't have to thread
// them through manually on each navigation within the same test.
let pendingTokens: TokenResponse | null = null;

/**
 * ADR-0049 §3: authenticates via Keycloak's token endpoint directly (Resource Owner
 * Password Credentials grant, same request shape scripts/seed.sh already uses to
 * verify the seeded users), bypassing the login UI — every feature-area spec except
 * the dedicated Auth Flows suite (JOB-208) should use this, not re-drive real login
 * on every run.
 *
 * Must be followed by a cy.visit() (before or after — order doesn't matter, the
 * override below seeds sessionStorage on every visit for the rest of the test) for
 * the token to actually reach the app; loginAs() alone only fetches it.
 */
Cypress.Commands.add('loginAs', (email: string, password = 'password123') => {
  cy.request({
    method: 'POST',
    url: `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
    form: true,
    body: {
      client_id: KEYCLOAK_CLIENT_ID,
      grant_type: 'password',
      username: email,
      password,
      scope: 'openid',
    },
  }).then(({ body }: { body: TokenResponse }) => {
    pendingTokens = body;
  });
});

// `visit`'s Cypress-provided types collapse to a single overload once passed through
// `Parameters<>` (used internally by `overwrite`'s typings), so the two call shapes
// (`visit(url, options)` vs `visit({ url, ...options })`) aren't distinguishable from
// this callback's own signature — normalized into one object form below instead of
// fighting that.
Cypress.Commands.overwrite(
  'visit',
  (originalFn: (options: Partial<Cypress.VisitOptions> & { url: string }) => Cypress.Chainable<Cypress.AUTWindow>,
    url: string | (Partial<Cypress.VisitOptions> & { url: string }),
    options: Partial<Cypress.VisitOptions> = {},
  ) => {
    const normalized: Partial<Cypress.VisitOptions> & { url: string } =
      typeof url === 'string' ? { ...options, url } : { ...url };

    if (!pendingTokens) return originalFn(normalized);

    const tokens = pendingTokens;
    const onBeforeLoad = normalized.onBeforeLoad;
    return originalFn({
      ...normalized,
      onBeforeLoad(win) {
        win.sessionStorage.setItem(E2E_TOKEN_KEY, tokens.access_token);
        win.sessionStorage.setItem(E2E_REFRESH_TOKEN_KEY, tokens.refresh_token);
        win.sessionStorage.setItem(E2E_ID_TOKEN_KEY, tokens.id_token);
        onBeforeLoad?.(win);
      },
    });
  },
);

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /** Logs in as a seeded demo user via Keycloak's token endpoint, bypassing the
       *  login UI. Password defaults to the standard seeded demo password. */
      loginAs(email: string, password?: string): Chainable<void>;
    }
  }
}
