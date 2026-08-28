// ADR-0049 Appendix §2 (Organisation Management — Invites). Uses cy.loginAs() per
// docs/dev/process/E2E.md, except the one case whose actual subject is the real
// login redirect (JOB-239), which drives real Keycloak UI like the dedicated Auth
// Flows suite does.

import {
  API,
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  createOrgWithSubscription,
  addMember,
  sendInvite,
  inviteTokenFor,
  expireInvite,
} from '../../support/orgApi';

describe('Organisation Management — Invites', () => {
  it('OWNER sends an invite; it appears pending with inviter name and a 7-day expiry', () => {
    const ownerEmail = uniqueEmail('send-owner');
    const inviteeEmail = uniqueEmail('send-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    createOrgWithSubscription(ownerEmail, 'Send Corp', uniqueSlug());

    cy.loginAs(ownerEmail);
    cy.visit('/org/invites');
    cy.get('input[type=email]').type(inviteeEmail);
    cy.contains('button', 'Send invite').click();
    cy.contains('td', inviteeEmail).should('be.visible');
    cy.contains('tr', inviteeEmail).contains('E2E Owner').should('be.visible');
    cy.contains('tr', inviteeEmail).contains('Expires in 7 days').should('be.visible');

    cy.deleteKeycloakUser(ownerEmail);
  });

  it('invited user (already has an account) accepts and is added as MEMBER', () => {
    const ownerEmail = uniqueEmail('accept-owner');
    const inviteeEmail = uniqueEmail('accept-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(inviteeEmail, 'E2E', 'Invitee');
    userIdFor(inviteeEmail); // sync into the backend users table

    createOrgWithSubscription(ownerEmail, 'Accept Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) => {
        inviteTokenFor(inviteId).then((token) => {
          cy.loginAs(inviteeEmail);
          cy.visit(`/invite/${token}`);
          cy.contains('button', 'Accept invite').click();
          cy.contains("You've joined the organisation", { timeout: 10000 }).should('be.visible');
        });
      });

      tokenFor(ownerEmail).then((ownerToken) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/organisations/${orgId}/members`,
          headers: { Authorization: `Bearer ${ownerToken}` },
        }).then((res) => {
          const emails = (res.body as Array<{ userEmail: string }>).map((m) => m.userEmail);
          expect(emails).to.include(inviteeEmail);
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(inviteeEmail);
  });

  it('an email already a member cannot be re-invited, and an already-pending invite cannot be duplicated', () => {
    const ownerEmail = uniqueEmail('dup-owner');
    const memberEmail = uniqueEmail('dup-member');
    const pendingEmail = uniqueEmail('dup-pending');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');

    createOrgWithSubscription(ownerEmail, 'Dup Corp', uniqueSlug()).then((orgId) => {
      userIdFor(memberEmail).then((memberId) => addMember(orgId, ownerEmail, memberId, 'MEMBER'));
      sendInvite(orgId, ownerEmail, pendingEmail);

      cy.loginAs(ownerEmail);
      cy.visit('/org/invites');

      cy.get('input[type=email]').type(memberEmail);
      cy.contains('button', 'Send invite').click();
      cy.contains('This email address is already a member of the organisation').should('be.visible');

      cy.get('input[type=email]').clear();
      cy.get('input[type=email]').type(pendingEmail);
      cy.contains('button', 'Send invite').click();
      cy.contains('A pending invite already exists for this email').should('be.visible');
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('blank email is blocked client-side; a malformed email is rejected server-side', () => {
    const ownerEmail = uniqueEmail('validation-owner');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    createOrgWithSubscription(ownerEmail, 'Validation Corp', uniqueSlug());

    cy.loginAs(ownerEmail);
    cy.visit('/org/invites');
    cy.contains('button', 'Send invite').should('be.disabled');

    cy.get('input[type=email]').type('not-an-email');
    cy.contains('button', 'Send invite').click();
    cy.contains(/valid email/i).should('be.visible');

    cy.deleteKeycloakUser(ownerEmail);
  });

  it('OWNER revokes a pending invite; it disappears from the list', () => {
    const ownerEmail = uniqueEmail('revoke-owner');
    const inviteeEmail = uniqueEmail('revoke-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');

    createOrgWithSubscription(ownerEmail, 'Revoke Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail);
    });

    cy.loginAs(ownerEmail);
    cy.visit('/org/invites');
    cy.contains('tr', inviteeEmail).contains('Revoke').click();
    cy.get('.z-50').should('be.visible').within(() => {
      cy.contains('Revoke invite?').should('be.visible');
      cy.contains('button', 'Revoke').click();
    });
    cy.contains('td', inviteeEmail).should('not.exist');

    cy.deleteKeycloakUser(ownerEmail);
  });

  it('a revoked/accepted invite never reappears in the pending list; GET only returns pending invites', () => {
    const ownerEmail = uniqueEmail('pending-only-owner');
    const inviteeEmail = uniqueEmail('pending-only-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');

    createOrgWithSubscription(ownerEmail, 'PendingOnly Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) => {
        tokenFor(ownerEmail).then((token) => {
          cy.request({
            method: 'DELETE',
            url: `${API}/api/organisations/${orgId}/invites/${inviteId}`,
            headers: { Authorization: `Bearer ${token}` },
          });
          cy.request({
            method: 'GET',
            url: `${API}/api/organisations/${orgId}/invites`,
            headers: { Authorization: `Bearer ${token}` },
          }).then((res) => {
            expect(res.body).to.deep.equal([]);
          });
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
  });

  it('revoking a nonexistent/foreign invite 404s; revoking an already-revoked invite 400s', () => {
    const ownerAEmail = uniqueEmail('revokeperm-a');
    const ownerBEmail = uniqueEmail('revokeperm-b');
    cy.createKeycloakUser(ownerAEmail, 'E2E', 'OwnerA');
    cy.createKeycloakUser(ownerBEmail, 'E2E', 'OwnerB');

    createOrgWithSubscription(ownerAEmail, 'Revoke Perm A', uniqueSlug()).then((orgAId) => {
      createOrgWithSubscription(ownerBEmail, 'Revoke Perm B', uniqueSlug()).then((orgBId) => {
        sendInvite(orgBId, ownerBEmail, uniqueEmail('revokeperm-target')).then((inviteId) => {
          tokenFor(ownerAEmail).then((tokenA) => {
            // Org A's owner trying to revoke Org B's invite via a guessed/known id.
            cy.request({
              method: 'DELETE',
              url: `${API}/api/organisations/${orgAId}/invites/${inviteId}`,
              headers: { Authorization: `Bearer ${tokenA}` },
              failOnStatusCode: false,
            }).then((res) => expect(res.status).to.eq(404));
          });

          tokenFor(ownerBEmail).then((tokenB) => {
            cy.request({
              method: 'DELETE',
              url: `${API}/api/organisations/${orgBId}/invites/${inviteId}`,
              headers: { Authorization: `Bearer ${tokenB}` },
            });
            // Already revoked now — revoking again must 400, not silently succeed.
            cy.request({
              method: 'DELETE',
              url: `${API}/api/organisations/${orgBId}/invites/${inviteId}`,
              headers: { Authorization: `Bearer ${tokenB}` },
              failOnStatusCode: false,
            }).then((res) => expect(res.status).to.eq(400));
          });
        });
      });
    });

    cy.deleteKeycloakUser(ownerAEmail);
    cy.deleteKeycloakUser(ownerBEmail);
  });

  it('accepting an unknown token 404s; accepting while logged in as a different email 403s', () => {
    const ownerEmail = uniqueEmail('accepterr-owner');
    const inviteeEmail = uniqueEmail('accepterr-invitee');
    const wrongEmail = uniqueEmail('accepterr-wrong');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(wrongEmail, 'E2E', 'Wrong');

    tokenFor(wrongEmail).then((wrongToken) => {
      cy.request({
        method: 'POST',
        url: `${API}/api/invites/00000000-0000-0000-0000-000000000000/accept`,
        headers: { Authorization: `Bearer ${wrongToken}` },
        failOnStatusCode: false,
      }).then((res) => expect(res.status).to.eq(404));
    });

    createOrgWithSubscription(ownerEmail, 'AcceptErr Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) => {
        inviteTokenFor(inviteId).then((token) => {
          tokenFor(wrongEmail).then((wrongToken) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/invites/${token}/accept`,
              headers: { Authorization: `Bearer ${wrongToken}` },
              failOnStatusCode: false,
            }).then((res) => expect(res.status).to.eq(403));
          });
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(wrongEmail);
  });

  it('invite email match is case-insensitive: sent as Mixed@Case, accepted as lowercase', () => {
    const ownerEmail = uniqueEmail('case-owner');
    const inviteeEmailLower = uniqueEmail('case-invitee');
    const inviteeEmailMixed = inviteeEmailLower.replace('e2e-org-case-invitee', 'E2E-Org-Case-Invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(inviteeEmailLower, 'E2E', 'Invitee');
    userIdFor(inviteeEmailLower);

    createOrgWithSubscription(ownerEmail, 'Case Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmailMixed).then((inviteId) => {
        inviteTokenFor(inviteId).then((token) => {
          cy.loginAs(inviteeEmailLower);
          cy.visit(`/invite/${token}`);
          cy.contains('button', 'Accept invite').click();
          cy.contains("You've joined the organisation", { timeout: 10000 }).should('be.visible');
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(inviteeEmailLower);
  });

  // ADR-0049 edge case: "Accepting an email that became a member via another path in
  // the meantime → 409 EMAIL_ALREADY_MEMBER" — e.g. the OWNER manually adds them via
  // the members picker while their invite is still sitting pending.
  it('accepting an invite for an email that became a member another way in the meantime 409s', () => {
    const ownerEmail = uniqueEmail('midair-owner');
    const inviteeEmail = uniqueEmail('midair-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(inviteeEmail, 'E2E', 'Invitee');

    createOrgWithSubscription(ownerEmail, 'Midair Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) => {
        userIdFor(inviteeEmail).then((inviteeId) => addMember(orgId, ownerEmail, inviteeId, 'MEMBER'));
        inviteTokenFor(inviteId).then((token) => {
          tokenFor(inviteeEmail).then((inviteeToken) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/invites/${token}/accept`,
              headers: { Authorization: `Bearer ${inviteeToken}` },
              failOnStatusCode: false,
            }).then((res) => expect(res.status).to.eq(409));
          });
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(inviteeEmail);
  });

  // ADR-0049: "Invite token expiry (7 days) actually enforced on accept, not just
  // revoked/accepted state." No API can fast-forward time — backdates expires_at
  // directly (cypress.config.ts's queryDb task) rather than waiting 7 real days.
  it('an expired invite cannot be accepted', () => {
    const ownerEmail = uniqueEmail('expiry-owner');
    const inviteeEmail = uniqueEmail('expiry-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(inviteeEmail, 'E2E', 'Invitee');

    createOrgWithSubscription(ownerEmail, 'Expiry Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) => {
        expireInvite(inviteId);
        inviteTokenFor(inviteId).then((token) => {
          tokenFor(inviteeEmail).then((inviteeToken) => {
            cy.request({
              method: 'POST',
              url: `${API}/api/invites/${token}/accept`,
              headers: { Authorization: `Bearer ${inviteeToken}` },
              failOnStatusCode: false,
            }).then((res) => expect(res.status).to.eq(400));
          });
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(inviteeEmail);
  });

  // JOB-237/JOB-239 regression: an unauthenticated visitor to an accept-invite link is
  // routed through real login and lands back on the exact invite URL, token intact.
  it('an unauthenticated visitor is routed through login and lands back on the invite page', () => {
    const ownerEmail = uniqueEmail('unauth-owner');
    const inviteeEmail = uniqueEmail('unauth-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(inviteeEmail, 'E2E', 'Invitee');
    userIdFor(inviteeEmail);

    createOrgWithSubscription(ownerEmail, 'Unauth Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) => {
        inviteTokenFor(inviteId).then((token) => {
          cy.visit('http://localhost:8180/realms/opsclear/protocol/openid-connect/logout?client_id=opsclear-frontend&post_logout_redirect_uri=http://localhost:5173');
          cy.location('pathname', { timeout: 10000 }).should('eq', '/');

          cy.visit(`/invite/${token}`);
          cy.location('pathname', { timeout: 10000 }).should('eq', '/');
          cy.contains('Log in').click();
          cy.origin('http://localhost:8180', { args: { inviteeEmail } }, ({ inviteeEmail }) => {
            cy.get('#username', { timeout: 10000 }).type(inviteeEmail);
            cy.get('#password').type('password123');
            cy.get('#kc-login').click();
          });
          cy.url({ timeout: 10000 }).should('include', `localhost:5173/invite/${token}`);
          cy.contains('Accept invite').should('be.visible');
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(inviteeEmail);
  });
});
