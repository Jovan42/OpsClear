// ADR-0049 Appendix §2 (Organisation Management — Settings). Uses cy.loginAs() per
// docs/dev/process/E2E.md. Every org used here gets a real subscription via
// cy.setUpOrgSubscription() immediately after creation, since /org/settings renders
// SubscriptionWall (not the settings form) until one exists.

import { API, uniqueEmail, uniqueSlug, tokenFor, userIdFor, createOrgWithSubscription, addMember } from '../../support/orgApi';

describe('Organisation Management — Settings', () => {
  it('OWNER updates the name and slug, reflected immediately with no reload', { tags: '@smoke' }, () => {
    const email = uniqueEmail('owner-update');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Acme Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/org/settings');

    const newSlug = uniqueSlug();
    cy.get('#org-name').clear();
    cy.get('#org-name').type('Updated Corp');
    cy.get('#org-slug').clear();
    cy.get('#org-slug').type(newSlug.toLowerCase());
    cy.contains('button', 'Save changes').click();
    cy.get('#org-name').should('have.value', 'Updated Corp');
    cy.get('#org-slug').should('have.value', newSlug);

    cy.deleteKeycloakUser(email);
  });

  // ADR-0049 edge case: "Re-saving an org's own current slug is not a collision (self
  // excluded from uniqueness check)."
  it('re-saving the name with the slug left unchanged succeeds (not a self-collision)', () => {
    const email = uniqueEmail('resave');
    cy.createKeycloakUser(email, 'E2E', 'Resave');
    createOrgWithSubscription(email, 'Resave Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/org/settings');

    cy.get('#org-name').clear();
    cy.get('#org-name').type('Resave Corp Renamed');
    cy.contains('button', 'Save changes').should('not.be.disabled').click();
    cy.get('#org-name').should('have.value', 'Resave Corp Renamed');
    cy.contains('An organisation with this slug already exists').should('not.exist');

    cy.deleteKeycloakUser(email);
  });

  it('OWNER soft-deletes the organisation, ending up back at onboarding (no org left)', () => {
    const email = uniqueEmail('owner-delete');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Doomed Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/org/settings');

    cy.contains('h2', 'Danger zone')
      .parents('section')
      .within(() => cy.contains('button', 'Delete organisation').click());
    // Scoped to the modal's own container (.z-50) — its confirm button has the exact
    // same text as the danger-zone trigger that opened it, so an unscoped lookup can
    // resolve to the (now overlay-hidden) trigger instead.
    cy.get('.z-50').should('be.visible').within(() => {
      cy.contains('Delete organisation?').should('be.visible');
      cy.contains('button', 'Delete organisation').click();
    });
    // handleDelete() itself navigates to /projects, but with no org left that route
    // is also gated by OrgRequiredRoute and immediately bounces onward — /onboarding
    // is where this genuinely settles, not /projects.
    cy.url({ timeout: 10000 }).should('include', '/onboarding');

    cy.deleteKeycloakUser(email);
  });

  (['ADMIN', 'MEMBER'] as const).forEach((role) => {
    it(`a non-owner (${role}) sees the settings form read-only with no danger zone`, () => {
      const ownerEmail = uniqueEmail(`ro-owner-${role}`);
      const memberEmail = uniqueEmail(`ro-member-${role}`);
      cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
      cy.createKeycloakUser(memberEmail, 'E2E', role);

      createOrgWithSubscription(ownerEmail, 'RO Corp', uniqueSlug()).then((orgId) => {
        userIdFor(memberEmail).then((userId) => addMember(orgId, ownerEmail, userId, role));
      });

      cy.loginAs(memberEmail);
      cy.visit('/org/settings');
      cy.get('#org-name').should('be.disabled');
      cy.get('#org-slug').should('be.disabled');
      cy.contains('button', 'Save changes').should('not.exist');
      cy.contains('Danger zone').should('not.exist');
      cy.contains('Delete organisation').should('not.exist');

      cy.deleteKeycloakUser(ownerEmail);
      cy.deleteKeycloakUser(memberEmail);
    });
  });

  it('a non-owner is blocked from PATCH/DELETE via direct API — 403', () => {
    const ownerEmail = uniqueEmail('api-owner');
    const memberEmail = uniqueEmail('api-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');

    createOrgWithSubscription(ownerEmail, 'API Corp', uniqueSlug()).then((orgId) => {
      userIdFor(memberEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'MEMBER'));

      tokenFor(memberEmail).then((token) => {
        cy.request({
          method: 'PATCH',
          url: `${API}/api/organisations/${orgId}`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'Hack', slug: uniqueSlug() },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(403));

        cy.request({
          method: 'DELETE',
          url: `${API}/api/organisations/${orgId}`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(403));
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  // ADR-0049 cross-tenant isolation edge case: Org A's OWNER cannot view/update/delete
  // Org B's settings via a guessed UUID.
  it("cannot view, update, or delete another organisation's settings via a guessed UUID", () => {
    const ownerAEmail = uniqueEmail('tenant-a');
    const ownerBEmail = uniqueEmail('tenant-b');
    cy.createKeycloakUser(ownerAEmail, 'E2E', 'TenantA');
    cy.createKeycloakUser(ownerBEmail, 'E2E', 'TenantB');

    createOrgWithSubscription(ownerAEmail, 'Tenant A Corp', uniqueSlug());
    createOrgWithSubscription(ownerBEmail, 'Tenant B Corp', uniqueSlug()).then((orgBId) => {
      tokenFor(ownerAEmail).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/organisations/${orgBId}`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(403));

        cy.request({
          method: 'PATCH',
          url: `${API}/api/organisations/${orgBId}`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'Hijacked', slug: uniqueSlug() },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(403));

        cy.request({
          method: 'DELETE',
          url: `${API}/api/organisations/${orgBId}`,
          headers: { Authorization: `Bearer ${token}` },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(403));
      });
    });

    cy.deleteKeycloakUser(ownerAEmail);
    cy.deleteKeycloakUser(ownerBEmail);
  });

  it('a slug conflict on save shows an inline error and does not update the org', () => {
    const takenEmail = uniqueEmail('conflict-taken');
    const ownerEmail = uniqueEmail('conflict-owner');
    cy.createKeycloakUser(takenEmail, 'E2E', 'Taken');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    const takenSlug = uniqueSlug();

    createOrgWithSubscription(takenEmail, 'Taken Corp', takenSlug);
    createOrgWithSubscription(ownerEmail, 'My Corp', uniqueSlug());

    cy.loginAs(ownerEmail);
    cy.visit('/org/settings');
    cy.get('#org-slug').clear();
    cy.get('#org-slug').type(takenSlug.toLowerCase());
    cy.contains('button', 'Save changes').should('not.be.disabled').click();
    cy.contains('An organisation with this slug already exists').should('be.visible');
    cy.get('#org-name').should('have.value', 'My Corp');

    cy.deleteKeycloakUser(takenEmail);
    cy.deleteKeycloakUser(ownerEmail);
  });

  it('blank name/slug on save shows inline validation errors and blocks the save', () => {
    const email = uniqueEmail('blank-update');
    cy.createKeycloakUser(email, 'E2E', 'Owner');
    createOrgWithSubscription(email, 'Blank Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/org/settings');
    cy.get('#org-name').clear();
    cy.get('#org-slug').clear();
    cy.contains('button', 'Save changes').click();
    cy.contains('Name is required').should('be.visible');
    cy.contains('Slug must be 2–3 letters').should('be.visible');

    cy.deleteKeycloakUser(email);
  });

  // ADR-0049: "GET /organisations/mine with no org membership → 204 No Content, not
  // 404 — verify frontend treats this as prompt org creation, not an error."
  it('a user with no org is prompted to create one, not shown an error', () => {
    const email = uniqueEmail('noorg');
    cy.createKeycloakUser(email, 'E2E', 'NoOrg');
    cy.loginAs(email);
    cy.visit('/org/settings');
    cy.url().should('include', '/onboarding');
    cy.contains('Create your organisation').should('be.visible');
    cy.deleteKeycloakUser(email);
  });
});
