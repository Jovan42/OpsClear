// ADR-0061 (JOB-354): the Stats default range and "Show approvals on Overview" preferences.
// Uses cy.loginAs() per docs/dev/process/E2E.md. Every test uses fresh users (not the
// shared seed users) and resets their stored preferences at the end.

import {
  addMember,
  createJobAs,
  createOrgWithFullAccess,
  createProjectAs,
  requestApprovalAs,
  uniqueEmail,
  uniqueSlug,
  userIdFor,
} from '../../support/orgApi';
import { getPreferencesAs, resetPreferencesAs } from '../../support/preferences';

const PREFERENCES_URL = '**/api/user/preferences';
/** The stats request for a range (`weeks` is the first query parameter). */
const statsUrl = (weeks: number) => `**/api/projects/*/stats?weeks=${weeks}&*`;

function cleanUp(email: string) {
  resetPreferencesAs(email);
  cy.deleteKeycloakUser(email);
}

/** The settings row whose label is exactly `label`. */
function settingRow(label: string) {
  return cy.contains('p', new RegExp(`^${label}$`)).parent().parent();
}

describe('Stats default range preference', () => {
  it('Stats opens on the chosen default range, and changing it there keeps the preference', { tags: '@smoke' }, () => {
    const email = uniqueEmail('stats-range');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Range Corp', uniqueSlug());
    createProjectAs(email, 'Range Project').then((projectId) => {
      cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
      cy.loginAs(email);
      cy.visit('/settings');
      settingRow('Default range').within(() => {
        cy.contains('button', '12 weeks').should('have.class', 'bg-gray-900');
        cy.contains('button', '4 weeks').click();
      });
      cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { statsDefaultRange: 4 } });

      cy.intercept('GET', statsUrl(4)).as('stats4');
      cy.intercept('GET', statsUrl(26)).as('stats26');
      cy.clearLocalStorage(); // the default comes from the server, not this browser's cache
      cy.visit(`/projects/${projectId}/stats`);
      cy.wait('@stats4');
      cy.contains('button', '4 weeks').should('have.attr', 'aria-pressed', 'true');

      cy.contains('button', '26 weeks').click();
      cy.wait('@stats26');
      cy.contains('button', '26 weeks').should('have.attr', 'aria-pressed', 'true');
      // The preference is still 4, in the app and on the server.
      cy.visit('/settings');
      settingRow('Default range').within(() => {
        cy.contains('button', '4 weeks').should('have.class', 'bg-gray-900');
      });
      getPreferencesAs(email).its('statsDefaultRange').should('eq', 4);

      cleanUp(email);
    });
  });
});

describe('Overview approvals preference', () => {
  it('an Owner hides the Overview approvals while the nav badge stays; a Member does not see the setting', () => {
    const ownerEmail = uniqueEmail('overview-owner');
    const memberEmail = uniqueEmail('overview-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Overview Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Overview Project').then((projectId) =>
        createJobAs(ownerEmail, projectId, { title: 'Order steel' }).then((jobId) => {
          requestApprovalAs(ownerEmail, projectId, jobId, 'Please approve the order');
          userIdFor(memberEmail).then((memberId) => addMember(orgId, ownerEmail, memberId, 'MEMBER'));

          cy.viewport(1280, 800);
          cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
          cy.loginAs(ownerEmail);
          cy.visit('/overview');
          cy.contains('h2', 'Pending Approvals').should('be.visible');
          cy.contains('Please approve the order').should('be.visible');

          cy.visit('/settings');
          settingRow('Show approvals on Overview').within(() => {
            cy.contains('button', /^On$/).should('have.class', 'bg-gray-900');
            cy.contains('button', /^Off$/).click();
          });
          cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { showOverviewApprovals: false } });

          cy.visit('/overview');
          cy.contains('h1', 'Overview').should('be.visible');
          cy.contains('h2', 'Pending Approvals').should('not.exist');
          cy.contains('Please approve the order').should('not.exist');

          // The nav's pending-approvals badge is unchanged.
          cy.visit(`/projects/${projectId}/jobs`);
          cy.get('nav').contains('a', 'Approvals').should('contain.text', '1');

          cy.loginAs(memberEmail);
          cy.visit('/settings');
          cy.contains('p', 'Default range').should('be.visible');
          cy.contains('Show approvals on Overview').should('not.exist');
        }),
      ),
    );

    cleanUp(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });
});
