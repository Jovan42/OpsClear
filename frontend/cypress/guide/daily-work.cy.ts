// ADR-0062 (JOB-358): screenshots for the Daily work guide pages (finding-jobs, dashboard,
// notifications, notes-links-history), in the language given by GUIDE_LANG. Not a test of
// the app: it seeds the sample workspace (support/world.ts) and captures what each page
// shows. Outside cypress/e2e/, so CI never runs it; run it with `npm run guide:screenshots`
// (see cypress/guide/README.md). Images are named `<slug>-<n>` after the guide page.

import { prepareCapture, shot } from './support/capture';
import { exact, tr } from './support/i18n';
import { visitAs } from './support/steps';
import { cleanUpWorld, emptyWorld, seedWorld } from './support/world';

const lang = Cypress.env('GUIDE_LANG') ?? 'en';
const w = emptyWorld();

describe(`Guide screenshots: Daily work (${lang})`, { testIsolation: false }, () => {
  beforeEach(prepareCapture);
  before(() => seedWorld(w));
  after(() => cleanUpWorld(w));

  const jobsPath = () => `/projects/${w.projectId}/jobs`;

  it('finding-jobs: the list, a status tab, Mine, search and sorting', () => {
    visitAs(w.owner.email, jobsPath());
    cy.get('[data-testid="job-table"]').should('be.visible');
    cy.contains('td', 'Run the new water pipes').should('be.visible');
    shot('finding-jobs', 1);

    cy.contains('button', tr('In Progress')).click();
    cy.contains('td', 'Run the new water pipes').should('be.visible');
    shot('finding-jobs', 2);

    // As a team member: only the jobs assigned to me
    visitAs(w.teammate.email, jobsPath());
    cy.get('[data-testid="assignee-filter-mine"]').click();
    cy.contains('td', 'Fit the kitchen sink').should('be.visible');
    shot('finding-jobs', 3);

    visitAs(w.owner.email, jobsPath());
    cy.get('input[type="search"], input[placeholder]').first().type('gas');
    cy.contains('td', 'Cap the old gas supply').should('be.visible');
    shot('finding-jobs', 4);

    visitAs(w.owner.email, jobsPath());
    cy.contains('th', tr('Deadline')).click();
    cy.get('[data-testid="job-deadline"]').should('have.length.greaterThan', 3);
    shot('finding-jobs', 5);
  });

  it('dashboard: the project dashboard', () => {
    visitAs(w.owner.email, `/projects/${w.projectId}/dashboard`);
    cy.contains(tr('Completed')).should('be.visible');
    shot('dashboard', 1, undefined, { settle: 2000 });
    cy.scrollTo('bottom', { duration: 0 });
    shot('dashboard', 2, undefined, { scroll: false });
  });

  it('notifications: the bell, the page, filters and mute settings', () => {
    visitAs(w.owner.email, '/projects');
    cy.get('[data-testid="notification-badge"]').should('be.visible');
    cy.get('[data-testid="notification-bell"]').click();
    cy.get('[data-testid="notification-item"]').should('have.length.greaterThan', 2);
    shot('notifications', 1);

    cy.visit('/notifications');
    cy.get('[data-testid="notification-list"]').should('be.visible');
    shot('notifications', 2);

    cy.contains('button', exact('Unread', 1)).click();
    cy.get('[data-testid="notification-list"]').should('be.visible');
    shot('notifications', 3);

    cy.visit('/settings');
    cy.get('[data-testid="notification-type-settings"]').scrollIntoView();
    cy.get('[data-testid="notification-type-settings"]').should('be.visible');
    shot('notifications', 4, undefined, { scroll: false });
  });

  it('notes-links-history: the sections of a job', () => {
    visitAs(w.owner.email, `/projects/${w.projectId}/jobs/${w.jobs.pipes}`);
    cy.contains('Parts delivered').should('be.visible');
    shot('notes-links-history', 1);

    cy.contains('Parts delivered').scrollIntoView();
    shot('notes-links-history', 2, undefined, { scroll: false });

    cy.contains('Pipe layout drawing').should('be.visible');
    cy.scrollTo('bottom', { duration: 0 });
    shot('notes-links-history', 3, undefined, { scroll: false });
  });
});
