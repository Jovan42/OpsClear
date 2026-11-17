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

// JOB-209: without this, pendingTokens set by one test's cy.loginAs() silently leaked
// into every later test's cy.visit() within the same spec file — including tests that
// never called loginAs() themselves, e.g. a real-UI-login test running after an
// API-driven one seeds a stale token into its cy.visit() calls, corrupting auth
// entirely (surfaced as AuthContext's initError screen). This is a module-level
// variable, not React state, so it doesn't reset on its own between tests the way
// component state would.
beforeEach(() => {
  pendingTokens = null;
});

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

/**
 * JOB-208: cleans up a real Keycloak user created by a registration spec, via the
 * Admin API — same admin-cli/master-realm ROPC grant `scripts/seed.sh` already uses
 * to manage seeded users, not something new to this file.
 */
Cypress.Commands.add('deleteKeycloakUser', (email: string) => {
  cy.request({
    method: 'POST',
    url: `${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`,
    form: true,
    body: {
      client_id: 'admin-cli',
      username: 'admin',
      password: 'admin',
      grant_type: 'password',
    },
  }).then(({ body }: { body: { access_token: string } }) => {
    const adminToken = body.access_token;
    cy.request({
      method: 'GET',
      url: `${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users`,
      qs: { email },
      headers: { Authorization: `Bearer ${adminToken}` },
    }).then(({ body: users }: { body: Array<{ id: string }> }) => {
      if (users.length === 0) return;
      cy.request({
        method: 'DELETE',
        url: `${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${users[0].id}`,
        headers: { Authorization: `Bearer ${adminToken}` },
      });
    });
  });
});

/**
 * JOB-209: creates a real, enabled, already-verified Keycloak user via the Admin API
 * (same shape scripts/seed.sh's create_kc_user uses) — for specs that need a
 * disposable user with a known-empty org state, since the 5 standard seed users'
 * org membership isn't predictable (other specs, and local manual testing, can leave
 * them already in an org). Pair with cy.deleteKeycloakUser() for cleanup.
 *
 * `emailVerified: false` (JOB-344) creates a user Keycloak sends to the verify-email page
 * at login, like an account registered before verification was turned on (ADR-0059).
 */
Cypress.Commands.add('createKeycloakUser', (
  email: string,
  firstName: string,
  lastName: string,
  password = 'password123',
  options: { emailVerified?: boolean } = {},
) => {
  cy.request({
    method: 'POST',
    url: `${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`,
    form: true,
    body: { client_id: 'admin-cli', username: 'admin', password: 'admin', grant_type: 'password' },
  }).then(({ body }: { body: { access_token: string } }) => {
    cy.request({
      method: 'POST',
      url: `${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users`,
      headers: { Authorization: `Bearer ${body.access_token}` },
      body: {
        username: email,
        email,
        firstName,
        lastName,
        enabled: true,
        emailVerified: options.emailVerified ?? true,
        credentials: [{ type: 'password', value: password, temporary: false }],
      },
    });
  });
});

/**
 * JOB-209: a freshly-created org has no subscription, which OrgRequiredRoute gates
 * behind SubscriptionWall — every non-billing org-management page is unreachable
 * until one exists. Calls the real, non-Paddle subscription endpoint
 * (PUT /organisations/{orgId}/subscription, exactly what "Save subscription" on that
 * wall itself calls) directly, the same way a real minimal-plan owner would, rather
 * than faking billing state — no external Paddle dependency either way, since this
 * endpoint never talks to Paddle itself. Must be called as the org's OWNER.
 */
Cypress.Commands.add('setUpOrgSubscription', (orgId: string, ownerToken: string) => {
  cy.request({
    method: 'GET',
    url: 'http://localhost:8080/api/subscriptions/catalog',
    headers: { Authorization: `Bearer ${ownerToken}` },
  }).then(({ body }: { body: { tiers: Array<{ id: string }> } }) => {
    cy.request({
      method: 'PUT',
      url: `http://localhost:8080/api/organisations/${orgId}/subscription`,
      headers: { Authorization: `Bearer ${ownerToken}` },
      body: { tierId: body.tiers[0].id, billingCycle: 'MONTHLY', addonIds: [] },
    });
  });
});

