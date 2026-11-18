// ADR-0061 (JOB-354): the landing project preference. After login (no saved deep link) the
// app opens the project list, the last opened project or a chosen project, on the user's
// default project page, and falls back to the list when that project can't be opened.
// Uses cy.loginAs() per docs/dev/process/E2E.md; "logging in again" is a fresh visit to `/`
// with the browser cache cleared, so the choice has to come from the server.
//
// Every test uses fresh users (not the shared seed users) and resets their stored
// preferences at the end. Not covered: an archived landing project (same fallback as a
// project the user can't access, both are missing from the user's active projects).

import { POST_LOGIN_REDIRECT_KEY } from '../../../src/auth/postLoginRedirect';
import { createOrgWithSubscription, createProjectAs, getProjectAs, uniqueEmail, uniqueSlug } from '../../support/orgApi';
import { resetPreferencesAs, setPreferencesAs } from '../../support/preferences';

const PREFERENCES_URL = '**/api/user/preferences';

/** A fresh user with two projects; yields their friendly ids. */
function userWithTwoProjects(label: string) {
  const email = uniqueEmail(label);
  cy.createKeycloakUser(email, 'E2E', 'Tester');
  createOrgWithSubscription(email, `${label} Corp`, uniqueSlug());
  return createProjectAs(email, 'Alpha Project').then((alpha) =>
    createProjectAs(email, 'Beta Project').then((beta) => ({ email, alpha, beta })),
  );
}

function cleanUp(email: string) {
  resetPreferencesAs(email);
  cy.deleteKeycloakUser(email);
}

/** Logs in again: empty browser cache, then the post-login landing at `/`. */
function logInAgain(email: string, onBeforeLoad?: (win: Window) => void) {
  cy.clearLocalStorage();
  cy.loginAs(email);
  cy.visit('/', { onBeforeLoad });
}

describe('Landing project preference', () => {
  it('a chosen project opens on the default project page after logging in again', { tags: '@smoke' }, () => {
    userWithTwoProjects('landing-specific').then(({ email, beta }) => {
      setPreferencesAs(email, { defaultProjectPage: 'JOBS' });
      cy.intercept('PATCH', PREFERENCES_URL).as('savePreferences');
      cy.loginAs(email);
      cy.visit('/settings');

      cy.get('select[aria-label="Landing project"]').should('have.value', 'LIST').select('SPECIFIC');
      cy.wait('@savePreferences').its('request.body').should('deep.equal', { ui: { landingProject: 'SPECIFIC' } });
      cy.get('select[aria-label="Project"] option:checked').should('have.text', 'Choose a project…');
      cy.get('select[aria-label="Project"]').select('Beta Project');
      cy.wait('@savePreferences')
        .its('request.body.ui.landingProjectId')
        .should('match', /^[0-9a-f-]{36}$/);

      logInAgain(email);
      cy.location('pathname').should('eq', `/projects/${beta}/jobs`);

      cleanUp(email);
    });
  });

  it('a saved deep link still wins over the landing project', () => {
    userWithTwoProjects('landing-deeplink').then(({ email, alpha, beta }) => {
      getProjectAs(email, beta).then((project: { id: string }) => {
        setPreferencesAs(email, { landingProject: 'SPECIFIC', landingProjectId: project.id });
      });

      logInAgain(email, (win) => win.sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, `/projects/${alpha}/milestones`));
      cy.location('pathname').should('eq', `/projects/${alpha}/milestones`);

      cleanUp(email);
    });
  });

  it('Last opened lands on the most recently opened project', () => {
    userWithTwoProjects('landing-last').then(({ email, alpha, beta }) => {
      setPreferencesAs(email, { landingProject: 'LAST', defaultProjectPage: 'JOBS' });
      cy.loginAs(email);
      cy.visit(`/projects/${beta}/jobs`);
      cy.contains('Beta Project').should('exist');
      cy.visit(`/projects/${alpha}/jobs`);
      cy.contains('Alpha Project').should('exist');

      // Recent projects are kept in this browser, so only the landing itself runs again.
      cy.visit('/');
      cy.location('pathname').should('eq', `/projects/${alpha}/jobs`);

      cleanUp(email);
    });
  });

  it('a project the user cannot access falls back to the project list', () => {
    const outsider = uniqueEmail('landing-outsider');
    cy.createKeycloakUser(outsider, 'E2E', 'Tester');
    createOrgWithSubscription(outsider, 'Outsider Corp', uniqueSlug());
    createProjectAs(outsider, 'Outsider Project').then((outsiderProject) =>
      getProjectAs(outsider, outsiderProject).then((project: { id: string }) => {
        userWithTwoProjects('landing-noaccess').then(({ email }) => {
          setPreferencesAs(email, { landingProject: 'SPECIFIC', landingProjectId: project.id });

          logInAgain(email);
          cy.location('pathname').should('eq', '/projects');
          cy.contains('Alpha Project').should('be.visible');

          cleanUp(email);
        });
      }),
    );
    cy.deleteKeycloakUser(outsider);
  });
});
