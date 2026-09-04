// ADR-0049 Appendix §20 (Localization — i18n Smoke Coverage). Uses cy.loginAs() per
// docs/dev/process/E2E.md for the authenticated-app tests; the public-page tests are
// deliberately unauthenticated, matching the real unauthenticated UX (PublicNav's
// locale toggle only renders for unauthenticated visitors — logged-in users switch
// locale from Settings instead).
//
// ADR CORRECTION #1: both the ADR-0049 bullet and ADR-0037 itself describe "12
// namespace files" — there are actually 13 (confirmed via readdirSync in
// cypress.config.ts's readLocaleFiles task): approvalsDashboardSettingsLanding,
// common, errors, feedback, jobsComponents, jobsPages, jobTypes,
// milestonesTemplatesSchedules, org, projects, shared1, shared2, superAdmin.
// superAdmin.json was likely added after the ADR text was written. The parity test
// below iterates the actual file list, not a hardcoded count, so it isn't affected by
// which number is "correct."
//
// ADR CORRECTION #2: the ADR-0049 bullet frames the hardcoded Intl.NumberFormat
// locale as "RSD currency formatting" — the pricing catalog's actual currency is EUR
// (confirmed via GET /api/subscriptions/catalog), not RSD. The underlying point still
// holds (the number-grouping locale is hardcoded to 'sr-RS' regardless of UI
// language, confirmed by reading all 5 call sites: SuperAdminPricingPage.tsx,
// SuperAdminFeedbackPage.tsx, BillingHistorySection.tsx, ProductAndPricing.tsx,
// SubscriptionSection.tsx), just not for the reason the ADR states.
//
// Two tests below rely on a small, Cypress-only test hook added to i18n/index.ts
// (`window.__i18nForE2E`, gated on `import.meta.env.DEV` — E2E always runs against
// the Vite dev server, never a production build) to reach the app's real, running
// i18next instance directly:
//   - the missing-key EN-fallback test needs to inject a resource key present in only
//     one locale, which isn't reproducible by deleting a real key without corrupting
//     the actual translation files
//   - the mid-form-fill test needs to trigger a locale change from a page (a modal)
//     that has no in-place locale switcher reachable without navigating away and
//     losing the form's ephemeral state, which would defeat the point of the test
//
// NOT covered as originally planned: a live behavioral test proving the pricing
// calculator's Intl.NumberFormat('sr-RS') grouping separator differs from what
// en-US formatting would produce. sr-RS and en-US format identically for any integer
// under 1000 (no thousands separator is rendered either way), and every real price in
// the catalog — even with every add-on selected and the highest member/project tier —
// stays well under 1000, so this specific formatting distinction has no reachable UI
// state to observe it in. The test below instead confirms the displayed digits don't
// change when the UI locale changes (the property that WOULD break if a future
// regression made this UI-locale-driven instead of hardcoded), backed by the source
// citation above for the deeper guarantee.

import { uniqueEmail, uniqueSlug, createOrgWithSubscription, createProjectAs } from '../../support/orgApi';

const NAMESPACES_EXPECTED_COUNT = 13;