const MAILPIT_URL = Cypress.env('MAILPIT_URL') ?? 'http://localhost:8025';
// Keycloak and the backend send mail after the request returns, so a message can take a
// moment to show up in Mailpit.
const MAILPIT_POLL_ATTEMPTS = 20;
const MAILPIT_POLL_INTERVAL_MS = 1000;

interface MailpitSearchResponse {
  messages: Array<{ ID: string }>;
}

interface MailpitMessage {
  HTML: string;
}

/** ID of the newest Mailpit message to `to` (search results are newest first), polling
 *  until one arrives. */
function newestMailpitMessageId(to: string, attempt = 1): Cypress.Chainable<string> {
  return cy
    .request<MailpitSearchResponse>({
      url: `${MAILPIT_URL}/api/v1/search`,
      qs: { query: `to:${to}` },
      log: false,
    })
    .then(({ body }): Cypress.Chainable<string> => {
      if (body.messages.length > 0) return cy.wrap(body.messages[0].ID, { log: false });
      if (attempt >= MAILPIT_POLL_ATTEMPTS) {
        throw new Error(`No email to ${to} arrived in Mailpit (${MAILPIT_URL}) after ${attempt} attempts`);
      }
      // Mail is sent asynchronously; there's no request or element to wait on instead.
      // eslint-disable-next-line cypress/no-unnecessary-waiting
      cy.wait(MAILPIT_POLL_INTERVAL_MS, { log: false });
      return newestMailpitMessageId(to, attempt + 1);
    });
}

/**
 * JOB-344 (ADR-0059): returns the first link containing `pathFragment` in the newest email
 * Mailpit received for `to`. Dev and CI send all Keycloak and backend mail to Mailpit
 * (UI/API on localhost:8025). Use a unique recipient per test so an older message can't
 * be picked up.
 */
Cypress.Commands.add('mailpitLink', (to: string, pathFragment: string) => {
  Cypress.log({ name: 'mailpitLink', message: `${to} ${pathFragment}` });
  return newestMailpitMessageId(to).then((id) =>
    cy.request<MailpitMessage>({ url: `${MAILPIT_URL}/api/v1/message/${id}`, log: false }).then(({ body }) => {
      const links = [...body.HTML.matchAll(/href="([^"]+)"/g)].map(([, href]) => href.replaceAll('&amp;', '&'));
      const link = links.find((href) => href.includes(pathFragment));
      if (!link) {
        throw new Error(`The newest email to ${to} has no link containing "${pathFragment}"`);
      }
      return link;
    }),
  );
});

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /** Logs in as a seeded demo user via Keycloak's token endpoint, bypassing the
       *  login UI. Password defaults to the standard seeded demo password. */
      loginAs(email: string, password?: string): Chainable<void>;
      /** Deletes a Keycloak user by email via the Admin API — cleanup for specs that
       *  self-register a real, permanent user (JOB-208). No-op if not found. */
      deleteKeycloakUser(email: string): Chainable<void>;
      /** Creates a real, enabled Keycloak user via the Admin API — for specs needing
       *  a disposable user with predictable (empty) org state (JOB-209). */
      createKeycloakUser(
        email: string,
        firstName: string,
        lastName: string,
        password?: string,
        options?: { emailVerified?: boolean },
      ): Chainable<void>;
      /** First link containing `pathFragment` in the newest Mailpit email to `to`,
       *  polling until the email arrives (JOB-344). */
      mailpitLink(to: string, pathFragment: string): Chainable<string>;
      /** Gives an org a real (non-Paddle) subscription so its OWNER can get past
       *  SubscriptionWall onto the actual org-management pages (JOB-209). */
      setUpOrgSubscription(orgId: string, ownerToken: string): Chainable<void>;
    }
  }
}
