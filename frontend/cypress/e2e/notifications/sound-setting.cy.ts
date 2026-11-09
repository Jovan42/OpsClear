// JOB-282: the "Notification sound" preference on the Settings page. Uses cy.loginAs()
// per docs/dev/process/E2E.md.
//
// Only checks that the toggle persists. Audio output isn't asserted (Cypress can't hear
// it); when the chime plays is unit tested (notificationSound.test.ts) and on the PR's
// manual checklist.

import { createOrgWithSubscription, uniqueEmail, uniqueSlug } from '../../support/orgApi';

const STORAGE_KEY = 'opsclear:preferences';

function storedSound(win: Window): unknown {
  return JSON.parse(win.localStorage.getItem(STORAGE_KEY) ?? '{}').notificationSound;
}

describe('Notification sound setting', () => {
  it('defaults to On, and turning it off persists across a reload', () => {
    const email = uniqueEmail('notif-sound');
    cy.createKeycloakUser(email, 'Sam', 'Sound');
    createOrgWithSubscription(email, 'Sound Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('Notification sound')
      .parents('div.flex')
      .first()
      .within(() => {
        cy.contains('button', 'On').should('have.class', 'bg-gray-900');
        cy.contains('button', 'Off').click();
        cy.contains('button', 'Off').should('have.class', 'bg-gray-900');
      });
    cy.window().then((win) => expect(storedSound(win)).to.eq(false));

    cy.reload();
    cy.contains('Notification sound')
      .parents('div.flex')
      .first()
      .within(() => {
        cy.contains('button', 'Off').should('have.class', 'bg-gray-900');
        cy.contains('button', 'On').click();
      });
    cy.window().then((win) => expect(storedSound(win)).to.eq(true));

    cy.deleteKeycloakUser(email);
  });
});
