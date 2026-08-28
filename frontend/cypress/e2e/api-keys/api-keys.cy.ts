// ADR-0049 Appendix §3 (API Keys). Uses cy.loginAs() per docs/dev/process/E2E.md.
// API keys are personal (scoped to the caller, not the org) but gated by the org's
// API_KEYS add-on — every test's user needs an org with full (internal) access via
// createOrgWithFullAccess(), since real Paddle billing is out of scope here and this
// area's actual subject isn't billing (ADR-0049's own fixture-over-live-dependency
// principle).

import { API, uniqueEmail, uniqueSlug, tokenFor, createOrgWithFullAccess, createOrgWithSubscription } from '../../support/orgApi';

function createKeyViaApi(email: string, name: string, expiresAt?: string) {
  return tokenFor(email).then((token) =>
    cy
      .request({
        method: 'POST',
        url: `${API}/api/user/api-keys`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name, expiresAt },
      })
      .then((res) => res.body as { id: string; key: string; keyPrefix: string }),
  );
}

function backdateCreatedAt(keyId: string, hoursAgo: number) {
  return cy.task('queryDb', {
    sql: "UPDATE api_keys SET created_at = NOW() - ($2 || ' hours')::interval WHERE id = $1",
    params: [keyId, String(hoursAgo)],
  });
}

