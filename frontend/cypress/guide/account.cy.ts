// ADR-0062 (JOB-358): screenshots for the Account & more guide pages (subscription,
// stats-overview, api-keys, faq), in the language given by GUIDE_LANG. Not a test of the
// app: it seeds the sample workspace (support/world.ts) and captures what each page shows.
// Outside cypress/e2e/, so CI never runs it; run it with `npm run guide:screenshots` (see
// cypress/guide/README.md). Images are named `<slug>-<n>`.
//
// No live Paddle call anywhere: the sample org's subscription is a database row, and the
// billing history is a stubbed reply.

import { API, createOrgWithSubscription, tokenFor, uniqueSlug } from '../support/orgApi';
import { setPreferencesAs } from '../support/preferences';
import { prepareCapture, shot } from './support/capture';
import { exact, tr } from './support/i18n';
import { clickButton, dialog, visitAs } from './support/steps';
import { guideEmail, guideLang, PROJECT_NAME } from './support/sample';
import { cleanUpWorld, emptyWorld, seedWorld } from './support/world';

const lang = guideLang();
const w = emptyWorld();
// An owner whose organisation has no add-ons: what a locked feature looks like
const locked = { email: guideEmail('locked'), orgId: '' };

describe(`Guide screenshots: Account & more (${lang})`, { testIsolation: false }, () => {
  beforeEach(prepareCapture);
  before(() => {
    seedWorld(w);
    cy.createKeycloakUser(locked.email, 'Sam', 'Okoro');
    cy.then(() => createOrgWithSubscription(locked.email, 'Bright Spark Electrics', uniqueSlug())).then((id) => {
      locked.orgId = id;
    });
    cy.then(() => setPreferencesAs(locked.email, { locale: lang, theme: 'light' }));
    // API keys: made through the API so no key is ever on screen, only the list
    for (const name of ['Dashboard screen', 'Accounting export']) {
      cy.then(() => tokenFor(w.owner.email)).then((token) =>
        cy.request({
          method: 'POST',
          url: `${API}/api/user/api-keys`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name },
        }),
      );
    }
  });
  after(() => {
    cleanUpWorld(w);
    cy.deleteKeycloakUser(locked.email);
  });

  it('subscription: the plan, billing history and a locked feature', () => {
    visitAs(w.owner.email, '/org/settings');
    cy.contains(tr('Billing history')).should('be.visible');
    cy.contains('Recurring scheduling').scrollIntoView();
    shot('subscription', 1, undefined, { scroll: false });

    // Billing history: a stubbed reply, as a live call would need a Paddle account
    cy.intercept('GET', '**/subscription/paddle/transactions', {
      statusCode: 200,
      body: [
        { id: 'txn_1', status: 'completed', billedAt: `${new Date().getFullYear()}-09-01T00:00:00Z`, currency: 'EUR', totalAmount: 153 },
        { id: 'txn_2', status: 'completed', billedAt: `${new Date().getFullYear()}-10-01T00:00:00Z`, currency: 'EUR', totalAmount: 153 },
        { id: 'txn_3', status: 'completed', billedAt: `${new Date().getFullYear()}-11-01T00:00:00Z`, currency: 'EUR', totalAmount: 153 },
      ],
    }).as('transactions');
    cy.contains('button', exact('Billing history')).click();
    cy.wait('@transactions');
    cy.contains('153').should('be.visible');
    cy.scrollTo('bottom', { duration: 0 });
    shot('subscription', 2, undefined, { scroll: false });

    // The locked organisation's project, made through the API; its Milestones page needs an add-on
    cy.loginAs(locked.email);
    tokenFor(locked.email)
      .then((token) =>
        cy.request({
          method: 'POST',
          url: `${API}/api/projects`,
          headers: { Authorization: `Bearer ${token}` },
          body: { name: 'Rewire the shop' },
        }),
      )
      .then(({ body }) => {
        cy.visit(`/projects/${body.friendlyId}/milestones`);
      });
    cy.contains('button', exact('Upgrade in org settings', 1)).should('be.visible');
    shot('subscription', 3);
  });

  it('stats-overview: stats with ranges, the members view and the overview', () => {
    visitAs(w.owner.email, `/projects/${w.projectId}/stats`);
    cy.get('[data-testid="stats-tile"]').should('have.length.greaterThan', 2);
    cy.get('[data-testid="stats-chart"]').should('be.visible');
    shot('stats-overview', 1, undefined, { settle: 2000 });

    clickButton('By member');
    cy.get('[data-testid="stats-members-table"]').should('be.visible');
    shot('stats-overview', 2, undefined, { settle: 1500 });

    visitAs(w.owner.email, '/overview');
    cy.contains(PROJECT_NAME).should('be.visible');
    shot('stats-overview', 3);
  });

  it('api-keys: the keys list', () => {
    visitAs(w.owner.email, '/settings');
    cy.contains('Accounting export').scrollIntoView();
    cy.contains('Accounting export').should('be.visible');
    shot('api-keys', 1, undefined, { scroll: false });

    clickButton('+ New API Key');
    dialog().should('be.visible');
    shot('api-keys', 2, undefined, { scroll: false });
  });

  it('faq: the "too many requests" toast', () => {
    cy.intercept('POST', '**/api/projects', {
      statusCode: 429,
      headers: { 'Retry-After': '7' },
      body: {
        error: 'Too Many Requests',
        message: 'Rate limit exceeded. Try again in 7 second(s).',
        timestamp: new Date().toISOString(),
      },
    }).as('createProject');
    visitAs(w.owner.email, '/projects');
    clickButton('+ New Project');
    dialog().find('input').first().type('Garage conversion');
    dialog().contains('button', exact('Create project')).click();
    cy.wait('@createProject');
    cy.get('[data-sonner-toast]').should('be.visible');
    shot('faq', 1);
  });
});
