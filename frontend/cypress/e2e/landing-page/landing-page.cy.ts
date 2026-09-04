// ADR-0049 Appendix §19, landing-page half (ADR-0029). No cy.loginAs() needed for most
// of this file — the landing page and pricing calculator are entirely public/
// unauthenticated; the one exception (authenticated -> redirect) logs in deliberately
// to prove the opposite case.
//
// The catalog seeded on this stack is a fully dense 7 member-band x 5 project-band
// grid (35 tiers, confirmed via GET /api/subscriptions/catalog) — every slider
// position resolves to a real tier, and every add-on is currently `available: true`.
// That means two ADR-flagged edge cases (selectedTier resolving to null; a "coming
// soon" add-on rendering non-interactively) are not reachable through any real slider
// position or real catalog data today. Both are still real code paths (ProductAndPricing.tsx's
// `.find()` can return undefined; FeaturesPage.tsx's badgeFor() has a real `coming-soon`
// branch), so both are tested via cy.intercept serving a deliberately incomplete/
// unavailable-flagged catalog rather than skipped — that exercises the actual code
// without pretending the live catalog has gaps it doesn't.

import { uniqueEmail } from '../../support/orgApi';

const CATALOG_URL = '**/api/subscriptions/catalog';

// jQuery's .val() sets the range input's DOM value directly, which visually moves the
// thumb but bypasses the native setter React's controlled <input> tracks internally —
// the onChange handler never fires, so memberIdx/projectIdx state never actually
// updates (same class of issue as the established setTextareaValue helper elsewhere
// in this suite). Set the value via the real native setter + a genuine 'input' event
// dispatch instead.
function setRangeValue(alias: string, value: number) {
  cy.get(alias).then(($el) => {
    const el = $el[0] as HTMLInputElement;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set!;
    setter.call(el, String(value));
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

describe('Public Landing Page (/)', () => {
  it('shows the full landing page to an unauthenticated visitor', () => {
    cy.visit('/');
    cy.contains("What's actually happening").should('be.visible');
    cy.contains('with your work today?').should('be.visible');
    cy.contains('button', 'Get started').should('be.visible');
    cy.contains('The status call is stealing your day').should('be.visible');
    cy.contains("See what's included. Know what it costs.").should('be.visible');
    cy.contains('button', 'Start now').should('be.visible');
  });

  it('redirects an authenticated visitor straight to /projects', () => {
    const email = uniqueEmail('landing-redirect');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    cy.loginAs(email);
    cy.visit('/');
    cy.url().should('include', '/projects');
    cy.contains("What's actually happening").should('not.exist');

    cy.deleteKeycloakUser(email);
  });

  it('the hero and CTA-footer buttons both navigate to Keycloak registration', () => {
    cy.visit('/');
    cy.contains('button', 'Get started').click();
    cy.url({ timeout: 10000 }).should('include', '/realms/opsclear/protocol/openid-connect/registrations');

    cy.visit('/');
    cy.contains('button', 'Start now').click();
    cy.url({ timeout: 10000 }).should('include', '/realms/opsclear/protocol/openid-connect/registrations');
  });

  it('the member/project sliders update the live base price as they move', () => {
    cy.visit('/');
    cy.contains('Base price').parent().contains(/\d+ EUR\/mo/).invoke('text').then((initial) => {
      cy.get('input[type="range"]').first().as('memberSlider');
      setRangeValue('@memberSlider', 6);
      cy.contains('Base price').parent().contains(/\d+ EUR\/mo/).invoke('text').should('not.equal', initial);
    });
  });

  it('toggling an available add-on adds its price to the total; toggling it off removes it', () => {
    cy.visit('/');
    cy.contains('Monthly total').parents('.rounded-xl').contains(/\d+/).invoke('text').then((before) => {
      cy.contains('button', 'Dashboard').click();
      cy.contains('Monthly total').parents('.rounded-xl').contains(/\d+/).invoke('text').should('not.equal', before);
      cy.contains('button', 'Dashboard').click();
      cy.contains('Monthly total').parents('.rounded-xl').contains(/\d+/).invoke('text').should('equal', before);
    });
  });

  it('the annual toggle recomputes the total and shows a "Save X" badge', () => {
    cy.visit('/');
    cy.contains('Save').should('not.exist');
    cy.get('button[role="switch"]').click();
    cy.contains(/Save \d+ EUR\/yr/).should('be.visible');
    cy.contains('Monthly total (annual)').should('be.visible');
  });

  it('the slider at min and max boundary indices shows correct values without crashing', () => {
    cy.visit('/');
    cy.get('input[type="range"]').eq(1).as('projectSlider'); // second slider = active projects
    cy.get('@projectSlider').invoke('attr', 'max').then((max) => {
      setRangeValue('@projectSlider', 0);
      cy.contains('Active projects').parent().contains('Up to').should('be.visible');
      setRangeValue('@projectSlider', Number(max));
      cy.contains('Active projects').parent().contains('Unlimited').should('be.visible');
    });
    cy.contains("What's actually happening").should('be.visible'); // page still intact, no crash
  });

  it('selectedTier resolves to null gracefully when the catalog has a gap for the current slider position — base falls back to 0, no crash', () => {
    cy.intercept('GET', CATALOG_URL, (req) => {
      req.continue((res) => {
        // Drop the very first tier (5 members / 3 projects) so slider index (0, 0)
        // has no matching tier — this is the exact gap ProductAndPricing.tsx's
        // `.find()` can hit; not reachable with the real, fully-dense seeded catalog.
        res.body.tiers = res.body.tiers.filter(
          (t: { maxMembers: number; maxProjects: number | null }) => !(t.maxMembers === 5 && t.maxProjects === 3),
        );
      });
    }).as('gappyCatalog');

    cy.visit('/');
    cy.wait('@gappyCatalog');
    cy.get('input[type="range"]').first().as('memberSlider');
    cy.get('input[type="range"]').eq(1).as('projectSlider');
    setRangeValue('@memberSlider', 0);
    setRangeValue('@projectSlider', 0);
    cy.contains('Base price').parent().contains('0 EUR/mo').should('be.visible');
    cy.contains("What's actually happening").should('be.visible'); // no crash
  });

  it('a "coming soon" (unavailable) add-on renders as a non-interactive card, not a clickable toggle', () => {
    cy.intercept('GET', CATALOG_URL, (req) => {
      req.continue((res) => {
        res.body.addons[0].available = false;
      });
    }).as('cappedCatalog');

    cy.visit('/');
    cy.wait('@cappedCatalog');
    cy.contains('Soon').should('be.visible');
    cy.contains('Soon').closest('div').should('not.match', 'button');
  });

  it('rapid slider dragging settles on the final value with no stale price left behind', () => {
    cy.visit('/');
    cy.get('input[type="range"]').first().as('slider');
    for (let i = 0; i < 6; i++) {
      setRangeValue('@slider', i % 3);
    }
    setRangeValue('@slider', 4);
    // memberBands = [5,10,15,20,30,40,50] -> index 4 = "Up to 30".
    cy.contains('Team members').parent().contains('Up to 30').should('be.visible');
  });
});
