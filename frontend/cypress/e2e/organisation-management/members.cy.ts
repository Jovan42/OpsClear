// ADR-0049 Appendix §2 (Organisation Management — Members). Uses cy.loginAs() per
// docs/dev/process/E2E.md.

import { API, uniqueEmail, uniqueSlug, tokenFor, userIdFor, createOrgWithSubscription, addMember } from '../../support/orgApi';

describe('Organisation Management — Members', () => {
  // ADR-0049 happy path: "OWNER/ADMIN adds an existing user (found via search) as
  // MEMBER/ADMIN". Was a real gap (JOB-244, fixed) — the search query was scoped to
  // the caller's own org, so it could only ever surface a user who ALREADY belonged
  // to your org, never someone genuinely new.
  it('OWNER adds a genuinely new user via search, then promotes them to ADMIN', () => {
    const ownerEmail = uniqueEmail('add-owner');
    const targetEmail = uniqueEmail('add-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'Findme', 'Target');
    userIdFor(targetEmail); // sync the target into the backend users table before searching for them

    createOrgWithSubscription(ownerEmail, 'Add Corp', uniqueSlug());
    cy.loginAs(ownerEmail);
    cy.visit('/org/members');

    cy.get('input[placeholder="Search by email…"]').type(targetEmail.split('@')[0]);
    cy.contains('li', targetEmail, { timeout: 10000 }).click();
    cy.contains('button', 'Add').click();
    cy.contains('td', targetEmail).should('be.visible');

    // Promote MEMBER -> ADMIN via the role <select> on their row — instant onChange,
    // no confirmation modal.
    cy.contains('tr', targetEmail).find('select').select('Admin');
    cy.contains('tr', targetEmail).find('select').should('have.value', 'ADMIN');

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  it('OWNER adds a member via direct API, then promotes them to ADMIN via the UI', () => {
    const ownerEmail = uniqueEmail('add-owner');
    const targetEmail = uniqueEmail('add-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgWithSubscription(ownerEmail, 'Add Corp', uniqueSlug()).then((orgId) => {
      userIdFor(targetEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'MEMBER'));
    });

    cy.loginAs(ownerEmail);
    cy.visit('/org/members');
    cy.contains('td', targetEmail).should('be.visible');

    // Promote MEMBER -> ADMIN via the role <select> on their row — instant onChange,
    // no confirmation modal.
    cy.contains('tr', targetEmail).find('select').select('Admin');
    cy.contains('tr', targetEmail).find('select').should('have.value', 'ADMIN');

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  it('an already-added member shows disabled with "(already a member)" in search results', () => {
    const ownerEmail = uniqueEmail('dup-owner');
    const targetEmail = uniqueEmail('dup-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'Dup', 'Target');

    createOrgWithSubscription(ownerEmail, 'Dup Corp', uniqueSlug()).then((orgId) => {
      userIdFor(targetEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'MEMBER'));
    });

    cy.loginAs(ownerEmail);
    cy.visit('/org/members');
    cy.get('input[placeholder="Search by email…"]').type(targetEmail.split('@')[0]);
    cy.contains('li', targetEmail, { timeout: 10000 }).should('contain.text', '(already a member)');
    cy.contains('li', targetEmail).find('button').should('be.disabled');

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  it('OWNER removes a non-owner member via the confirm modal', () => {
    const ownerEmail = uniqueEmail('remove-owner');
    const targetEmail = uniqueEmail('remove-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgWithSubscription(ownerEmail, 'Remove Corp', uniqueSlug()).then((orgId) => {
      userIdFor(targetEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'MEMBER'));
    });

    cy.loginAs(ownerEmail);
    cy.visit('/org/members');
    cy.contains('tr', targetEmail).contains('Remove').click();
    cy.get('.z-50').should('be.visible').within(() => {
      cy.contains('Remove member?').should('be.visible');
      cy.contains('button', 'Remove').click();
    });
    cy.contains('td', targetEmail).should('not.exist');

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  it('a plain MEMBER sees the list read-only — no add form, no role select, no remove link', () => {
    const ownerEmail = uniqueEmail('ro-owner');
    const memberEmail = uniqueEmail('ro-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');

    createOrgWithSubscription(ownerEmail, 'RO Corp', uniqueSlug()).then((orgId) => {
      userIdFor(memberEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'MEMBER'));
    });

    cy.loginAs(memberEmail);
    cy.visit('/org/members');
    cy.get('input[placeholder="Search by email…"]').should('not.exist');
    cy.get('select').should('not.exist');
    cy.contains('Remove').should('not.exist');
    cy.contains('td', ownerEmail).should('be.visible');
    cy.contains('td', memberEmail).should('be.visible');

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it("the OWNER's own row never shows a role select or remove link, for an ADMIN viewer either", () => {
    const ownerEmail = uniqueEmail('ownerrow-owner');
    const adminEmail = uniqueEmail('ownerrow-admin');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Admin');

    createOrgWithSubscription(ownerEmail, 'OwnerRow Corp', uniqueSlug()).then((orgId) => {
      userIdFor(adminEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'ADMIN'));
    });

    cy.loginAs(adminEmail);
    cy.visit('/org/members');
    cy.contains('tr', ownerEmail).find('select').should('not.exist');
    cy.contains('tr', ownerEmail).contains('Remove').should('not.exist');
    // ADMIN is not OWNER, so even a non-owner member's role shows read-only too.
    cy.contains('tr', adminEmail).find('select').should('not.exist');

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
  });

  it('a plain MEMBER cannot add or remove members via direct API — 403', () => {
    const ownerEmail = uniqueEmail('perm-owner');
    const memberEmail = uniqueEmail('perm-member');
    const targetEmail = uniqueEmail('perm-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgWithSubscription(ownerEmail, 'Perm Corp', uniqueSlug()).then((orgId) => {
      userIdFor(memberEmail).then((memberId) => addMember(orgId, ownerEmail, memberId, 'MEMBER'));
      userIdFor(targetEmail).then((targetId) => {
        tokenFor(memberEmail).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/organisations/${orgId}/members`,
            headers: { Authorization: `Bearer ${token}` },
            body: { userId: targetId, role: 'MEMBER' },
            failOnStatusCode: false,
          }).then((res) => expect(res.status).to.eq(403));
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  it('an ADMIN cannot change a member\'s role via direct API (OWNER-only) — 403', () => {
    const ownerEmail = uniqueEmail('roleperm-owner');
    const adminEmail = uniqueEmail('roleperm-admin');
    const memberEmail = uniqueEmail('roleperm-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Admin');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');

    createOrgWithSubscription(ownerEmail, 'RolePerm Corp', uniqueSlug()).then((orgId) => {
      userIdFor(adminEmail).then((adminId) => addMember(orgId, ownerEmail, adminId, 'ADMIN'));
      userIdFor(memberEmail).then((memberId) => {
        addMember(orgId, ownerEmail, memberId, 'MEMBER');
        tokenFor(adminEmail).then((token) => {
          cy.request({
            method: 'PATCH',
            url: `${API}/api/organisations/${orgId}/members/${memberId}`,
            headers: { Authorization: `Bearer ${token}` },
            body: { role: 'ADMIN' },
            failOnStatusCode: false,
          }).then((res) => expect(res.status).to.eq(403));
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('cannot add a member with role OWNER, cannot change a member TO owner, cannot change/remove the OWNER — all 403 via direct API', () => {
    const ownerEmail = uniqueEmail('ownerguard-owner');
    const targetEmail = uniqueEmail('ownerguard-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgWithSubscription(ownerEmail, 'OwnerGuard Corp', uniqueSlug()).then((orgId) => {
      userIdFor(targetEmail).then((targetId) => {
        tokenFor(ownerEmail).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/organisations/${orgId}/members`,
            headers: { Authorization: `Bearer ${token}` },
            body: { userId: targetId, role: 'OWNER' },
            failOnStatusCode: false,
          }).then((res) => expect(res.status).to.eq(403));

          // ADR-0049: "Adding role: OWNER → 403 ... (same on role-change path)" —
          // promoting an EXISTING non-owner member to OWNER must be blocked too, not
          // just blocking it at add-time.
          addMember(orgId, ownerEmail, targetId, 'MEMBER');
          cy.request({
            method: 'PATCH',
            url: `${API}/api/organisations/${orgId}/members/${targetId}`,
            headers: { Authorization: `Bearer ${token}` },
            body: { role: 'OWNER' },
            failOnStatusCode: false,
          }).then((res) => expect(res.status).to.eq(403));

          userIdFor(ownerEmail).then((ownerId) => {
            cy.request({
              method: 'PATCH',
              url: `${API}/api/organisations/${orgId}/members/${ownerId}`,
              headers: { Authorization: `Bearer ${token}` },
              body: { role: 'ADMIN' },
              failOnStatusCode: false,
            }).then((res) => expect(res.status).to.eq(403));

            cy.request({
              method: 'DELETE',
              url: `${API}/api/organisations/${orgId}/members/${ownerId}`,
              headers: { Authorization: `Bearer ${token}` },
              failOnStatusCode: false,
            }).then((res) => expect(res.status).to.eq(403));
          });
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  it('adding a nonexistent userId 404s; adding an existing member again 409s', () => {
    const ownerEmail = uniqueEmail('notfound-owner');
    const memberEmail = uniqueEmail('notfound-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');

    createOrgWithSubscription(ownerEmail, 'NotFound Corp', uniqueSlug()).then((orgId) => {
      tokenFor(ownerEmail).then((token) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/organisations/${orgId}/members`,
          headers: { Authorization: `Bearer ${token}` },
          body: { userId: '00000000-0000-0000-0000-000000000000', role: 'MEMBER' },
          failOnStatusCode: false,
        }).then((res) => expect(res.status).to.eq(404));

        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          cy.request({
            method: 'POST',
            url: `${API}/api/organisations/${orgId}/members`,
            headers: { Authorization: `Bearer ${token}` },
            body: { userId: memberId, role: 'MEMBER' },
            failOnStatusCode: false,
          }).then((res) => expect(res.status).to.eq(409));
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  // ADR-0049 originally expected search results scoped away from other orgs
  // entirely. JOB-244 (fixed): that scoping was actually a bug — it was implemented
  // as "only members of the CALLER's own org," which made it impossible to ever find
  // a genuinely new candidate to invite (anyone findable was, by definition, already
  // a member). Search is unscoped by design now — a user genuinely in a different
  // org is a legitimate, findable result, same as any other registered user; the
  // add-member picker's own client-side check is what prevents adding someone
  // who's already a member of a DIFFERENT org (they'd need to leave it first).
  it("user search finds a user from a different organisation too (unscoped by design, JOB-244)", () => {
    const orgAOwnerEmail = uniqueEmail('isolation-a');
    const orgBOwnerEmail = uniqueEmail('isolation-b');
    cy.createKeycloakUser(orgAOwnerEmail, 'E2E', 'OrgA');
    cy.createKeycloakUser(orgBOwnerEmail, 'E2E', 'OrgB');
    userIdFor(orgBOwnerEmail); // sync into backend users table

    createOrgWithSubscription(orgAOwnerEmail, 'Org A', uniqueSlug());
    createOrgWithSubscription(orgBOwnerEmail, 'Org B', uniqueSlug());

    tokenFor(orgAOwnerEmail).then((token) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { email: orgBOwnerEmail },
        headers: { Authorization: `Bearer ${token}` },
      }).then((res) => {
        const emails = (res.body as Array<{ email: string }>).map((u) => u.email);
        expect(emails).to.include(orgBOwnerEmail);
      });
    });

    cy.deleteKeycloakUser(orgAOwnerEmail);
    cy.deleteKeycloakUser(orgBOwnerEmail);
  });

  it('user search rejects short/blank queries with 400, and a caller with no org gets 403', () => {
    const ownerEmail = uniqueEmail('search-validation-owner');
    const noOrgEmail = uniqueEmail('search-validation-noorg');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(noOrgEmail, 'E2E', 'NoOrg');
    createOrgWithSubscription(ownerEmail, 'Search Validation Corp', uniqueSlug());

    tokenFor(ownerEmail).then((token) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { email: 'a' },
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(400));

      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { email: '' },
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(400));
    });

    tokenFor(noOrgEmail).then((token) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { email: 'someone@example.com' },
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(403));
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(noOrgEmail);
  });

  // ADR-0049 edge case: results capped at 10, confirmed as truncation not an error.
  // Candidates are added as members of the search org first — given the search-scope
  // gap documented above, an unaffiliated candidate is never findable at all, so this
  // still exercises the truncation logic itself (independent of that gap) by
  // satisfying the current (buggy) within-org constraint instead of fighting it.
  it('user search results are capped at 10 even with 11+ matches', () => {
    const ownerEmail = uniqueEmail('cap-owner');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');

    const prefix = `e2e-cap-${Date.now()}`;
    const matchEmails = Array.from({ length: 11 }, (_, i) => `${prefix}-${i}@example.com`);
    matchEmails.forEach((email) => {
      cy.createKeycloakUser(email, 'Cap', `User${email}`);
    });

    createOrgWithSubscription(ownerEmail, 'Cap Corp', uniqueSlug()).then((orgId) => {
      matchEmails.forEach((email) => {
        userIdFor(email).then((userId) => addMember(orgId, ownerEmail, userId, 'MEMBER'));
      });
    });

    tokenFor(ownerEmail).then((token) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { email: prefix },
        headers: { Authorization: `Bearer ${token}` },
      }).then((res) => {
        expect(res.body.length).to.eq(10);
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    matchEmails.forEach((email) => cy.deleteKeycloakUser(email));
  });
});
