// ADR-0051 (JOB-283): per-type notification mutes on the Settings page. Uses cy.loginAs()
// per docs/dev/process/E2E.md.
//
// Mutes are stored on the server (they decide fan-out), unlike the device-local sound
// toggle in the same section (notifications/sound-setting.cy.ts). Notifications are
// triggered through real API actions by another user, as in the notifications specs.
//
// Left to the PR's manual checklist: another browser/device, mobile width, sr labels.

import { API, createOrgWithSubscription, requestApprovalAs, tokenFor, uniqueEmail, uniqueSlug, updateJobStatusAs } from '../../support/orgApi';
import { cleanUpUsers, setUpOwnerAndMember, waitForUnreadCount } from '../../support/notifications';

const SETTINGS_PATH = '/api/user/notification-settings';

const toggle = (type: string) => cy.get(`[data-testid="notification-type-toggle-${type}"]`);
const bell = () => cy.get('[data-testid="notification-bell"]');
const badge = () => cy.get('[data-testid="notification-badge"]');

function mutedFor(email: string): Cypress.Chainable<string[]> {
  return tokenFor(email).then((token) =>
    cy
      .request({ url: `${API}${SETTINGS_PATH}`, headers: { Authorization: `Bearer ${token}` } })
      .then(({ body }: { body: { muted: string[] } }) => body.muted),
  );
}

function notificationTypesFor(email: string): Cypress.Chainable<string[]> {
  return tokenFor(email).then((token) =>
    cy
      .request({ url: `${API}/api/notifications`, headers: { Authorization: `Bearer ${token}` } })
      .then(({ body }: { body: { items: { type: string }[] } }) => body.items.map((i) => i.type)),
  );
}

function newUserWithOrg(label: string): Cypress.Chainable<string> {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'Nora', 'Settings');
  return createOrgWithSubscription(email, 'Mute Corp', uniqueSlug()).then(() => email);
}

describe('Notification settings', () => {
  it('mutes a type, and the toggle stays off after a reload', { tags: '@smoke' }, () => {
    newUserWithOrg('mute-persist').then((email) => {
      cy.intercept('PUT', `**${SETTINGS_PATH}`).as('save');
      cy.loginAs(email);
      cy.visit('/settings');

      // All 16 on by default.
      cy.get('[data-testid="notification-type-settings"]').should('contain', 'All your devices');
      cy.get('[data-testid^="notification-type-toggle-"]')
        .should('have.length', 16)
        .each(($el) => expect($el).to.have.attr('aria-checked', 'true'));

      toggle('JOB_STATUS_CHANGED').click();
      toggle('JOB_STATUS_CHANGED').should('have.attr', 'aria-checked', 'false');
      cy.wait('@save').then(({ request, response }) => {
        expect(request.body).to.deep.eq({ muted: ['JOB_STATUS_CHANGED'] });
        expect(response?.statusCode).to.eq(200);
      });

      cy.reload();
      toggle('JOB_STATUS_CHANGED').should('have.attr', 'aria-checked', 'false');
      toggle('JOB_ASSIGNED').should('have.attr', 'aria-checked', 'true');
      mutedFor(email).should('deep.eq', ['JOB_STATUS_CHANGED']);

      cy.deleteKeycloakUser(email);
    });
  });

  it("doesn't deliver a muted type, while an unmuted one still arrives", () => {
    setUpOwnerAndMember('mute-delivery').then(({ ownerEmail, memberEmail, projectId, jobId }) => {
      cy.intercept('PUT', `**${SETTINGS_PATH}`).as('save');
      cy.loginAs(ownerEmail);
      cy.visit('/settings');
      bell().should('have.attr', 'data-live', 'true');
      badge().should('not.exist');

      toggle('JOB_STATUS_CHANGED').click();
      cy.wait('@save').its('response.statusCode').should('eq', 200);

      // Muted for the owner: no row should be created.
      updateJobStatusAs(memberEmail, projectId, jobId, 'IN_PROGRESS').its('status').should('eq', 200);
      // Positive control, triggered after the muted one: proves delivery works, so a broken
      // mute would have shown up too.
      requestApprovalAs(memberEmail, projectId, jobId, 'Buy a new pump').its('status').should('eq', 201);

      badge().should('have.text', '1');
      waitForUnreadCount(ownerEmail, 1);
      // Bounded wait for a late, wrongly delivered status change (fan-out is async).
      // eslint-disable-next-line cypress/no-unnecessary-waiting
      cy.wait(3000);
      badge().should('have.text', '1');
      notificationTypesFor(ownerEmail).should('deep.eq', ['APPROVAL_REQUESTED']);

      cleanUpUsers(ownerEmail, memberEmail);
    });
  });

  it('shows the subscription past-due type locked on, and never sends it', () => {
    newUserWithOrg('mute-locked').then((email) => {
      cy.intercept('PUT', `**${SETTINGS_PATH}`).as('save');
      cy.loginAs(email);
      cy.visit('/settings');

      toggle('SUBSCRIPTION_PAST_DUE')
        .should('have.attr', 'aria-checked', 'true')
        .and('be.disabled')
        .and('have.attr', 'aria-describedby');
      cy.contains('Always on: billing problems affect the whole organisation.').should('be.visible');

      toggle('NOTE_ADDED').click();
      cy.wait('@save').its('request.body').should('deep.eq', { muted: ['NOTE_ADDED'] });
      toggle('SUBSCRIPTION_PAST_DUE').should('have.attr', 'aria-checked', 'true');

      // The backend rejects it outright, which is why the UI never sends it.
      tokenFor(email).then((token) =>
        cy
          .request({
            method: 'PUT',
            url: `${API}${SETTINGS_PATH}`,
            headers: { Authorization: `Bearer ${token}` },
            body: { muted: ['SUBSCRIPTION_PAST_DUE'] },
            failOnStatusCode: false,
          })
          .its('status')
          .should('eq', 400),
      );
      mutedFor(email).should('deep.eq', ['NOTE_ADDED']);

      cy.deleteKeycloakUser(email);
    });
  });

  it('puts the toggle back and shows an error when saving fails', () => {
    newUserWithOrg('mute-error').then((email) => {
      cy.intercept('PUT', `**${SETTINGS_PATH}`, {
        statusCode: 500,
        body: { error: 'Internal Server Error', message: 'Boom' },
      }).as('save');
      cy.loginAs(email);
      cy.visit('/settings');

      toggle('JOB_UPDATED').should('have.attr', 'aria-checked', 'true').click();
      cy.wait('@save');
      toggle('JOB_UPDATED').should('have.attr', 'aria-checked', 'true');
      cy.get('[data-testid="notification-settings-save-error"]').should(
        'have.text',
        "Couldn't save that change, so it was put back.",
      );
      mutedFor(email).should('deep.eq', []);

      cy.deleteKeycloakUser(email);
    });
  });
});