describe('API Keys', () => {
  it('creates a named key: raw key shown once with a copy button, listed with prefix/name/created/never-used', () => {
    const email = uniqueEmail('create');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Create Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('button', '+ New API Key').click();
    cy.get('input[placeholder*="deploy script"]').type('Deploy script');
    // Scoped to the modal's own container — an unscoped `cy.contains('button',
    // 'Create')` can ambiguously match the header's user-menu toggle too, since it
    // renders the caller's display name and nothing stops that name from containing
    // "Create" (or any other button's label) as a substring.
    cy.get('.z-50').should('be.visible').within(() => cy.contains('button', 'Create').click());

    cy.contains('Copy your key now — it will not be shown again.').should('be.visible');
    cy.get('code').invoke('text').should('match', /^opck_/).as('rawKey');
    cy.contains('button', 'Copy').click();
    cy.contains('button', 'Copied!').should('be.visible');
    cy.contains("I've copied it, close").click();

    cy.contains('Deploy script').should('be.visible');
    cy.contains('Never used').should('be.visible');
    cy.get('@rawKey').then((rawKey) => {
      const prefix = (rawKey as unknown as string).slice(0, 12);
      cy.contains(prefix).should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('the raw key never reappears after closing and reopening the create modal', () => {
    const email = uniqueEmail('reopen');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Reopen Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('button', '+ New API Key').click();
    cy.get('input[placeholder*="deploy script"]').type('First key');
    cy.get('.z-50').should('be.visible').within(() => cy.contains('button', 'Create').click());
    cy.get('code').should('be.visible');
    cy.contains("I've copied it, close").click();

    cy.contains('button', '+ New API Key').click();
    cy.get('code').should('not.exist');
    cy.get('input[placeholder*="deploy script"]').should('have.value', '');

    cy.deleteKeycloakUser(email);
  });

  it('listing a key never returns the hash or raw key, only the prefix', () => {
    const email = uniqueEmail('shape');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Shape Corp', uniqueSlug());
    createKeyViaApi(email, 'Shape key');

    tokenFor(email).then((token) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/user/api-keys`,
        headers: { Authorization: `Bearer ${token}` },
      }).then((res) => {
        const key = (res.body as Array<Record<string, unknown>>)[0];
        expect(key).to.have.property('keyPrefix');
        expect(key).to.not.have.property('key');
        expect(key).to.not.have.property('keyHash');
        expect(key).to.not.have.property('key_hash');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('revoking a key excludes it from the active list and a subsequent use of it is rejected', () => {
    const email = uniqueEmail('revoke');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Revoke Corp', uniqueSlug());

    createKeyViaApi(email, 'To revoke').then(({ id, key }) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'DELETE',
          url: `${API}/api/user/api-keys/${id}`,
          headers: { Authorization: `Bearer ${token}` },
        }).its('status').should('eq', 204);

        cy.request({
          method: 'GET',
          url: `${API}/api/user/api-keys`,
          headers: { Authorization: `Bearer ${token}` },
        }).then((res) => {
          const ids = (res.body as Array<{ id: string }>).map((k) => k.id);
          expect(ids).to.not.include(id);
        });

        // The revoked raw key is now rejected as an auth credential entirely.
        cy.request({
          method: 'GET',
          url: `${API}/api/organisations/mine`,
          headers: { 'X-Api-Key': key },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(401));
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('a key actively used updates last_used_at, reflected on the next list fetch', () => {
    const email = uniqueEmail('lastused');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'LastUsed Corp', uniqueSlug());

    createKeyViaApi(email, 'Active key').then(({ id, key }) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/user/api-keys`,
          headers: { Authorization: `Bearer ${token}` },
        }).then((res) => {
          const before = (res.body as Array<{ id: string; lastUsedAt: string | null }>).find((k) => k.id === id);
          expect(before?.lastUsedAt).to.equal(null);
        });

        // Use the raw key as a real auth credential against a real endpoint.
        cy.request({
          method: 'GET',
          url: `${API}/api/organisations/mine`,
          headers: { 'X-Api-Key': key },
        });

        cy.request({
          method: 'GET',
          url: `${API}/api/user/api-keys`,
          headers: { Authorization: `Bearer ${token}` },
        }).then((res) => {
          const after = (res.body as Array<{ id: string; lastUsedAt: string | null }>).find((k) => k.id === id);
          expect(after?.lastUsedAt).to.not.equal(null);
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('blank name and a name over 100 characters are rejected with 400 (client already blocks both in the UI)', () => {
    const email = uniqueEmail('validation');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Validation Corp', uniqueSlug());

    tokenFor(email).then((token) => {
      cy.request({
        method: 'POST',
        url: `${API}/api/user/api-keys`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: '' },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(400));

      cy.request({
        method: 'POST',
        url: `${API}/api/user/api-keys`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'A'.repeat(101) },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(400));
    });

    cy.deleteKeycloakUser(email);
  });

  it('revoking a key not owned by the caller 404s (cross-user isolation)', () => {
    const ownerEmail = uniqueEmail('cross-owner');
    const otherEmail = uniqueEmail('cross-other');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(otherEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Cross Owner Corp', uniqueSlug());
    createOrgWithFullAccess(otherEmail, 'Cross Other Corp', uniqueSlug());

    createKeyViaApi(ownerEmail, "Owner's key").then(({ id }) => {
      tokenFor(otherEmail).then((token) => {
        cy.request({
          method: 'DELETE',
          url: `${API}/api/user/api-keys/${id}`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(404));
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(otherEmail);
  });

  // ADR-0049: "Revoking an already-revoked key → 404, not idempotent-204 — confirm
  // intentional."
  it('revoking an already-revoked key 404s — not idempotent', () => {
    const email = uniqueEmail('doublerevoke');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'DoubleRevoke Corp', uniqueSlug());

    createKeyViaApi(email, 'Revoke twice').then(({ id }) => {
      tokenFor(email).then((token) => {
        cy.request({
          method: 'DELETE',
          url: `${API}/api/user/api-keys/${id}`,
          headers: { Authorization: `Bearer ${token}` },
        }).its('status').should('eq', 204);

        cy.request({
          method: 'DELETE',
          url: `${API}/api/user/api-keys/${id}`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(404));
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('every API key endpoint is blocked without the API_KEYS add-on', () => {
    const email = uniqueEmail('noaddon');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    // A real (non-internal, no add-ons selected) subscription — enough to pass
    // SubscriptionWall, not enough to unlock API_KEYS specifically.
    createOrgWithSubscription(email, 'NoAddon Corp', uniqueSlug());

    tokenFor(email).then((token) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/user/api-keys`,
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(403));

      cy.request({
        method: 'POST',
        url: `${API}/api/user/api-keys`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'Blocked' },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(403));
    });

    cy.loginAs(email);
    cy.visit('/settings');
    cy.contains('API Keys').should('not.exist');
    cy.contains('+ New API Key').should('not.exist');

    cy.deleteKeycloakUser(email);
  });

  // ADR-0049 expected this to be a documented gap ("expiresAt is stored but
  // explicitly not enforced in the auth filter, ADR-0025 V1"). Confirmed stale
  // against the current code: ApiKeyModel.isActive() checks !isExpired() as well as
  // !isRevoked(), and ApiKeyAuthFilter rejects any key that isn't isActive() —
  // expiry genuinely is enforced today. Asserting the actual (correct, secure)
  // current behavior rather than the ADR's outdated expectation.
  it('an expired key is rejected — expiry is enforced', () => {
    const email = uniqueEmail('expiry');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Expiry Corp', uniqueSlug());

    const pastExpiry = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
    createKeyViaApi(email, 'Expired key', pastExpiry).then(({ key }) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/organisations/mine`,
        headers: { 'X-Api-Key': key },
        failOnStatusCode: false,
      }).its('status').should('eq', 401);
    });

    cy.deleteKeycloakUser(email);
  });

  // ADR-0049 edge case: "Unused for 90+ days" badge boundary — exactly at, just
  // under, and just over. No API backdates created_at, so this uses the queryDb task
  // directly (cypress.config.ts) — the badge itself is computed client-side from
  // createdAt/lastUsedAt vs Date.now(), so backdating the stored value is enough.
  // Backdating in hours (not whole days) so "at the boundary" can sit a few hours on
  // the safe side of exactly 90*24 — a razor-exact backdate would flip to "over" by
  // the time the page actually loads and evaluates it, given the real (if small)
  // delay between the DB write here and the frontend's Date.now() comparison.
  it('the "unused 90+ days" badge respects the boundary: not at 90 days, shown just past it', () => {
    const email = uniqueEmail('boundary');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Boundary Corp', uniqueSlug());

    createKeyViaApi(email, 'Under 90').then(({ id }) => backdateCreatedAt(id, 89 * 24));
    createKeyViaApi(email, 'At 90').then(({ id }) => backdateCreatedAt(id, 90 * 24 - 6));
    createKeyViaApi(email, 'Over 90').then(({ id }) => backdateCreatedAt(id, 91 * 24));

    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('div', 'Under 90').should('be.visible').should('not.contain.text', 'Unused 90+ days');
    cy.contains('div', 'At 90').should('be.visible').should('not.contain.text', 'Unused 90+ days');
    cy.contains('div', 'Over 90').should('be.visible').should('contain.text', 'Unused 90+ days');

    cy.deleteKeycloakUser(email);
  });
});
