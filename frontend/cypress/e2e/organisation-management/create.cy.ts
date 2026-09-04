// ADR-0049 Appendix §2 (Organisation Management — Create). Uses cy.loginAs() per
// docs/dev/process/E2E.md — this area doesn't need real Keycloak UI login, unlike
// the dedicated Auth Flows suite. Every test uses a disposable Keycloak user via
// cy.createKeycloakUser()/cy.deleteKeycloakUser(): the 5 standard seed users' org
// membership isn't predictable (e.g. JOB-208's login.cy.ts gives testuser an org as
// a side effect of its own setup), and scripts/seed.sh itself creates no orgs at all.
// Slugs are generated fresh per test (not hardcoded) since they're never cleaned up
// after creation — org slugs stay reserved forever unless the org is later deleted,
// so a hardcoded slug would collide with itself on any re-run after a failure.
//
// Not covered: org-prefix immutability (ADR-0049 flagged this as a possible gap to
// verify) — there's no endpoint or UI anywhere that exposes a friendly-ID prefix for
// editing in the first place, so there's no surface to test "immutability" against.

import { API, uniqueEmail, uniqueSlug, tokenFor, createOrgAs } from '../../support/orgApi';

describe('Organisation Management — Create', () => {
  it('a user with no org creates one (slug normalized to uppercase) and lands on org settings', { tags: '@smoke' }, () => {
    const email = uniqueEmail('create');
    const slug = uniqueSlug();
    cy.createKeycloakUser(email, 'E2E', 'Creator');
    cy.loginAs(email);
    cy.visit('/onboarding');
    cy.get('#org-name').type('Acme Corp');
    cy.get('#org-slug').type(slug.toLowerCase());
    cy.contains('button', 'Create organisation').click();
    cy.url().should('include', '/org/settings');

    // A freshly-created org has no subscription yet, so /org/settings itself renders
    // SubscriptionWall at this point, not the settings form — give it one (the real,
    // non-Paddle endpoint) the same way a real owner would, then reload to see past
    // it and confirm the org was actually created with the right, normalized values.
    tokenFor(email).then((token) => {
      cy.request({ method: 'GET', url: `${API}/api/organisations/mine`, headers: { Authorization: `Bearer ${token}` } })
        .then(({ body }: { body: { id: string } }) => cy.setUpOrgSubscription(body.id, token));
    });
    cy.reload();
    cy.get('#org-name').should('have.value', 'Acme Corp');
    cy.get('#org-slug').should('have.value', slug);
    cy.deleteKeycloakUser(email);
  });

  it('typing non-letters into the slug field strips them and forces uppercase live', () => {
    const email = uniqueEmail('slugtype');
    cy.createKeycloakUser(email, 'E2E', 'SlugType');
    cy.loginAs(email);
    cy.visit('/onboarding');
    cy.get('#org-slug').type('ab1!c');
    cy.get('#org-slug').should('have.value', 'ABC');
    cy.deleteKeycloakUser(email);
  });

  it('blank name and slug show inline validation errors and block submission', () => {
    const email = uniqueEmail('blank');
    cy.createKeycloakUser(email, 'E2E', 'Blank');
    cy.loginAs(email);
    cy.visit('/onboarding');
    cy.contains('button', 'Create organisation').click();
    cy.contains('Name is required').should('be.visible');
    cy.contains('Slug must be 2–3 letters').should('be.visible');
    cy.url().should('include', '/onboarding');
    cy.deleteKeycloakUser(email);
  });

  it('a name over 100 characters shows an inline validation error', () => {
    const email = uniqueEmail('longname');
    cy.createKeycloakUser(email, 'E2E', 'LongName');
    cy.loginAs(email);
    cy.visit('/onboarding');
    cy.get('#org-name').type('A'.repeat(101));
    cy.get('#org-slug').type(uniqueSlug());
    cy.contains('button', 'Create organisation').click();
    cy.contains('Max 100 characters').should('be.visible');
    cy.deleteKeycloakUser(email);
  });

  it('a slug already taken (case-insensitive) shows an inline conflict error', () => {
    const takenEmail = uniqueEmail('taken-owner');
    const conflictEmail = uniqueEmail('taken-conflict');
    const slug = uniqueSlug();
    cy.createKeycloakUser(takenEmail, 'E2E', 'TakenOwner');
    cy.createKeycloakUser(conflictEmail, 'E2E', 'TakenConflict');

    // Not createOrgAs() here — this test needs `slug` to be the exact value that
    // ends up taken, since the UI attempt below deliberately reuses it to trigger the
    // conflict; a retry-on-collision helper could silently swap in a different slug.
    tokenFor(takenEmail).then((token) => {
      cy.request({
        method: 'POST',
        url: `${API}/api/organisations`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'Taken Corp', slug },
      });
    });

    cy.loginAs(conflictEmail);
    cy.visit('/onboarding');
    cy.get('#org-name').type('Conflict Corp');
    cy.get('#org-slug').type(slug.toLowerCase()); // lowercase — must still conflict case-insensitively
    cy.contains('button', 'Create organisation').click();
    cy.contains('An organisation with this slug already exists').should('be.visible');
    cy.url().should('include', '/onboarding');

    cy.deleteKeycloakUser(takenEmail);
    cy.deleteKeycloakUser(conflictEmail);
  });

  // ADR-0049 edge case: "confirm actual enforced behavior via direct API, not just UI
  // absence of the option." Was a real gap (JOB-241, fixed) — a second create()
  // silently succeeded, leaving the caller a member of two orgs.
  it('a user who already belongs to an organisation cannot create a second (verified via direct API)', () => {
    const email = uniqueEmail('second');
    cy.createKeycloakUser(email, 'E2E', 'Second');
    createOrgAs(email, 'First Corp', uniqueSlug());
    tokenFor(email).then((token) => {
      // Not createOrgAs() for this one — retrying past a 409 here would mask the
      // exact thing this test asserts on (that THIS specific request is rejected
      // for already belonging to an org, not for a slug collision).
      cy.request({
        method: 'POST',
        url: `${API}/api/organisations`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'Second Corp', slug: uniqueSlug() },
        failOnStatusCode: false,
      }).then((secondRes) => {
        expect(secondRes.status).to.eq(409);
      });
    });
    cy.deleteKeycloakUser(email);
  });

  // ADR-0049 edge case: whether a deleted org's slug becomes reusable. Was a real gap
  // (JOB-238, fixed) — the DB constraint and the app-level check disagreed, causing a
  // raw 500 instead of either a clean reuse or a clean 409.
  it("a deleted organisation's slug becomes reusable by a new org (verified via direct API)", () => {
    const firstEmail = uniqueEmail('reuse-first');
    const secondEmail = uniqueEmail('reuse-second');
    const slug = uniqueSlug();
    cy.createKeycloakUser(firstEmail, 'E2E', 'ReuseFirst');
    cy.createKeycloakUser(secondEmail, 'E2E', 'ReuseSecond');

    tokenFor(firstEmail).then((token) => {
      cy.request({
        method: 'POST',
        url: `${API}/api/organisations`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'Reused Corp', slug },
      }).then((createRes) => {
        cy.request({
          method: 'DELETE',
          url: `${API}/api/organisations/${createRes.body.id}`,
          headers: { Authorization: `Bearer ${token}` },
        });
      });
    });

    tokenFor(secondEmail).then((token) => {
      cy.request({
        method: 'POST',
        url: `${API}/api/organisations`,
        headers: { Authorization: `Bearer ${token}` },
        body: { name: 'New Owner Corp', slug },
      }).then((res) => {
        expect(res.status).to.eq(201);
      });
    });

    cy.deleteKeycloakUser(firstEmail);
    cy.deleteKeycloakUser(secondEmail);
  });
});