describe('Localization (i18n) — Smoke Coverage', () => {
  it('every locale namespace file has matching keys between en and sr', () => {
    cy.task('readLocaleFiles').then((files) => {
      const filesTyped = files as Record<string, { en: string[]; sr: string[] }>;
      const fileNames = Object.keys(filesTyped);
      expect(fileNames, 'namespace file count').to.have.length(NAMESPACES_EXPECTED_COUNT);

      for (const file of fileNames) {
        const { en, sr } = filesTyped[file];
        const enSet = new Set(en);
        const srSet = new Set(sr);
        const missingFromSr = en.filter((k) => !srSet.has(k));
        const missingFromEn = sr.filter((k) => !enSet.has(k));
        expect(missingFromSr, `${file}: keys in en but missing from sr`).to.deep.equal([]);
        expect(missingFromEn, `${file}: keys in sr but missing from en`).to.deep.equal([]);
      }
    });
  });

  it('defaults to English with no stored preference, and the public-page toggle switches locale live with no reload', () => {
    cy.clearLocalStorage();
    cy.visit('/');
    cy.contains('button', 'Log in').should('be.visible');
    cy.contains('a', 'OpsClear').should('be.visible');

    cy.window().then((win) => {
      (win as unknown as { __reloadMarker: string }).__reloadMarker = 'still-here';
    });

    cy.contains('button', 'EN').click();
    cy.contains('button', 'SR').should('be.visible');
    cy.contains('button', 'Prijava').should('be.visible');
    cy.contains('button', 'Log in').should('not.exist');

    // No reload happened — a real navigation/reload would have wiped this marker.
    cy.window().then((win) => {
      expect((win as unknown as { __reloadMarker?: string }).__reloadMarker).to.equal('still-here');
    });
  });

  it('the locale selection persists across a reload', () => {
    cy.clearLocalStorage();
    cy.visit('/');
    cy.contains('button', 'EN').click();
    cy.contains('button', 'Prijava').should('be.visible');

    cy.reload();
    cy.contains('button', 'Prijava').should('be.visible');
    cy.contains('button', 'Log in').should('not.exist');
  });

  it('an API error toast translates the category label but keeps the detailed message in English, in both locales', () => {
    const email = uniqueEmail('locale-toast');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Locale Toast Corp', uniqueSlug()).then(() =>
      createProjectAs(email, 'Duplicate Name Corp').then(() => {
        // A prior test in this file may have left localStorage's locale preference
        // set to 'sr' — start each test that assumes an English starting UI from a
        // known-clean state rather than depending on execution order.
        cy.clearLocalStorage();
        cy.loginAs(email);
        cy.visit('/projects');

        // Same owner, same name again — 409 Conflict (NAME_ALREADY_EXISTS), English msg.
        cy.contains('button', '+ New Project').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('input[placeholder="e.g. Website Redesign"]').type('Duplicate Name Corp');
          cy.contains('button', 'Create project').click();
        });
        cy.get('[data-sonner-toast]').should('be.visible').and('contain.text', 'Conflict');
        cy.get('[data-sonner-toast]').should('contain.text', 'A project with this name already exists');

        cy.visit('/settings');
        cy.contains('button', 'Srpski').click();

        cy.visit('/projects');
        cy.contains('button', '+ Novi projekat').click();
        cy.get('.z-50:visible').within(() => {
          cy.get('input').first().type('Duplicate Name Corp');
          cy.contains('button', /Kreiraj|Create/).click();
        });
        cy.get('[data-sonner-toast]').should('be.visible').and('contain.text', 'Konflikt');
        // Detailed message stays English even in the sr locale (ADR-0037's deliberate cut).
        cy.get('[data-sonner-toast]').should('contain.text', 'A project with this name already exists');
      }),
    );

    cy.deleteKeycloakUser(email);
  });

  it('a key present in only one locale falls back to the English text, not a raw key string', () => {
    const email = uniqueEmail('locale-fallback');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Locale Fallback Corp', uniqueSlug()).then(() => {
      cy.loginAs(email);
      cy.visit('/settings');

      cy.window().then((win) => {
        const w = win as unknown as {
          __i18nForE2E: {
            addResourceBundle: (...args: unknown[]) => void;
            changeLanguage: (l: string) => Promise<unknown>;
            t: (k: string) => string;
          };
        };
        w.__i18nForE2E.addResourceBundle('en', 'common', { e2eFallbackProbe: 'English-only probe text' }, true, true);
        return w.__i18nForE2E.changeLanguage('sr').then(() => {
          const rendered = w.__i18nForE2E.t('common:e2eFallbackProbe');
          expect(rendered).to.equal('English-only probe text');
          expect(rendered).to.not.equal('common:e2eFallbackProbe');
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('switching locale mid-form-fill re-renders labels but loses no already-typed data', () => {
    const email = uniqueEmail('locale-midform');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Locale Midform Corp', uniqueSlug()).then(() => {
      cy.clearLocalStorage();
      cy.loginAs(email);
      cy.visit('/projects');
      cy.contains('button', '+ New Project').click();
      cy.get('.z-50:visible').within(() => {
        cy.get('input[placeholder="e.g. Website Redesign"]').type('Locale Mid Fill Project');
      });
      cy.contains('label', 'Name').should('be.visible');

      cy.window().then((win) => {
        const w = win as unknown as { __i18nForE2E: { changeLanguage: (l: string) => Promise<unknown> } };
        return w.__i18nForE2E.changeLanguage('sr');
      });

      // The placeholder text itself is translated too, so it can no longer be used
      // to find this field after the locale switch — select by position instead.
      cy.get('.z-50:visible').within(() => {
        cy.get('input').first().should('have.value', 'Locale Mid Fill Project');
        cy.contains('label', 'Naziv').should('be.visible');
      });

      cy.window().then((win) => {
        const w = win as unknown as { __i18nForE2E: { changeLanguage: (l: string) => Promise<unknown> } };
        return w.__i18nForE2E.changeLanguage('en');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('the pricing calculator\'s displayed price stays digit-identical across UI locales (Intl.NumberFormat is hardcoded, not UI-language-driven)', () => {
    cy.clearLocalStorage();
    cy.visit('/');
    cy.contains("See what's included").should('be.visible');
    cy.get('body').then(($body) => {
      const match = $body.text().match(/(\d[\d.,]*)\s*EUR\/mo/);
      expect(match, 'monthly total, e.g. "24 EUR/mo"').to.not.equal(null);
      const totalEn = match![1];

      cy.contains('button', 'EN').click();
      cy.contains('Vidite šta je uključeno').should('be.visible');
      cy.get('body').then(($bodySr) => {
        const matchSr = $bodySr.text().match(/(\d[\d.,]*)\s*EUR\/mes/);
        expect(matchSr, 'monthly total in sr, e.g. "24 EUR/mes"').to.not.equal(null);
        expect(matchSr![1]).to.equal(totalEn);
      });
    });
  });

  it('switching to Serbian applies no RTL styling — html has no dir="rtl" and body computes direction:ltr (ADR-0037: RTL out of scope)', () => {
    cy.clearLocalStorage();
    cy.visit('/');
    cy.contains('button', 'EN').click();
    cy.contains('button', 'Prijava').should('be.visible');
    cy.get('html').invoke('attr', 'dir').should('not.equal', 'rtl');
    cy.get('body').should(($body) => {
      expect($body.css('direction')).to.equal('ltr');
    });
  });
});
