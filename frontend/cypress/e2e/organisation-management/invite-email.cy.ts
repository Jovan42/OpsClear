// JOB-345 (ADR-0059): the invite email in Mailpit, Copy link and Resend email on the
// pending invites page. Uses cy.loginAs() per docs/dev/process/E2E.md.

import {
  uniqueEmail,
  uniqueSlug,
  createOrgWithSubscription,
  sendInvite,
  inviteTokenFor,
} from '../../support/orgApi';

describe('Organisation Management — Invite email, Copy link and Resend', () => {
  it('OWNER invites a new email; the email arrives in Mailpit and its link joins the invitee', { tags: '@smoke' }, () => {
    const ownerEmail = uniqueEmail('mail-owner');
    const inviteeEmail = uniqueEmail('mail-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(inviteeEmail, 'E2E', 'Invitee');
    createOrgWithSubscription(ownerEmail, 'Mail Corp', uniqueSlug());

    cy.intercept('POST', '**/api/organisations/*/invites').as('sendInvite');
    cy.loginAs(ownerEmail);
    cy.visit('/org/invites');
    cy.get('input[type=email]').type(inviteeEmail);
    cy.contains('button', 'Send invite').click();
    // The email goes out in the inviter's UI language
    cy.wait('@sendInvite').its('request.body.language').should('eq', 'en');
    cy.contains('td', inviteeEmail).should('be.visible');

    cy.mailpitLink(inviteeEmail, '/invite/').then((link) => {
      expect(link).to.match(/\/invite\/[0-9a-f]+$/);
      cy.loginAs(inviteeEmail);
      cy.visit(new URL(link).pathname);
      // Signed in with the invited address, the page joins straight away (JOB-399)
      cy.location('pathname', { timeout: 10000 }).should('eq', '/projects');
      cy.get('[data-sonner-toast]').should('contain.text', "You've joined Mail Corp");
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(inviteeEmail);
  });

  it('Copy link puts the invite URL on the clipboard and confirms with a toast', () => {
    const ownerEmail = uniqueEmail('copy-owner');
    const inviteeEmail = uniqueEmail('copy-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');

    createOrgWithSubscription(ownerEmail, 'Copy Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) => {
        inviteTokenFor(inviteId).then((token) => {
          cy.loginAs(ownerEmail);
          cy.visit('/org/invites');
          cy.window().then((win) => {
            cy.stub(win.navigator.clipboard, 'writeText').as('writeText').resolves();
          });
          cy.contains('tr', inviteeEmail).contains('button', 'Copy link').click();
          cy.location('origin').then((origin) => {
            cy.get('@writeText').should('have.been.calledOnceWith', `${origin}/invite/${token}`);
          });
          cy.get('[data-sonner-toast]').should('be.visible').and('contain.text', 'Link copied');
        });
      });
    });

    cy.deleteKeycloakUser(ownerEmail);
  });

  it('Resend straight after the invite shows only the cooldown message', () => {
    const ownerEmail = uniqueEmail('resend-owner');
    const inviteeEmail = uniqueEmail('resend-invitee');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');

    createOrgWithSubscription(ownerEmail, 'Resend Corp', uniqueSlug()).then((orgId) => {
      sendInvite(orgId, ownerEmail, inviteeEmail);
    });

    cy.intercept('POST', '**/api/organisations/*/invites/*/resend').as('resend');
    cy.loginAs(ownerEmail);
    cy.visit('/org/invites');
    cy.contains('tr', inviteeEmail).contains('button', 'Resend email').click();
    // Real 429 from the backend: creating the invite started the 5-minute cooldown
    cy.wait('@resend').its('response.statusCode').should('eq', 429);

    // "N minutes" when the browser can read Retry-After; cross-origin (local dev, CI) it
    // can't unless the backend's CORS exposes it, and the generic wording shows instead
    cy.get('[data-sonner-toast]')
      .should('have.length', 1)
      .and('contain.text', 'You can resend in')
      .invoke('text')
      .should('match', /You can resend in (5 minutes|a few minutes)/);
    // The interceptor's generic 429 toast is skipped for this request
    cy.contains('[data-sonner-toast]', 'Too many requests').should('not.exist');

    cy.deleteKeycloakUser(ownerEmail);
  });
});
