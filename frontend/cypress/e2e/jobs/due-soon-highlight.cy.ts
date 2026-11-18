// ADR-0061 (JOB-353): the due-soon deadline highlight. Amber text plus a "Due soon" hint on
// the job list (table and cards), job detail and dashboard rows; overdue stays red; Off turns
// it off. Uses cy.loginAs() per docs/dev/process/E2E.md. Every test uses a fresh user and
// resets their stored preferences (PATCH with nulls) at the end.
//
// Not covered here: each threshold option (1 / 3 / 5 / 7). The day boundaries are covered by
// the deadlineState unit tests; this spec checks the default (3) and Off end to end.

import {
  createJobAs,
  createOrgWithFullAccess,
  createProjectAs,
  uniqueEmail,
  uniqueSlug,
  updateJobStatusAs,
} from '../../support/orgApi';
import { resetPreferencesAs } from '../../support/preferences';

const PREFERENCES_URL = '**/api/user/preferences';
const DAY_MS = 86_400_000;

/** A date-only deadline (UTC midnight, as stored) `days` after the viewer's local today. */
function deadlineInDays(days: number): string {
  const now = new Date();
  return new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate() + days)).toISOString();
}

/** Two full UTC days back, so wall-clock timing can't make it not overdue yet. */
function overdueDeadline(): string {
  const d = new Date(Date.now() - 2 * DAY_MS);
  d.setUTCHours(0, 0, 0, 0);
  return d.toISOString();
}

/** A user with one project holding a due-tomorrow, an overdue, a far-off and a completed job. */
function userWithDeadlines(label: string) {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'E2E', 'Tester');
  createOrgWithFullAccess(email, `${label} Corp`, uniqueSlug());
  return createProjectAs(email, `${label} Project`).then((projectId) => {
    createJobAs(email, projectId, { title: 'Due tomorrow', deadline: deadlineInDays(1) });
    createJobAs(email, projectId, { title: 'Already late', deadline: overdueDeadline() });
    createJobAs(email, projectId, { title: 'Next month', deadline: deadlineInDays(30) });
    createJobAs(email, projectId, { title: 'Done early', deadline: deadlineInDays(1) }).then((jobId) =>
      updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
        updateJobStatusAs(email, projectId, jobId, 'COMPLETED'),
      ),
    );
    return createJobAs(email, projectId, { title: 'Stuck tomorrow', deadline: deadlineInDays(1) }).then((jobId) =>
      updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS')
        .then(() => updateJobStatusAs(email, projectId, jobId, 'BLOCKED', 'Waiting on parts'))
        .then(() => ({ email, projectId })),
    );
  });
}

function row(title: string) {
  return cy.get('[data-testid="job-table"]').contains('tr', title);
}

function cleanUp(email: string) {
  resetPreferencesAs(email);
  cy.deleteKeycloakUser(email);
}

describe('Due-soon deadline highlight', () => {
  it('marks a job due tomorrow as due soon at the default threshold, and not once it is Off', { tags: '@smoke' }, () => {
    userWithDeadlines('due-soon').then(({ email, projectId }) => {
      cy.viewport(1280, 800);
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);

      row('Due tomorrow').find('[data-testid="due-soon-hint"]').should('have.text', 'Due soon');
      row('Due tomorrow').find('[data-testid="job-deadline"]').should('have.class', 'text-amber-600');
      // Overdue stays red and wins; far-off and completed jobs aren't highlighted.
      row('Already late').find('[data-testid="job-deadline"]').should('have.class', 'text-red-600');
      row('Already late').find('[data-testid="due-soon-hint"]').should('not.exist');
      row('Next month').find('[data-testid="due-soon-hint"]').should('not.exist');
      row('Done early').find('[data-testid="due-soon-hint"]').should('not.exist');
      row('Done early').find('[data-testid="job-deadline"]').should('not.have.class', 'text-amber-600');

      cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
      cy.visit('/settings');
      cy.get('select[aria-label="Due-soon highlight"]').should('have.value', '3').select('0');
      cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { dueSoonDays: 0 } });

      cy.visit(`/projects/${projectId}/jobs`);
      row('Due tomorrow').should('be.visible');
      cy.get('[data-testid="due-soon-hint"]').should('not.exist');
      row('Due tomorrow').find('[data-testid="job-deadline"]').should('not.have.class', 'text-amber-600');
      row('Already late').find('[data-testid="job-deadline"]').should('have.class', 'text-red-600');

      cleanUp(email);
    });
  });

  it('shows the hint on the mobile cards, the job detail and the dashboard', () => {
    userWithDeadlines('due-soon-views').then(({ email, projectId }) => {
      cy.loginAs(email);

      cy.viewport('iphone-x');
      cy.visit(`/projects/${projectId}/jobs`);
      cy.contains('button', 'Due tomorrow').find('[data-testid="due-soon-hint"]').should('be.visible');
      cy.contains('button', 'Already late').find('[data-testid="job-deadline"]').should('have.class', 'text-red-600');

      cy.viewport(1280, 800);
      cy.visit(`/projects/${projectId}/jobs`);
      row('Due tomorrow').click();
      cy.get('[data-testid="job-deadline"]').should('have.class', 'text-amber-600');
      cy.get('[data-testid="due-soon-hint"]').should('have.text', 'Due soon');

      cy.visit(`/projects/${projectId}/dashboard`);
      // A blocked job due soon shows its deadline with the hint; the overdue row's deadline is red.
      cy.contains('h2', 'Blocked').parent().parent().contains('div', 'Stuck tomorrow')
        .parent()
        .find('[data-testid="due-soon-hint"]')
        .should('be.visible');
      cy.contains('h2', 'Overdue').parent().parent().contains('div', 'Already late')
        .parent()
        .find('[data-testid="job-deadline"]')
        .should('have.class', 'text-red-600');

      cleanUp(email);
    });
  });
});
