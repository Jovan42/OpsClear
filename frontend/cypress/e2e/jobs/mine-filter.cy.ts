// ADR-0061 (JOB-353): the Everyone / Mine filter on the job list and its default preference.
// Mine keeps the jobs the user is responsible for, in the browser, and the status tab counts
// follow it. Uses cy.loginAs() per docs/dev/process/E2E.md. Every test uses a fresh user and
// resets their stored preferences (PATCH with nulls) at the end.

import {
  createJobAs,
  createOrgWithSubscription,
  createProjectAs,
  uniqueEmail,
  uniqueSlug,
  updateJobStatusAs,
  userIdFor,
} from '../../support/orgApi';
import { resetPreferencesAs } from '../../support/preferences';

const PREFERENCES_URL = '**/api/user/preferences';

/** A user with two jobs assigned to them (one New, one In Progress) and one unassigned job. */
function userWithMixedJobs(label: string) {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'E2E', 'Tester');
  createOrgWithSubscription(email, `${label} Corp`, uniqueSlug());
  return userIdFor(email).then((userId) =>
    createProjectAs(email, `${label} Project`).then((projectId) => {
      createJobAs(email, projectId, { title: 'My new job', assignedTo: userId });
      createJobAs(email, projectId, { title: 'My started job', assignedTo: userId }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS'),
      );
      return createJobAs(email, projectId, { title: 'Nobody owns this' }).then(() => ({ email, projectId }));
    }),
  );
}

/** The count badge on a status tab. */
function tabCount(label: string) {
  return cy.get('@tabBar').contains('button', label).find('span').last();
}

function cleanUp(email: string) {
  resetPreferencesAs(email);
  cy.deleteKeycloakUser(email);
}

describe('Everyone / Mine job filter', () => {
  it('Mine shows only my jobs and the tab counts follow it', { tags: '@smoke' }, () => {
    userWithMixedJobs('mine').then(({ email, projectId }) => {
      cy.viewport(1280, 800);
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);

      cy.contains('button', 'All').parent().as('tabBar');
      cy.get('[data-testid="assignee-filter-everyone"]').should('have.attr', 'aria-pressed', 'true');
      tabCount('All').should('have.text', '3');
      cy.get('[data-testid="job-table"]').should('contain.text', 'Nobody owns this');

      cy.get('[data-testid="assignee-filter-mine"]').click();
      cy.get('[data-testid="assignee-filter-mine"]').should('have.attr', 'aria-pressed', 'true');
      tabCount('All').should('have.text', '2');
      tabCount('New').should('have.text', '1');
      tabCount('In Progress').should('have.text', '1');
      cy.get('[data-testid="job-table"]').should('contain.text', 'My new job').and('contain.text', 'My started job');
      cy.get('[data-testid="job-table"]').should('not.contain.text', 'Nobody owns this');

      // Switching on the page doesn't change the preference: a fresh visit opens on Everyone.
      cy.visit(`/projects/${projectId}/jobs`);
      cy.get('[data-testid="assignee-filter-everyone"]').should('have.attr', 'aria-pressed', 'true');
      cy.contains('button', 'All').parent().as('tabBar');
      tabCount('All').should('have.text', '3');

      cleanUp(email);
    });
  });

  it('opens on Mine after setting it as the default', () => {
    userWithMixedJobs('mine-default').then(({ email, projectId }) => {
      cy.viewport(1280, 800);
      cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
      cy.loginAs(email);
      cy.visit('/settings');
      cy.contains('p', 'Default assignee filter').parent().parent().contains('button', 'Mine').click();
      cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { defaultAssigneeFilter: 'MINE' } });

      cy.visit(`/projects/${projectId}/jobs`);
      cy.get('[data-testid="assignee-filter-mine"]').should('have.attr', 'aria-pressed', 'true');
      cy.contains('button', 'All').parent().as('tabBar');
      tabCount('All').should('have.text', '2');
      cy.get('[data-testid="job-table"]').should('not.contain.text', 'Nobody owns this');

      cy.reload();
      cy.get('[data-testid="assignee-filter-mine"]').should('have.attr', 'aria-pressed', 'true');
      cy.get('[data-testid="job-table"]').should('contain.text', 'My new job').and('not.contain.text', 'Nobody owns this');

      cleanUp(email);
    });
  });
});
