// ADR-0062 (JOB-358): screenshots for the Admin & settings guide pages (roles-permissions,
// project-settings, organisation-settings, preferences), in the language given by
// GUIDE_LANG. Not a test of the app: it seeds the sample workspace (support/world.ts) and
// captures what each page shows. Outside cypress/e2e/, so CI never runs it; run it with
// `npm run guide:screenshots` (see cypress/guide/README.md). Images are named `<slug>-<n>`.

import { sendInvite, uniqueEmail } from '../support/orgApi';
import { prepareCapture, shot } from './support/capture';
import { tr } from './support/i18n';
import { visitAs } from './support/steps';
import { cleanUpWorld, emptyWorld, seedWorld } from './support/world';

const lang = Cypress.env('GUIDE_LANG') ?? 'en';
const w = emptyWorld();

describe(`Guide screenshots: Admin & settings (${lang})`, { testIsolation: false }, () => {
  beforeEach(prepareCapture);
  before(() => {
    seedWorld(w);
    // Two invites still waiting, for the invites page
    cy.then(() => sendInvite(w.orgId, w.owner.email, uniqueEmail('guide-invited')));
    cy.then(() => sendInvite(w.orgId, w.owner.email, uniqueEmail('guide-invited2')));
  });
  after(() => cleanUpWorld(w));

  const settingsPath = () => `/projects/${w.projectId}/settings`;

  /** Scrolls the element into view and captures the viewport as it is. */
  function shotAt(slug: string, n: number, selector: string) {
    cy.get(selector).scrollIntoView();
    cy.get(selector).should('be.visible');
    shot(slug, n, undefined, { scroll: false });
  }

  it('roles-permissions: organisation members and project members', () => {
    visitAs(w.owner.email, '/org/members');
    cy.contains('td', w.admin.last).should('be.visible');
    shot('roles-permissions', 1);

    visitAs(w.owner.email, settingsPath());
    cy.contains('td', w.member.last).should('be.visible');
    shot('roles-permissions', 2);
  });

  it('project-settings: the settings page, completion approval and job sections', () => {
    visitAs(w.owner.email, settingsPath());
    cy.get('[data-testid="completion-approval-setting"]').should('be.visible');
    shot('project-settings', 1);

    shotAt('project-settings', 2, '[data-testid="completion-approval-setting"]');
    shotAt('project-settings', 3, '[data-testid="job-sections-setting"]');
  });

  it('organisation-settings: settings, ID prefixes, members and invites', () => {
    visitAs(w.owner.email, '/org/settings');
    cy.get('input').first().should('have.value', 'Acme Plumbing');
    shot('organisation-settings', 1);

    cy.contains(tr('ID prefixes')).scrollIntoView();
    cy.contains(tr('ID prefixes')).should('be.visible');
    shot('organisation-settings', 2, undefined, { scroll: false });

    visitAs(w.owner.email, '/org/members');
    cy.contains('td', w.teammate.last).should('be.visible');
    shot('organisation-settings', 3);

    visitAs(w.owner.email, '/org/invites');
    cy.get('input[type=email]').should('be.visible');
    cy.get('td').should('have.length.greaterThan', 2);
    shot('organisation-settings', 4);
  });

  it('preferences: the settings page, its section index and the preferences', () => {
    visitAs(w.owner.email, '/settings');
    cy.get('[data-testid="settings-section-index"]').should('be.visible');
    shot('preferences', 1);

    shot('preferences', 2, '[data-testid="settings-section-index"]');

    shotAt('preferences', 3, '[data-testid="notification-type-settings"]');
  });
});
