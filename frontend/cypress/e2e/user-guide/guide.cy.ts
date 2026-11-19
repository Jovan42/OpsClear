// ADR-0062 (JOB-356): the public user guide at /guide. Logged out for most of this file
// (the guide needs no login); one test logs in to prove the app header replaces the public
// nav. Every page in the guide is written; src/guide/content.test.ts checks that none falls back
// to the "Coming soon" placeholder.

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

    // The sidebar scrolls on its own (sticky, viewport-high), so the lower sections are
    // below its fold at 800px: scroll each heading into view before checking it.
    const sections: Array<[string, string]> = [
      ['getting-started', 'Getting started'],
      ['daily-work', 'Daily work'],
      ['planning', 'Planning'],
      ['admin-settings', 'Admin & settings'],
      ['account-more', 'Account & more'],
    ];
    for (const [id, label] of sections) {
      cy.get(`[data-testid=guide-section-${id}] button[aria-expanded]`).scrollIntoView();
      cy.get(`[data-testid=guide-section-${id}] button[aria-expanded]`).should('be.visible').and('have.text', label);
    }
    cy.get('[data-testid=guide-sidebar] a[aria-current=page]').should('have.text', 'Getting started with OpsClear');

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
    cy.get('[data-testid=guide-coming-soon]').should('not.exist');
    cy.contains('h2', 'Create a milestone').should('be.visible');
    cy.title().should('eq', 'Milestones · Guide · OpsClear');
    cy.get('[data-testid=guide-section-planning] a[aria-current=page]').should('have.text', 'Milestones');

    cy.get('[data-testid=guide-section-planning]').contains('button', 'Planning').click();
    cy.get('[data-testid=guide-section-planning]').contains('a', 'Milestones').should('not.exist');

    cy.visit('/guide/no-such-page');
    cy.location('pathname').should('eq', '/guide');
    cy.contains('h1', 'Getting started with OpsClear').should('be.visible');
  });

  it('"On this page" jumps to a heading and highlights it, including the last one', () => {
    cy.visit('/guide');

    // A heading with enough page below it scrolls to the top.
    cy.get('[data-testid=guide-on-this-page]').contains('a', 'Your first steps').click();
    cy.location('hash').should('eq', '#your-first-steps');
    cy.get('#your-first-steps').should(($h) => {
      const top = $h[0].getBoundingClientRect().top;
      expect(top).to.be.within(-5, 200);
    });
    cy.get('[data-testid=guide-on-this-page] a[aria-current=location]').should('have.text', 'Your first steps');

    // The last heading can't reach the top: the page bottoms out first. It's in view, the page
    // is at the bottom, and it's the highlighted one, not the heading before it.
    cy.get('[data-testid=guide-on-this-page]').contains('a', 'How this guide is organised').click();
    cy.location('hash').should('eq', '#how-this-guide-is-organised');
    cy.window().should((win) => {
      const root = win.document.documentElement;
      expect(win.innerHeight + win.scrollY, 'scrolled to the bottom').to.be.at.least(root.scrollHeight - 2);
      const top = win.document.getElementById('how-this-guide-is-organised')!.getBoundingClientRect().top;
      expect(top, 'heading in view').to.be.within(0, win.innerHeight);
    });
    cy.get('[data-testid=guide-on-this-page] a[aria-current=location]').should('have.text', 'How this guide is organised');
  });

  it('search shows an error when its index fails to load, and "Try again" reloads so it works', () => {
    // Fail the first load of the index page's plain text (the `?raw` import search reads)
    // with a 500 (a dropped connection gets retried by the browser on its own),
    // then let the retry through.
    cy.intercept({ method: 'GET', url: /\/src\/guide\/en\/00-index\.mdx\?.*raw/, times: 1 }, { statusCode: 500, body: 'unavailable' }).as('rawFail');
    cy.visit('/guide/sign-up');
    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').focus();
    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').type('responsible person');
    cy.wait('@rawFail');
    cy.get('[data-testid=guide-search-error]').should('contain', "Search couldn't load.");

    // "Try again" reloads the page (a failed chunk import stays failed until then); this time
    // the text loads and search works.
    cy.get('[data-testid=guide-search-error]').contains('button', 'Try again').click();
    cy.location('pathname').should('eq', '/guide/sign-up');
    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').should('have.value', '');
    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').focus();
    cy.get('[data-testid=guide-sidebar] [data-testid=guide-search]').type('responsible person');
    cy.get('[data-testid=guide-search-error]').should('not.exist');
    cy.get('[data-testid=guide-search-results]').should('contain', 'Getting started with OpsClear');
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
    cy.get('[data-testid=guide-prev]').should('contain', 'Feedback and credits').click();
    cy.location('pathname').should('eq', '/guide/feedback');
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
