// ADR-0062 (JOB-356): the public user guide at /guide. Logged out for most of this file
// (the guide needs no login); one test logs in to prove the app header replaces the public
// nav. Only the index page has content so far, so the other slugs show "Coming soon".

import { uniqueEmail } from '../../support/orgApi';

describe('User guide', () => {
  beforeEach(() => {
    // Wide enough for all three columns (sidebar from md, "On this page" from lg).
    cy.viewport(1280, 800);
  });

  it('opens logged out with the public nav, the sidebar and the index page', { tags: '@smoke' }, () => {
    cy.visit('/guide');
    cy.contains('nav a', 'Guide').should('be.visible');
    cy.contains('button', 'Log in').should('be.visible');
    cy.contains('h1', 'Getting started with OpsClear').should('be.visible');
    cy.title().should('eq', 'Getting started with OpsClear · Guide · OpsClear');

    cy.get('[data-testid=guide-sidebar]').within(() => {
      cy.contains('Getting started').should('be.visible');
      cy.contains('Daily work').should('be.visible');
      cy.contains('Planning').should('be.visible');
      cy.contains('Admin & settings').should('be.visible');
      cy.contains('Account & more').should('be.visible');
      cy.get('a[aria-current=page]').should('have.text', 'Getting started with OpsClear');
    });

    // Every guide component renders on the index page.
    cy.get('[data-testid=guide-article]').within(() => {
      cy.get('[data-testid=guide-steps]').should('be.visible');
      cy.get('[data-testid=guide-callout-note]').should('be.visible');
      cy.get('[data-testid=guide-callout-tip]').should('be.visible');
      cy.get('[data-testid=guide-callout-warning]').should('be.visible');
      cy.get('[data-testid=guide-screenshot] img').should('have.attr', 'alt').and('not.be.empty');
      cy.get('[data-testid=guide-role-badge]').should('contain', 'Owner').and('contain', 'Admin');
      cy.get('[data-testid=guide-feature-link]').should('have.attr', 'href', '/features#job-tracking');
    });
  });

  it('opens logged in with the app header, from the user menu', () => {
    const email = uniqueEmail('guide-logged-in');
    cy.createKeycloakUser(email, 'E2E', 'Reader');
    cy.loginAs(email);
    cy.visit('/guide');
    cy.contains('h1', 'Getting started with OpsClear').should('be.visible');
    cy.get('button[aria-label="User menu"]').should('be.visible');
    cy.contains('button', 'Log in').should('not.exist');

    cy.get('button[aria-label="User menu"]').click();
    cy.contains('button', 'Help & guide').should('be.visible');

    cy.deleteKeycloakUser(email);
  });

  it('navigates from the sidebar, collapses a section and sends unknown pages to the index', () => {
    cy.visit('/guide');
    cy.get('[data-testid=guide-section-planning]').contains('a', 'Milestones').click();
    cy.location('pathname').should('eq', '/guide/milestones');
    cy.contains('h1', 'Milestones').should('be.visible');
    cy.get('[data-testid=guide-coming-soon]').should('contain', 'Coming soon');
    cy.title().should('eq', 'Milestones · Guide · OpsClear');
    cy.get('[data-testid=guide-section-planning] a[aria-current=page]').should('have.text', 'Milestones');

    cy.get('[data-testid=guide-section-planning]').contains('button', 'Planning').click();
    cy.get('[data-testid=guide-section-planning]').contains('a', 'Milestones').should('not.exist');

    cy.visit('/guide/no-such-page');
    cy.location('pathname').should('eq', '/guide');
    cy.contains('h1', 'Getting started with OpsClear').should('be.visible');
  });

  it('"On this page" lists the headings and jumps to one', () => {
    cy.visit('/guide');
    cy.get('[data-testid=guide-on-this-page]').within(() => {
      cy.contains('a', 'Your first steps').should('be.visible');
      cy.contains('a', 'How this guide is organised').click();
    });
    cy.location('hash').should('eq', '#how-this-guide-is-organised');
    cy.get('#how-this-guide-is-organised').should(($h) => {
      const top = $h[0].getBoundingClientRect().top;
      expect(top).to.be.within(-5, 200);
    });
  });

  it('search finds text on the index page and goes to its heading', () => {
    cy.visit('/guide/sign-up');
    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').focus();
    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').type('responsible person');
    cy.get('[data-testid=guide-search-results]').within(() => {
      cy.contains('Getting started with OpsClear').should('be.visible');
      cy.contains('# Your first steps').click();
    });
    cy.location('pathname').should('eq', '/guide');
    cy.location('hash').should('eq', '#your-first-steps');
    cy.contains('h2', 'Your first steps').should('be.visible');

    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').type('zzqqxx');
    cy.get('[data-testid=guide-search-results]').should('contain', 'No results');
  });

  it('previous and next follow the sidebar order', () => {
    cy.visit('/guide');
    cy.get('[data-testid=guide-prev]').should('not.exist');
    cy.get('[data-testid=guide-next]').should('contain', 'Sign up and verify your email').click();
    cy.location('pathname').should('eq', '/guide/sign-up');
    cy.get('[data-testid=guide-next]').should('contain', 'Create your organisation');
    cy.get('[data-testid=guide-prev]').should('contain', 'Getting started with OpsClear').click();
    cy.location('pathname').should('eq', '/guide');

    cy.visit('/guide/faq');
    cy.get('[data-testid=guide-next]').should('not.exist');
    cy.get('[data-testid=guide-prev]').should('contain', 'API keys');
  });

  it('the language switch shows the Serbian index', () => {
    cy.visit('/guide');
    cy.get('[data-testid=guide-lang-sr]').click();
    cy.contains('h1', 'Prvi koraci sa OpsClear-om').should('be.visible');
    cy.get('[data-testid=guide-sidebar]').should('contain', 'Svakodnevni rad');
    cy.get('[data-testid=guide-on-this-page]').should('contain', 'Na ovoj stranici');
    cy.get('[data-testid=guide-not-translated]').should('not.exist');

    cy.get('[data-testid=guide-lang-en]').click();
    cy.contains('h1', 'Getting started with OpsClear').should('be.visible');
  });
});
