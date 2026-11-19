// JOB-399: the public invite page (/invite/:token). Signed out it shows who invited you and
// where, with Create account and I already have an account; signing up or logging in from
// there comes back to the page, which joins the organisation without another click. The
// sign-up and login cases drive the real Keycloak pages, like the Auth Flows suite; the rest
// use cy.loginAs() per docs/dev/process/E2E.md.

import { PENDING_INVITE_KEY } from '../../../src/features/org/inviteLink';
import {
  API,
  KEYCLOAK,
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithSubscription,
  sendInvite,
  inviteTokenFor,
  expireInvite,
} from '../../support/orgApi';

const LOGOUT = `${KEYCLOAK}/realms/opsclear/protocol/openid-connect/logout?client_id=opsclear-frontend&post_logout_redirect_uri=http://localhost:5173`;

interface InviteFixture {
  ownerEmail: string;
  orgId: string;
  inviteId: string;
  token: string;
}

/** An org owned by a new "Ana Owner", with a pending invite to `inviteeEmail`. */
function setUpInvite(label: string, orgName: string, inviteeEmail: string): Cypress.Chainable<InviteFixture> {
  const ownerEmail = uniqueEmail(`${label}-owner`);
  cy.createKeycloakUser(ownerEmail, 'Ana', 'Owner');
  return createOrgWithSubscription(ownerEmail, orgName, uniqueSlug()).then((orgId) =>
    sendInvite(orgId, ownerEmail, inviteeEmail).then((inviteId) =>
      inviteTokenFor(inviteId).then((token) => ({ ownerEmail, orgId, inviteId, token })),
    ),
  );
}

