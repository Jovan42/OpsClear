// ADR-0061 (JOB-351): preferences sync through /api/user/preferences, with localStorage as a
// per-user cache, the one-time upload of the legacy `opsclear:preferences` key, the
// save-error toast, and the Settings page's section index. Uses cy.loginAs() per
// docs/dev/process/E2E.md. Every test uses fresh users and resets their stored preferences
// (PATCH with nulls) at the end.
//
// Not covered: "the same user on two browsers" (Cypress has one browser per run; the server
// round trip after clearing localStorage below is the same path) and the backend being
// down for real (the error test stubs the PATCH instead). Both are on the PR's manual list.

import { createOrgWithSubscription, uniqueEmail, uniqueSlug, userIdFor } from '../../support/orgApi';
import {
  LEGACY_PREFERENCES_KEY,
  MIGRATED_BY_KEY,
  getPreferencesAs,
  resetPreferencesAs,
} from '../../support/preferences';

const PREFERENCES_URL = '**/api/user/preferences';

function freshUser(label: string): string {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'E2E', 'Tester');
  createOrgWithSubscription(email, `${label} Corp`, uniqueSlug());
  return email;
}

function cleanUp(...emails: string[]) {
  emails.forEach((email) => {
    resetPreferencesAs(email);
    cy.deleteKeycloakUser(email);
  });
}

describe('Server-synced preferences', () => {
  it('a preference changed in Settings is still set after clearing localStorage and logging in again', { tags: '@smoke' }, () => {
    const email = freshUser('prefs-sync');
    cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('button', 'Dark').click();
    cy.get('html').should('have.class', 'dark');
    cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { theme: 'dark' } });
    getPreferencesAs(email).its('theme').should('eq', 'dark');

    // A new browser for the same user: nothing cached, so the theme can only come from the server.
    cy.clearLocalStorage();
    cy.intercept('GET', PREFERENCES_URL).as('loadPreferences');
    cy.loginAs(email);
    cy.visit('/settings');
    cy.wait('@loadPreferences');
    cy.get('html').should('have.class', 'dark');
    cy.contains('button', 'Dark').should('have.class', 'bg-gray-900');

    cleanUp(email);
  });

  it('uploads the legacy local preferences once, and never for a different user in the same browser', () => {
    const first = freshUser('prefs-upload-a');
    const second = freshUser('prefs-upload-b');

    userIdFor(first).then((firstUserId) => {
      cy.intercept('PATCH', PREFERENCES_URL).as('upload');
      cy.loginAs(first);
      cy.visit('/settings', {
        onBeforeLoad(win) {
          // What a browser has from before ADR-0061: one shared key, no marker.
          win.localStorage.setItem(
            LEGACY_PREFERENCES_KEY,
            JSON.stringify({ theme: 'dark', defaultProjectPage: 'JOBS', staleKey: 'dropped' }),
          );
        },
      });

      cy.wait('@upload')
        .its('request.body')
        .should('deep.equal', { ui: { theme: 'dark', defaultProjectPage: 'JOBS' } });
      cy.get('html').should('have.class', 'dark');
      getPreferencesAs(first).should('deep.equal', { theme: 'dark', defaultProjectPage: 'JOBS' });
      cy.window().its('localStorage').invoke('getItem', MIGRATED_BY_KEY).should('eq', firstUserId);
    });

    // Same browser (localStorage kept), a different user with nothing saved yet.
    const patches: unknown[] = [];
    cy.intercept('PATCH', PREFERENCES_URL, (req) => {
      patches.push(req.body);
    });
    cy.intercept('GET', PREFERENCES_URL).as('loadSecond');
    cy.loginAs(second);
    cy.visit('/settings');
    cy.wait('@loadSecond');
    cy.contains('button', 'System').should('have.class', 'bg-gray-900');
    cy.get('html').should('not.have.class', 'dark');
    getPreferencesAs(second).should('deep.equal', {});
    cy.then(() => expect(patches).to.have.length(0));

    cleanUp(first, second);
  });

  it('keeps a change locally and shows one error toast with the server message when saving fails', () => {
    const email = freshUser('prefs-save-error');
    cy.intercept('PATCH', PREFERENCES_URL, {
      statusCode: 400,
      body: {
        status: 400,
        error: 'Bad Request',
        message: 'ui is required and must be an object',
        timestamp: new Date().toISOString(),
      },
    }).as('failedSave');
    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('button', 'Dark').click();
    cy.wait('@failedSave');
    cy.get('[data-sonner-toast]')
      .should('have.length', 1)
      .and('contain.text', "Couldn't save your preferences")
      .and('contain.text', 'ui is required and must be an object');
    cy.get('html').should('have.class', 'dark');

    // The next change retries the failed one too, and doesn't add a second toast.
    cy.contains('button', 'Srpski').click();
    cy.wait('@failedSave').its('request.body').should('deep.equal', { ui: { theme: 'dark', locale: 'sr' } });
    cy.get('[data-sonner-toast]').should('have.length', 1);
    cy.contains('button', 'Tamna').should('have.class', 'bg-gray-900');

    cleanUp(email);
  });
});

describe('Settings section index', () => {
  function expectNearTop(selector: string) {
    cy.get(selector).should(($el) => {
      expect($el[0].getBoundingClientRect().top).to.be.within(-2, 40);
    });
  }

  it('scrolls to a section from the sticky list on desktop', () => {
    const email = freshUser('prefs-index');
    cy.viewport(1280, 720);
    cy.loginAs(email);
    cy.visit('/settings');

    cy.get('[data-testid="settings-section-index"]').within(() => cy.contains('a', 'Notifications').click());
    expectNearTop('#notifications');
    cy.location('hash').should('eq', '#notifications');
    // The list is sticky, so it's still on screen after scrolling.
    cy.get('[data-testid="settings-section-index"]')
      .contains('a', 'Appearance')
      .should(($el) => {
        expect($el[0].getBoundingClientRect().top).to.be.within(0, 720);
      });

    cleanUp(email);
  });

  it('scrolls to a section from the anchor chips on mobile', () => {
    const email = freshUser('prefs-index-mobile');
    cy.viewport('iphone-x');
    cy.loginAs(email);
    cy.visit('/settings');

    cy.get('[data-testid="settings-section-index"]').within(() => cy.contains('a', 'Milestones').click());
    expectNearTop('#milestones');

    cleanUp(email);
  });
});
