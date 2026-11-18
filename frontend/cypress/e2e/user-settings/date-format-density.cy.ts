// ADR-0061 (JOB-352): the date format preference (one shared formatter for every date) and
// row density on the desktop job table. Uses cy.loginAs() per docs/dev/process/E2E.md. Every
// test uses a fresh user and resets their stored preferences (PATCH with nulls) at the end.
//
// Not covered here: each of the ~25 screens that now use the shared formatter (the unit tests
// cover every format in both languages, and the PR's manual list covers the main screens).

import { createJobAs, createOrgWithSubscription, createProjectAs, uniqueEmail, uniqueSlug } from '../../support/orgApi';
import { resetPreferencesAs } from '../../support/preferences';

const PREFERENCES_URL = '**/api/user/preferences';

/** UTC midnight of the 15th, three months ahead: how a date-only deadline is stored. */
function deadlineIso(): string {
  const d = new Date();
  d.setUTCHours(0, 0, 0, 0);
  d.setUTCDate(15);
  d.setUTCMonth(d.getUTCMonth() + 3);
  return d.toISOString();
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Sets up a user with one project and one job with a deadline; yields the project id. */
function userWithJob(label: string, title: string, deadline: string) {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'E2E', 'Tester');
  createOrgWithSubscription(email, `${label} Corp`, uniqueSlug());
  return createProjectAs(email, `${label} Project`).then((projectId) =>
    createJobAs(email, projectId, { title, deadline }).then(() => ({ email, projectId })),
  );
}

function cleanUp(email: string) {
  resetPreferencesAs(email);
  cy.deleteKeycloakUser(email);
}

describe('Date format preference', () => {
  it('shows a job deadline as DD.MM.YYYY after choosing that format', { tags: '@smoke' }, () => {
    const deadline = deadlineIso();
    userWithJob('date-dmy', 'Pour foundation', deadline).then(({ email, projectId }) => {
      cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
      cy.loginAs(email);
      cy.visit('/settings');
      cy.get('select[aria-label="Date format"]').select('DMY_DOT');
      cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { dateFormat: 'DMY_DOT' } });

      cy.visit(`/projects/${projectId}/jobs`);
      // Deadlines show in the viewer's zone, so work out the expected day the same way.
      const d = new Date(deadline);
      const expected = `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()}`;
      cy.get('[data-testid="job-table"]').contains('tr', 'Pour foundation').should('contain.text', expected);

      cleanUp(email);
    });
  });

  it('on Auto, follows the app language: a Serbian month style after switching to Srpski', () => {
    const deadline = deadlineIso();
    userWithJob('date-auto-sr', 'Install windows', deadline).then(({ email, projectId }) => {
      cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
      cy.loginAs(email);
      cy.visit('/settings');
      cy.get('select[aria-label="Date format"]').should('have.value', 'AUTO');
      cy.contains('button', 'Srpski').click();
      cy.wait('@savePreferences');

      cy.visit(`/projects/${projectId}/jobs`);
      // sr-Latn short dates read "15. mar 2027.": day with a dot, lowercase month, year with a dot.
      const d = new Date(deadline);
      const serbian = new RegExp(`${d.getDate()}\\. [a-zčćšđž]+\\.? ${d.getFullYear()}\\.`);
      cy.get('[data-testid="job-table"]')
        .contains('tr', 'Install windows')
        .invoke('text')
        .should('match', serbian);

      cleanUp(email);
    });
  });
});

describe('Row density preference', () => {
  it('compact density shrinks the desktop job table rows and text', () => {
    userWithJob('density', 'Paint walls', deadlineIso()).then(({ email, projectId }) => {
      cy.viewport(1280, 800);
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);

      // Comfortable (the default).
      cy.get('[data-testid="job-table"]').should('have.class', 'text-sm');
      cy.get('[data-testid="job-table"]').contains('tr', 'Paint walls').find('td').first().should('have.class', 'py-3');
      cy.get('[data-testid="job-table"]')
        .contains('tr', 'Paint walls')
        .invoke('outerHeight')
        .then((comfortableHeight) => {
          cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
          cy.visit('/settings');
          cy.contains('button', 'Compact').click();
          cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { rowDensity: 'COMPACT' } });

          cy.visit(`/projects/${projectId}/jobs`);
          cy.get('[data-testid="job-table"]').should('have.class', 'text-xs');
          cy.get('[data-testid="job-table"]').contains('tr', 'Paint walls').find('td').first().should('have.class', 'py-1.5');
          cy.get('[data-testid="job-table"]')
            .contains('tr', 'Paint walls')
            .invoke('outerHeight')
            .should('be.lessThan', comfortableHeight as unknown as number);
        });

      cleanUp(email);
    });
  });
});