function memberEmails(orgId: string, ownerEmail: string) {
  return tokenFor(ownerEmail).then((token) =>
    cy
      .request({
        url: `${API}/api/organisations/${orgId}/members`,
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((res) => (res.body as Array<{ userEmail: string }>).map((m) => m.userEmail)),
  );
}

function publicInviteStatus(token: string) {
  return cy.request({ url: `${API}/api/invites/${token}` }).then((res) => res.body.status as string);
}

/**
 * Ends any Keycloak session and waits for the home page it redirects to to finish loading.
 * The home page's own check-sso round trip (a full-page redirect to Keycloak and back to `/`)
 * otherwise lands after the next cy.visit() and takes the browser back to `/`.
 */
function signOutOfKeycloak() {
  cy.visit(LOGOUT);
  cy.location('pathname', { timeout: 10000 }).should('eq', '/');
  cy.contains('button', 'Log in', { timeout: 10000 }).should('be.visible');
}

/** Client-side navigation, as a link in the app would do: keeps the page's query cache and
 *  module state, which cy.visit() (a full page load) would reset. */
function navigateInApp(path: string) {
  cy.window().then((win) => {
    win.history.pushState({}, '', path);
    win.dispatchEvent(new PopStateEvent('popstate'));
  });
}

/** Fails only the next accept, with a backend-style error body. */
function failNextAccept() {
  cy.intercept(
    { method: 'POST', url: '**/api/invites/*/accept', times: 1 },
    { statusCode: 409, body: { error: 'Conflict', message: 'Something on the server said no' } },
  ).as('failedAccept');
}

describe('Organisation Management — Invite page', () => {
  it('signed out, shows the invite; I already have an account logs in and joins without another click', { tags: '@smoke' }, () => {
    const inviteeEmail = uniqueEmail('page-login-invitee');
    cy.createKeycloakUser(inviteeEmail, 'Nikola', 'Invitee');

    setUpInvite('page-login', 'Login Corp', inviteeEmail).then(({ ownerEmail, orgId, token }) => {
      signOutOfKeycloak();
      cy.visit(`/invite/${token}`);
      // No login screen first: the page itself, with the inviter, the organisation and the address
      cy.location('pathname').should('eq', `/invite/${token}`);
      cy.contains('Ana Owner invited you to join Login Corp on OpsClear').should('be.visible');
      cy.contains(`The invite is for ${inviteeEmail}`).should('be.visible');
      cy.contains('button', 'Create account').should('be.visible');
      cy.contains('button', 'I already have an account').click();

      cy.origin(KEYCLOAK, { args: { inviteeEmail } }, ({ inviteeEmail }) => {
        // login_hint fills in the invited address
        cy.get('#username', { timeout: 10000 }).should('have.value', inviteeEmail);
        cy.get('#password').type('password123');
        cy.get('#kc-login').click();
      });

      cy.location('pathname', { timeout: 15000 }).should('eq', '/projects');
      cy.contains("You've joined Login Corp").should('be.visible');
      memberEmails(orgId, ownerEmail).should('include', inviteeEmail);
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(inviteeEmail);
  });

  // The verification link is opened in the same browser, as clicking it in the mail would;
  // Keycloak then sends the user back to the invite page, which joins.
  it('Create account opens registration with the invited address, and after verifying the email the user has joined', () => {
    const inviteeEmail = uniqueEmail('page-register-invitee');

    setUpInvite('page-register', 'Register Corp', inviteeEmail).then(({ ownerEmail, orgId, token }) => {
      signOutOfKeycloak();
      cy.visit(`/invite/${token}`);
      cy.contains('button', 'Create account').click();

      cy.origin(KEYCLOAK, { args: { inviteeEmail } }, ({ inviteeEmail }) => {
        cy.get('#email', { timeout: 10000 }).should('have.value', inviteeEmail);
        cy.get('#password').type('password123');
        cy.get('#password-confirm').type('password123');
        cy.get('#firstName').type('Nikola');
        cy.get('#lastName').type('Newuser');
        cy.get('#kc-form-buttons').find('input[type=submit]').click();
        cy.get('h1').should('contain.text', 'Verify your email');
      });
      cy.mailpitLink(inviteeEmail, '/login-actions/action-token').then((link) => {
        cy.origin(KEYCLOAK, { args: { link } }, ({ link }) => {
          cy.window().then((win) => {
            win.location.href = link;
          });
          cy.url().should('not.include', 'localhost:8180');
        });
      });

      cy.location('pathname', { timeout: 15000 }).should('eq', '/projects');
      cy.contains("You've joined Register Corp").should('be.visible');
      memberEmails(orgId, ownerEmail).should('include', inviteeEmail);
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(inviteeEmail);
  });

  it('signed in with the invited address, joins once; opening the link again says it was used', () => {
    const inviteeEmail = uniqueEmail('page-once-invitee');
    cy.createKeycloakUser(inviteeEmail, 'Nikola', 'Invitee');

    setUpInvite('page-once', 'Once Corp', inviteeEmail).then(({ ownerEmail, token }) => {
      cy.intercept('POST', '**/api/invites/*/accept').as('accept');
      cy.loginAs(inviteeEmail);
      cy.visit(`/invite/${token}`);
      cy.location('pathname', { timeout: 10000 }).should('eq', '/projects');
      cy.contains("You've joined Once Corp").should('be.visible');
      // The dev server runs React StrictMode, which runs effects twice: still one accept
      cy.get('@accept.all').should('have.length', 1);

      cy.visit(`/invite/${token}`);
      cy.contains('This invite has already been used').should('be.visible');
      cy.contains('button', 'Go to projects').should('be.visible');
      cy.get('@accept.all').should('have.length', 1);
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(inviteeEmail);
  });

  it('signed in as a different address, shows the wrong-account state with Sign out and joins nothing', () => {
    const inviteeEmail = uniqueEmail('page-wrong-invitee');
    const otherEmail = uniqueEmail('page-wrong-other');
    cy.createKeycloakUser(otherEmail, 'Other', 'User');

    setUpInvite('page-wrong', 'Wrong Corp', inviteeEmail).then(({ ownerEmail, token }) => {
      cy.loginAs(otherEmail);
      cy.visit(`/invite/${token}`);
      cy.contains('This invite is for a different account').should('be.visible');
      cy.contains(`This invite is for ${inviteeEmail}, but you're signed in as ${otherEmail}`).should('be.visible');
      cy.get('[data-testid=invite-page]').contains('button', 'Sign out').should('be.visible');
      publicInviteStatus(token).should('eq', 'PENDING');
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(otherEmail);
  });

  it('expired, withdrawn and unknown invite links each show their own message', () => {
    const expiredEmail = uniqueEmail('page-closed-expired');
    const revokedEmail = uniqueEmail('page-closed-revoked');

    setUpInvite('page-closed', 'Closed Corp', expiredEmail).then(({ ownerEmail, orgId, inviteId, token }) => {
      expireInvite(inviteId);
      cy.visit(`/invite/${token}`);
      cy.contains('This invite has expired').should('be.visible');
      cy.contains('Ask Ana Owner to send you a new invite.').should('be.visible');
      cy.contains('button', 'Log in').should('be.visible');

      sendInvite(orgId, ownerEmail, revokedEmail).then((revokedId) => {
        tokenFor(ownerEmail).then((ownerToken) =>
          cy.request({
            method: 'DELETE',
            url: `${API}/api/organisations/${orgId}/invites/${revokedId}`,
            headers: { Authorization: `Bearer ${ownerToken}` },
          }),
        );
        inviteTokenFor(revokedId).then((revokedToken) => {
          cy.visit(`/invite/${revokedToken}`);
          cy.contains('This invite was withdrawn').should('be.visible');
          cy.contains('Ask Ana Owner to send you a new invite.').should('be.visible');
        });
      });

      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.visit('/invite/0000000000000000000000000000000000000000');
    cy.contains("This invite link isn't valid").should('be.visible');
  });

  // A user who saw "no organisation" first (here: /projects sent them to onboarding) has that
  // cached. Joining must not leave it there, or /projects sends the new member back to
  // onboarding and the toast is lost.
  it('a user whose app already knows they have no organisation lands on Projects after joining', () => {
    const inviteeEmail = uniqueEmail('page-cache-invitee');
    cy.createKeycloakUser(inviteeEmail, 'Nikola', 'Invitee');

    setUpInvite('page-cache', 'Cache Corp', inviteeEmail).then(({ ownerEmail, token }) => {
      cy.loginAs(inviteeEmail);
      cy.visit('/projects');
      cy.location('pathname', { timeout: 10000 }).should('eq', '/onboarding');
      navigateInApp(`/invite/${token}`);
      cy.location('pathname', { timeout: 10000 }).should('eq', '/projects');
      cy.contains("You've joined Cache Corp").should('be.visible');
      cy.location('pathname').should('eq', '/projects');
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(inviteeEmail);
  });

  it('an accept error shows the server message, and Try again sends a new accept that joins', () => {
    const inviteeEmail = uniqueEmail('page-retry-invitee');
    cy.createKeycloakUser(inviteeEmail, 'Nikola', 'Invitee');

    setUpInvite('page-retry', 'Retry Corp', inviteeEmail).then(({ ownerEmail, orgId, token }) => {
      failNextAccept();
      cy.loginAs(inviteeEmail);
      cy.visit(`/invite/${token}`);
      cy.wait('@failedAccept');
      cy.contains("Couldn't join Retry Corp").should('be.visible');
      cy.contains('Something on the server said no').should('be.visible');

      cy.intercept('POST', '**/api/invites/*/accept').as('accept');
      cy.contains('button', 'Try again').click();
      cy.wait('@accept').its('response.statusCode').should('eq', 200);
      cy.location('pathname', { timeout: 10000 }).should('eq', '/projects');
      cy.contains("You've joined Retry Corp").should('be.visible');
      memberEmails(orgId, ownerEmail).should('include', inviteeEmail);
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(inviteeEmail);
  });

  it('after an accept error, Go to projects leaves the page, and opening the invite again in the same session joins', () => {
    const inviteeEmail = uniqueEmail('page-reopen-invitee');
    cy.createKeycloakUser(inviteeEmail, 'Nikola', 'Invitee');

    setUpInvite('page-reopen', 'Reopen Corp', inviteeEmail).then(({ ownerEmail, token }) => {
      failNextAccept();
      cy.loginAs(inviteeEmail);
      cy.visit(`/invite/${token}`);
      cy.wait('@failedAccept');
      cy.contains('Something on the server said no').should('be.visible');

      cy.get('[data-testid=invite-page]').contains('button', 'Go to projects').click();
      // Projects, which sends a user with no organisation on to onboarding
      cy.location('pathname', { timeout: 10000 }).should('eq', '/onboarding');

      // Same page load, so the failed token must not still count as "accept sent"
      cy.intercept('POST', '**/api/invites/*/accept').as('accept');
      navigateInApp(`/invite/${token}`);
      cy.wait('@accept').its('response.statusCode').should('eq', 200);
      cy.location('pathname', { timeout: 10000 }).should('eq', '/projects');
      cy.contains("You've joined Reopen Corp").should('be.visible');
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(inviteeEmail);
  });

  // The fallback for a sign-up whose redirect doesn't come back to the invite page (email
  // verified elsewhere): the token the page saved sends the signed-in user back to it.
  it('a token saved by the invite page brings a user who lands on the home page back to join', () => {
    const inviteeEmail = uniqueEmail('page-saved-invitee');
    cy.createKeycloakUser(inviteeEmail, 'Nikola', 'Invitee');

    setUpInvite('page-saved', 'Saved Corp', inviteeEmail).then(({ ownerEmail, token }) => {
      cy.loginAs(inviteeEmail);
      cy.visit('/', {
        onBeforeLoad(win) {
          win.localStorage.setItem(PENDING_INVITE_KEY, JSON.stringify({ token, savedAt: Date.now() }));
        },
      });
      cy.location('pathname', { timeout: 10000 }).should('eq', '/projects');
      cy.contains("You've joined Saved Corp").should('be.visible');
      cy.window().then((win) => expect(win.localStorage.getItem(PENDING_INVITE_KEY)).to.be.null);
      cy.deleteKeycloakUser(ownerEmail);
    });

    cy.deleteKeycloakUser(inviteeEmail);
  });
});
