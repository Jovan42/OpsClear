// ADR-0049 Appendix §21, dark mode half (ADR-0019). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// ADR-0019's documented FOIT (flash of incorrect theme) on cold load is an accepted
// gap, not a regression to chase — these tests assert the post-hydration steady
// state only, never a zero-flash guarantee.
//
// `cy.window()` here always refers to the *app's* window (the AUT iframe), matching
// how `system` theme tests below stub `window.matchMedia` in that same window rather
// than the Cypress runner's own — a mismatch there would silently no-op.
//
// AppLayout also renders sonner's <Toaster theme="system">, which independently
// calls window.matchMedia('(prefers-color-scheme: dark)') for its own toast
// styling — unrelated to useTheme's hook, but the SAME global function once stubbed.
// The "reacts live" test below collects every registered handler into an array and
// fires all of them (matching what a real OS-level change event would do — every
// page listener, not just this app's) rather than assuming there's exactly one.
//
// NOT covered (documented, not silently skipped): "no leaked listener across
// repeated toggling". Counting raw addEventListener/removeEventListener calls on a
// stubbed matchMedia isn't a reliable signal — it can't distinguish useTheme's own
// calls from Toaster's identical, unrelated ones (confirmed empirically: the naive
// count came back as 7, not the expected 2). And even a genuine leak wouldn't be
// observable through DOM assertions either way — N accumulated identical listeners
// all calling the same idempotent classList.add('dark')/remove('dark') produce
// exactly the same visible result as one. Proving "no leak" would need low-level
// introspection of the MediaQueryList's internal listener list, which isn't exposed
// to page JS at all (Chrome DevTools' getEventListeners() is a devtools-only API).
// Out of practical E2E reach; useTheme.ts's own cleanup return is what actually
// guards against this, verified by source read, not by a black-box test here.

import { uniqueEmail, uniqueSlug, createOrgWithSubscription, createProjectAs } from '../../support/orgApi';

describe('Dark Mode', () => {
  it('light/dark/system themes apply (or remove) the dark class on <html> correctly, and the choice persists across a reload', () => {
    const email = uniqueEmail('theme-basic');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Falcon Corp', uniqueSlug());
    createProjectAs(email, 'Falcon Theme Project').then(() => {
      cy.loginAs(email);
      cy.visit('/settings');

      cy.contains('button', 'Dark').click();
      cy.get('html').should('have.class', 'dark');
      cy.reload();
      cy.get('html').should('have.class', 'dark');

      cy.contains('button', 'Light').click();
      cy.get('html').should('not.have.class', 'dark');
      cy.reload();
      cy.get('html').should('not.have.class', 'dark');
    });

    cy.deleteKeycloakUser(email);
  });

  it('the "system" theme follows the OS color-scheme preference and reacts live to a change, with no reload', () => {
    const email = uniqueEmail('theme-system');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Nimbus Corp', uniqueSlug());
    createProjectAs(email, 'Nimbus Theme Project').then(() => {
      cy.loginAs(email);
      cy.visit('/settings');

      const changeHandlers: Array<(e: { matches: boolean }) => void> = [];
      cy.window().then((win) => {
        cy.stub(win, 'matchMedia').callsFake((query: string) => ({
          matches: false,
          media: query,
          addEventListener: (_: string, handler: (e: { matches: boolean }) => void) => { changeHandlers.push(handler); },
          removeEventListener: () => {},
          addListener: () => {},
          removeListener: () => {},
          dispatchEvent: () => false,
        }));
      });

      cy.contains('button', 'System').click();
      cy.get('html').should('not.have.class', 'dark');

      // Simulate the OS flipping to dark mode — no reload, no re-click, every
      // registered listener (this app's own, plus Toaster's unrelated one) fires,
      // same as a real prefers-color-scheme change event would.
      cy.then(() => changeHandlers.forEach((h) => h({ matches: true })));
      cy.get('html').should('have.class', 'dark');

      cy.then(() => changeHandlers.forEach((h) => h({ matches: false })));
      cy.get('html').should('not.have.class', 'dark');
    });

    cy.deleteKeycloakUser(email);
  });
});
