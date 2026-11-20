// ADR-0049 Appendix §2 (Organisation Management — Members). Uses cy.loginAs() per
// docs/dev/process/E2E.md.

import { API, uniqueEmail, uniqueSlug, tokenFor, userIdFor, createOrgWithSubscription, addMember } from '../../support/orgApi';

describe('Organisation Management — Members', () => {
  // Invites are the only way into an organisation (JOB-401), and the invite flow
  // itself is covered in invites.cy.ts. The target is put in the org as a fixture
  // here (addMember) so this test covers the promote flow on the members page.
  it('OWNER promotes a new org member to ADMIN', { tags: '@smoke' }, () => {
    const ownerEmail = uniqueEmail('add-owner');
    const targetEmail = uniqueEmail('add-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'Findme', 'Target');

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

  // JOB-401: the direct-add form is gone; OWNER and ADMIN get a pointer to Invites instead.
  it('OWNER and ADMIN see no direct-add form, only a pointer that opens Invites', () => {
    const ownerEmail = uniqueEmail('pointer-owner');
    const adminEmail = uniqueEmail('pointer-admin');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(adminEmail, 'E2E', 'Admin');

    createOrgWithSubscription(ownerEmail, 'Pointer Corp', uniqueSlug()).then((orgId) => {
      userIdFor(adminEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'ADMIN'));
    });

    cy.loginAs(ownerEmail);
    cy.visit('/org/members');
    cy.contains('td', adminEmail).should('be.visible');
    cy.get('input[placeholder*="Search"]').should('not.exist');
    cy.get('[data-testid=org-members-invite-pointer]').within(() => {
      cy.contains('send them an invite').should('be.visible');
      cy.contains('button', 'Invite people').click();
    });
    cy.location('pathname').should('eq', '/org/invites');

    cy.loginAs(adminEmail);
    cy.visit('/org/members');
    cy.contains('td', ownerEmail).should('be.visible');
    cy.get('[data-testid=org-members-invite-pointer]').should('be.visible');

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(adminEmail);
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

  it('a plain MEMBER sees the list read-only — no invite pointer, no role select, no remove link', () => {
    const ownerEmail = uniqueEmail('ro-owner');
    const memberEmail = uniqueEmail('ro-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');

    createOrgWithSubscription(ownerEmail, 'RO Corp', uniqueSlug()).then((orgId) => {
      userIdFor(memberEmail).then((userId) => addMember(orgId, ownerEmail, userId, 'MEMBER'));
    });

    cy.loginAs(memberEmail);
    cy.visit('/org/members');
    cy.contains('td', ownerEmail).should('be.visible');
    cy.get('[data-testid=org-members-invite-pointer]').should('not.exist');
    cy.get('select').should('not.exist');
    cy.contains('Remove').should('not.exist');
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

  it('a plain MEMBER cannot remove members via direct API — 403', () => {
    const ownerEmail = uniqueEmail('perm-owner');
    const memberEmail = uniqueEmail('perm-member');
    const targetEmail = uniqueEmail('perm-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgWithSubscription(ownerEmail, 'Perm Corp', uniqueSlug()).then((orgId) => {
      userIdFor(memberEmail).then((memberId) => addMember(orgId, ownerEmail, memberId, 'MEMBER'));
      userIdFor(targetEmail).then((targetId) => {
        addMember(orgId, ownerEmail, targetId, 'MEMBER');
        tokenFor(memberEmail).then((token) => {
          cy.request({
            method: 'DELETE',
            url: `${API}/api/organisations/${orgId}/members/${targetId}`,
            headers: { Authorization: `Bearer ${token}` },
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

  it('cannot change a member TO owner, cannot change/remove the OWNER — all 403 via direct API', () => {
    const ownerEmail = uniqueEmail('ownerguard-owner');
    const targetEmail = uniqueEmail('ownerguard-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgWithSubscription(ownerEmail, 'OwnerGuard Corp', uniqueSlug()).then((orgId) => {
      userIdFor(targetEmail).then((targetId) => {
        tokenFor(ownerEmail).then((token) => {
          // ADR-0049: "role: OWNER → 403 ... (same on role-change path)" — promoting an
          // existing non-owner member to OWNER is blocked. Direct add is gone (JOB-402).
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

  // JOB-402: invites are the only way into an organisation. The direct-add endpoint
  // is gone, so a POST to the members collection is 405 and nobody joins.
  it('the removed direct-add endpoint answers 405 and adds nobody', () => {
    const ownerEmail = uniqueEmail('directadd-owner');
    const targetEmail = uniqueEmail('directadd-target');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgWithSubscription(ownerEmail, 'DirectAdd Corp', uniqueSlug()).then((orgId) => {
      userIdFor(targetEmail).then((targetId) => {
        tokenFor(ownerEmail).then((token) => {
          cy.request({
            method: 'POST',
            url: `${API}/api/organisations/${orgId}/members`,
            headers: { Authorization: `Bearer ${token}` },
            body: { userId: targetId, role: 'MEMBER' },
            failOnStatusCode: false,
          }).then((res) => expect(res.status).to.eq(405));

          cy.request({
            method: 'GET',
            url: `${API}/api/organisations/${orgId}/members`,
            headers: { Authorization: `Bearer ${token}` },
          }).then((res) => {
            expect(res.body.map((m: { userId: string }) => m.userId)).not.to.include(targetId);
          });
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(targetEmail);
  });

  // ADR-0049 multi-tenant isolation (JOB-400): searching from Org A for an Org B
  // user's exact email or name returns nothing. The deprecated `email` alias is
  // checked too while it exists; JOB-403 removes the alias and that assertion.
  it('user search never returns a user from a different organisation', () => {
    const orgAOwnerEmail = uniqueEmail('isolation-a');
    const orgBOwnerEmail = uniqueEmail('isolation-b');
    cy.createKeycloakUser(orgAOwnerEmail, 'E2E', 'OrgA');
    cy.createKeycloakUser(orgBOwnerEmail, 'E2E', 'OrgB');
    userIdFor(orgBOwnerEmail); // sync into backend users table

    createOrgWithSubscription(orgAOwnerEmail, 'Org A', uniqueSlug());
    createOrgWithSubscription(orgBOwnerEmail, 'Org B', uniqueSlug());

    tokenFor(orgAOwnerEmail).then((token) => {
      // Positive control: the same kind of name search finds someone in Org A
      // (the caller themselves), so the empty results below prove scoping, not a
      // broken name search.
      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { q: 'OrgA' },
        headers: { Authorization: `Bearer ${token}` },
      }).then((res) => {
        expect(res.status).to.eq(200);
        expect(res.body.map((u: { email: string }) => u.email)).to.deep.eq([orgAOwnerEmail]);
      });

      [{ q: orgBOwnerEmail }, { q: 'OrgB' }, { email: orgBOwnerEmail }].forEach((qs) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/users`,
          qs,
          headers: { Authorization: `Bearer ${token}` },
        }).then((res) => {
          expect(res.status).to.eq(200);
          expect(res.body).to.have.length(0);
        });
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
        qs: { q: 'a' },
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(400));

      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { q: '' },
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(400));
    });

    tokenFor(noOrgEmail).then((token) => {
      cy.request({
        method: 'GET',
        url: `${API}/api/users`,
        qs: { q: 'someone@example.com' },
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(403));
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(noOrgEmail);
  });

  // ADR-0049 edge case: results capped at 10, confirmed as truncation not an error.
  // Candidates are added as members of the search org first, since the search only
  // returns members of the caller's organisation.
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
        qs: { q: prefix },
        headers: { Authorization: `Bearer ${token}` },
      }).then((res) => {
        expect(res.body.length).to.eq(10);
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    matchEmails.forEach((email) => cy.deleteKeycloakUser(email));
  });
});
