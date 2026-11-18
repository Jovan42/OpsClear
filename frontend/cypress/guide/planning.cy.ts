// ADR-0062 (JOB-358): screenshots for the Planning guide pages (milestones,
// templates-and-types, recurring-jobs, job-relationships), in the language given by
// GUIDE_LANG. Not a test of the app: it seeds the sample workspace (support/world.ts) and
// captures what each page shows. Outside cypress/e2e/, so CI never runs it; run it with
// `npm run guide:screenshots` (see cypress/guide/README.md). Images are named `<slug>-<n>`.

import { prepareCapture, shot } from './support/capture';
import { exact, tr } from './support/i18n';
import { clickButton, dialog, visitAs } from './support/steps';
import { cleanUpWorld, emptyWorld, seedWorld } from './support/world';

const lang = Cypress.env('GUIDE_LANG') ?? 'en';
const w = emptyWorld();

describe(`Guide screenshots: Planning (${lang})`, { testIsolation: false }, () => {
  beforeEach(prepareCapture);
  before(() => seedWorld(w));
  after(() => cleanUpWorld(w));

  const projectPath = (page: string) => `/projects/${w.projectId}/${page}`;

  it('milestones: the list with progress, and the form', () => {
    visitAs(w.owner.email, projectPath('milestones'));
    cy.contains('Second fix and finish').should('be.visible');
    shot('milestones', 1);

    clickButton('+ New Milestone');
    dialog().should('be.visible');
    shot('milestones', 2);
  });

  it('templates-and-types: templates (project and organisation) and job types', () => {
    visitAs(w.owner.email, projectPath('templates'));
    cy.contains('Customer follow-up call').should('be.visible');
    shot('templates-and-types', 1);

    cy.contains('button', exact('Edit')).first().click();
    dialog().should('be.visible');
    shot('templates-and-types', 2);

    cy.visit(projectPath('types'));
    cy.contains('Gas').should('be.visible');
    shot('templates-and-types', 3);

    clickButton('+ New Type');
    dialog().should('be.visible');
    shot('templates-and-types', 4);
  });

  it('recurring-jobs: schedules, the form with its preview, and missed runs', () => {
    visitAs(w.owner.email, projectPath('schedules'));
    cy.contains('Monthly boiler service').should('be.visible');
    cy.contains(/missed run|propušten/i).should('be.visible');
    shot('recurring-jobs', 1);

    clickButton('+ New Schedule');
    // This form is not a role="dialog": find it by its heading and name field
    cy.contains(tr('New Schedule')).should('be.visible');
    cy.get(`input[placeholder="${tr('e.g. Weekly deploy checklist')}"]`).type('Weekly pressure check');
    shot('recurring-jobs', 2);
    cy.visit(projectPath('schedules'));
    cy.contains('Monthly boiler service').should('be.visible');
    cy.contains(/missed run|propušten/i).click();
    shot('recurring-jobs', 3);
  });

  it('job-relationships: the section and the add dialog', () => {
    visitAs(w.owner.email, `/projects/${w.projectId}/jobs/${w.jobs.sink}`);
    cy.contains(tr('Relationships')).should('be.visible');
    cy.contains('Run the new water pipes').should('be.visible');
    shot('job-relationships', 1);

    cy.contains('button', exact('+ Add')).first().click();
    dialog().should('be.visible');
    shot('job-relationships', 2);
  });
});
