// ADR-0050 (Job completion velocity stats), JOB-272: the Stats page with the team view
// and the Member "Your stats" view. Uses cy.loginAs() per docs/dev/process/E2E.md.
//
// The metric definitions (lead/cycle/blocked time, late rate, reopened jobs, week
// filling, Member scoping, 400/403 rules) are covered by StatsServiceTest and the
// stats integration tests on the backend. This spec covers what only the frontend
// exercises: nav entry, tiles, chart, range switching, the Member title, the empty
// state, add-on gating and the load error. Exact duration formatting isn't asserted:
// jobs completed seconds after creation all read "0 minutes", which proves nothing.
// The per-member breakdown and drill-down ship with Frontend 2 (JOB-273).

import {
  uniqueEmail,
  uniqueSlug,
  userIdFor,
  createOrgWithFullAccess,
  createOrgWithSubscription,
  createProjectAs,
  addMember,
  addProjectMember,
  createJobAs,
  updateJobStatusAs,
} from '../../support/orgApi';

const STATS_URL = '**/api/projects/*/stats*';

/** The big number on the tile whose label is exactly `label`. */
function tileValue(label: string) {
  return cy
    .contains('[data-testid="stats-tile"] span', new RegExp(`^${label}$`))
    .parent()
    .find('span')
    .first();
}

function completeJob(email: string, projectId: string, body: Parameters<typeof createJobAs>[2]) {
  return createJobAs(email, projectId, body).then((jobId) =>
    updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS').then(() =>
      updateJobStatusAs(email, projectId, jobId, 'COMPLETED'),
    ),
  );
}

describe('Stats page', () => {
  it('an owner opens Stats from the nav, sees the team tiles and weekly chart, and switching to 4 weeks updates the chart', { tags: '@smoke' }, () => {
    const email = uniqueEmail('stats-owner');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Quasar Corp', uniqueSlug());
    createProjectAs(email, 'Quasar Stats Project').then((projectId) => {
      const past = new Date(Date.now() - 60 * 60_000).toISOString();
      completeJob(email, projectId, { title: 'Done late', deadline: past });
      completeJob(email, projectId, { title: 'Done without deadline' });

      cy.intercept('GET', STATS_URL).as('statsFetch');
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/dashboard`);
      cy.get(`a[href="/projects/${projectId}/stats"]`).filter(':visible').first().click();
      cy.url().should('include', `/projects/${projectId}/stats`);

      cy.wait('@statsFetch').its('request.url').should((url: string) => {
        expect(url).to.include('weeks=12');
        expect(url).to.match(/tz=[^&]+/);
      });

      cy.contains('h1', 'Team stats').should('be.visible');
      tileValue('Completed').should('have.text', '2');
      tileValue('Completed late').should('have.text', '100%');
      cy.contains('1 of 1 with a deadline').should('be.visible');
      cy.get('[data-testid="stats-chart"]').should('have.attr', 'data-weeks', '12');
      cy.get('[data-testid="stats-chart"] .recharts-surface').should('be.visible');
      cy.contains('No jobs were completed in this range.').should('not.exist');

      cy.contains('button', '4 weeks').click();
      cy.wait('@statsFetch').its('request.url').should('include', 'weeks=4');
      cy.contains('button', '4 weeks').should('have.attr', 'aria-pressed', 'true');
      cy.get('[data-testid="stats-chart"]').should('have.attr', 'data-weeks', '4');
      tileValue('Completed').should('have.text', '2');
    });

    cy.deleteKeycloakUser(email);
  });

  it('a member sees "Your stats" with only their own completed jobs, not the team totals', () => {
    const ownerEmail = uniqueEmail('stats-member-owner');
    const memberEmail = uniqueEmail('stats-member-member');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Tester');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Tester');
    createOrgWithFullAccess(ownerEmail, 'Pulsar Corp', uniqueSlug()).then((orgId) =>
      createProjectAs(ownerEmail, 'Pulsar Stats Project').then((projectId) =>
        userIdFor(memberEmail).then((memberId) => {
          addMember(orgId, ownerEmail, memberId, 'MEMBER');
          addProjectMember(projectId, ownerEmail, memberId, 'MEMBER');

          completeJob(ownerEmail, projectId, { title: 'Member job', assignedTo: memberId });
          completeJob(ownerEmail, projectId, { title: 'Unassigned job' });
          completeJob(ownerEmail, projectId, { title: 'Another unassigned job' });

          cy.loginAs(memberEmail);
          cy.visit(`/projects/${projectId}/stats`);
          cy.contains('h1', 'Your stats').should('be.visible');
          cy.contains('Team stats').should('not.exist');
          tileValue('Completed').should('have.text', '1');
        }),
      ),
    );

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
  });

  it('a project with no completions in range shows the empty state, zero/"—" tiles and the empty weeks', () => {
    const email = uniqueEmail('stats-empty');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Nebula Corp', uniqueSlug());
    createProjectAs(email, 'Nebula Stats Project').then((projectId) => {
      createJobAs(email, projectId, { title: 'Still open' }).then((jobId) =>
        updateJobStatusAs(email, projectId, jobId, 'IN_PROGRESS'),
      );

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/stats`);
      cy.contains('No jobs were completed in this range.').should('be.visible');
      tileValue('Completed').should('have.text', '0');
      tileValue('Avg. total time').should('have.text', '—');
      tileValue('Avg. working time').should('have.text', '—');
      tileValue('Avg. blocked time').should('have.text', '—');
      tileValue('Completed late').should('have.text', '—');
      cy.get('[data-testid="stats-chart"]').should('have.attr', 'data-weeks', '12');
    });

    cy.deleteKeycloakUser(email);
  });

  it('an org without the DASHBOARD add-on sees a locked Stats tab and an upgrade card, and never fetches', () => {
    const email = uniqueEmail('stats-locked');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Meteor Corp', uniqueSlug());
    createProjectAs(email, 'Meteor Stats Project').then((projectId) => {
      cy.intercept('GET', STATS_URL).as('statsFetch');
      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/jobs`);
      cy.get(`a[href="/projects/${projectId}/stats"]`).filter(':visible').first().within(() => {
        cy.contains('Stats');
        cy.get('svg').should('exist');
      });

      cy.visit(`/projects/${projectId}/stats`);
      cy.contains('h2', 'Stats').should('be.visible');
      cy.contains('button, a', 'Upgrade').should('exist');
      cy.get('@statsFetch.all').should('have.length', 0);
    });

    cy.deleteKeycloakUser(email);
  });

  it('a failed stats load shows PageError with a working retry', () => {
    const email = uniqueEmail('stats-error');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithFullAccess(email, 'Corona Corp', uniqueSlug());
    createProjectAs(email, 'Corona Stats Project').then((projectId) => {
      // The QueryClient retries once (main.tsx), so every request must fail until the
      // flag flips, or the automatic retry would hide the error state.
      let failing = true;
      cy.intercept('GET', STATS_URL, (req) => {
        if (failing) {
          req.reply({ statusCode: 500, body: { error: 'boom' } });
        } else {
          req.continue();
        }
      });

      cy.loginAs(email);
      cy.visit(`/projects/${projectId}/stats`);
      cy.contains('Failed to load stats.').should('be.visible');
      cy.then(() => { failing = false; });
      cy.contains('button', 'Try again').click();
      cy.contains('Failed to load stats.').should('not.exist');
      tileValue('Completed').should('have.text', '0');
    });

    cy.deleteKeycloakUser(email);
  });
});
