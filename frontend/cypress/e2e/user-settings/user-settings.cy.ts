// ADR-0049 Appendix §4 (User Settings & Preferences). Uses cy.loginAs() per
// docs/dev/process/E2E.md.
//
// Not covered:
// - "Brief flash-of-incorrect-theme on cold load for explicit Light/Dark" — ADR-0049
//   names this a documented, accepted gap (ADR-0019); the theme test below already
//   only asserts the post-hydration steady state, never a zero-flash guarantee.
// - "Two tabs with different in-memory preference state don't live-sync without a
//   reload" — Cypress has no multi-tab support (same tooling limitation noted for
//   JOB-208's Auth Flows suite), and ADR-0049 itself frames this as expected
//   behavior to confirm rather than a bug to reproduce.

import { uniqueEmail, uniqueSlug, createOrgWithSubscription, createProjectAs } from '../../support/orgApi';

const STORAGE_KEY = 'opsclear:preferences';

describe('User Settings & Preferences', () => {
  it('/settings is reachable via the user menu', () => {
    const email = uniqueEmail('reach');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Reach Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/projects');
    cy.get('[aria-haspopup="true"]').click();
    cy.contains('Account settings').click();
    cy.url().should('include', '/settings');
    cy.contains('Theme').should('be.visible');

    cy.deleteKeycloakUser(email);
  });

  it('theme toggle (Light/Dark/System) persists to localStorage and survives a reload', () => {
    const email = uniqueEmail('theme');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Theme Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('button', 'Dark').click();
    cy.get('html').should('have.class', 'dark');
    cy.window().then((win) => {
      const stored = JSON.parse(win.localStorage.getItem(STORAGE_KEY) ?? '{}');
      expect(stored.theme).to.eq('dark');
    });

    cy.reload();
    cy.get('html').should('have.class', 'dark');
    cy.contains('button', 'Dark').should('have.class', 'bg-gray-900');

    cy.contains('button', 'Light').click();
    cy.get('html').should('not.have.class', 'dark');
    cy.window().then((win) => {
      const stored = JSON.parse(win.localStorage.getItem(STORAGE_KEY) ?? '{}');
      expect(stored.theme).to.eq('light');
    });

    cy.deleteKeycloakUser(email);
  });

  it('the System theme reacts live to OS prefers-color-scheme changes, no reload needed', () => {
    const email = uniqueEmail('system-theme');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'System Theme Corp', uniqueSlug());
    cy.loginAs(email);

    // Stubs window.matchMedia before the app boots, capturing every change listener
    // registered against it so the test can fire them manually — there's no real OS
    // to change the preference of in a headless browser. sonner's global Toaster
    // (JOB-118) *also* calls matchMedia('(prefers-color-scheme: dark)') for its own
    // theme detection, so this must support multiple concurrent listeners rather
    // than a single last-write-wins handler, or firing the event only reaches
    // whichever of the two happened to register last.
    cy.visit('/settings', {
      onBeforeLoad(win) {
        const changeHandlers: Array<(e: { matches: boolean }) => void> = [];
        const mql = {
          matches: false,
          media: '(prefers-color-scheme: dark)',
          addEventListener: (_event: string, cb: (e: { matches: boolean }) => void) => {
            changeHandlers.push(cb);
          },
          removeEventListener: () => {},
        };
        cy.stub(win, 'matchMedia').returns(mql);
        // @ts-expect-error -- test-only hook exposed on the window for the assertion below
        win.__fireSchemeChange = (matches: boolean) => changeHandlers.forEach((cb) => cb({ matches }));
      },
    });

    cy.contains('button', 'System').click();
    cy.get('html').should('not.have.class', 'dark');

    cy.window().then((win) => {
      // @ts-expect-error -- see the test-only hook set up in onBeforeLoad above
      win.__fireSchemeChange(true);
    });
    cy.get('html').should('have.class', 'dark');

    cy.window().then((win) => {
      // @ts-expect-error -- see the test-only hook set up in onBeforeLoad above
      win.__fireSchemeChange(false);
    });
    cy.get('html').should('not.have.class', 'dark');

    cy.deleteKeycloakUser(email);
  });

  // Representative "persists and is read across the app" case (ADR-0049 groups
  // job-list/dashboard/navigation preferences as one happy-path bullet, not a
  // separate exhaustive case per field) — the default-project-page preference is the
  // simplest to verify end-to-end since it needs no seeded job/milestone data, just a
  // project to redirect into.
  it('the default project page preference is read by ProjectRedirect when opening a project', () => {
    const email = uniqueEmail('read-elsewhere');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'ReadElsewhere Corp', uniqueSlug());
    createProjectAs(email, 'Redirect Target').then((friendlyId) => {
      cy.loginAs(email);
      cy.visit('/settings');
      cy.contains('Default project page').parent().parent().within(() => cy.contains('button', 'Jobs').click());

      cy.visit(`/projects/${friendlyId}`);
      cy.url().should('include', `/projects/${friendlyId}/jobs`);
    });

    cy.deleteKeycloakUser(email);
  });

  // ADR-0049: "Corrupted JSON in the preferences localStorage key falls back to
  // defaults via usePreferences' try/catch — seed garbage before load and assert the
  // fallback."
  it('corrupted JSON in the preferences key falls back to defaults instead of crashing', () => {
    const email = uniqueEmail('corrupt');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Corrupt Corp', uniqueSlug());
    cy.loginAs(email);

    cy.visit('/settings', {
      onBeforeLoad(win) {
        win.localStorage.setItem(STORAGE_KEY, '{not valid json');
      },
    });

    // Falls back to defaults (theme: 'system') rather than crashing or hanging.
    cy.contains('Theme').should('be.visible');
    cy.get('html').should('not.have.class', 'dark'); // system default in a light-preferring headless browser
    cy.contains('button', 'System').should('have.class', 'bg-gray-900');

    cy.deleteKeycloakUser(email);
  });

  // ADR-0049 edge case: "Clearing storage resets to defaults (expected)."
  it('clearing storage and reloading resets every preference to its default', () => {
    const email = uniqueEmail('clear');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Clear Corp', uniqueSlug());
    cy.loginAs(email);
    cy.visit('/settings');

    cy.contains('button', 'Dark').click();
    cy.get('html').should('have.class', 'dark');

    cy.window().then((win) => win.localStorage.removeItem(STORAGE_KEY));
    cy.reload();

    cy.get('html').should('not.have.class', 'dark'); // theme default is 'system'
    cy.contains('button', 'System').should('have.class', 'bg-gray-900');
    cy.window().then((win) => {
      expect(win.localStorage.getItem(STORAGE_KEY)).to.equal(null);
    });

    cy.deleteKeycloakUser(email);
  });
});
