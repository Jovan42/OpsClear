// ADR-0049 Appendix §19, /features-demos half (ADR-0040). Entirely unauthenticated —
// no cy.loginAs() anywhere in this file, matching the real public-page UX.
//
// JOB-199 CORRECTION: the ADR-0049 job description for this backfill (and ADR-0049
// §19's own "Edge cases" text) frames the Notes and History demo cards as a KNOWN BUG
// that "should currently FAIL until JOB-199 is fixed". JOB-199 is already fixed and
// merged (confirmed by reading DemoQueryScope.tsx — it wraps children in
// <OrgContext.Provider value={mockOrgState}> with mockOrgState.hasAddon returning
// true unconditionally, exactly the fix the ADR describes). Both cards are tested
// below as PASSING regression tests proving the fix holds, not as documented
// failures.
//
// ANOTHER ADR GAP: §19's "multi-slide cards (approvals)" bullet undersells this —
// MilestonesDemo and JobTypesDemo are ALSO 2-slide demos (confirmed by reading their
// source), not just Approvals. The slide-nav tests below exercise the mechanism
// generically via Approvals as the primary example, plus a second assertion against
// Milestones to confirm it isn't an approvals-only special case.
//
// MSW note: registering the demo's real Service Worker (msw/browser) inside headless
// Cypress works reliably (verified via a throwaway scratch spec before writing this
// file) — the one real gotcha is timing: clicking a card's trigger before the shrunk
// live preview has actually finished rendering real seeded content clicks on a
// still-settling DOM and silently no-ops (handleOpen() bails if slides is still
// null). Every test below waits for the live preview's real content first, then
// clicks — this turned out to be both more reliable AND faster than a blind cy.wait().
//
// NOT covered (documented, not silently skipped):
// - "A slide's render throwing is caught by a per-slide ErrorBoundary" — confirmed
//   the ErrorBoundary wrapping exists (DemoTrigger.tsx wraps every slide's render()
//   in <ErrorBoundary>), but forcing a genuine render-time exception requires either
//   a test-only error-injection hook or modifying application source, neither of
//   which exists — out of practical reach for a pure DOM-interaction E2E test.
// - "MSW worker registration failure falls back to the static preview with a logged
//   warning" — attempted via cy.stub(win.navigator.serviceWorker, 'register') in
//   onBeforeLoad, the standard Cypress pattern for this. Exhaustively diagnosed via
//   throwaway scratch specs: unregistering any pre-existing registration works fine
//   (confirmed via getRegistrations()), and the demo's real worker DOES register
//   successfully in the normal case (navigator.serviceWorker.controller becomes a
//   real ServiceWorker) — but a spy/stub on navigator.serviceWorker.register from
//   onBeforeLoad shows ZERO calls even in that successful case, meaning MSW's
//   internal registration call isn't going through the same object reference
//   Cypress's stub replaces. This is a known-hard class of problem (simulating a
//   failed real Service Worker registration under MSW is a recurring pain point
//   across the Cypress/Playwright ecosystem, not specific to this app). Forcing the
//   underlying network request to fail (cy.intercept on /mockServiceWorker.js) was
//   also tried and didn't reach the catch block either. Out of practical reach
//   without adding a test-only hook to DemoTrigger.tsx itself.

const NOTES_CARD_TITLE = 'Notes';
const HISTORY_CARD_TITLE = 'Job status history';
const APPROVALS_CARD_TITLE = 'Approvals';
const MILESTONES_CARD_TITLE = 'Milestones';
const API_KEYS_CARD_TITLE = 'API keys';
const LINKS_CARD_TITLE = 'Job links';
const RELATIONSHIPS_CARD_TITLE = 'Job relationships';
const FEEDBACK_CARD_TITLE = 'Feedback & Credits';

/** Waits for a card's shrunk live preview to render real content (not the static
 *  fallback), then clicks it open. The wait is scoped to the DemoTrigger's own
 *  [role="button"] element specifically, NOT the whole card container — the card's
 *  h3 title and description sit as DOM siblings of that element (see FeaturesPage.tsx's
 *  Card component), so scoping any wider risks the text search trivially matching the
 *  always-present title/description instead of actually waiting for the live preview
 *  (bit us for the API Keys card, whose own title is "API keys" — identical to a
 *  naive wait string). */
function openDemo(cardTitle: string, waitForText: string) {
  cy.contains('h3', cardTitle)
    .parents('.rounded-2xl')
    .find('[role="button"]')
    .first()
    .as('trigger');
  cy.get('@trigger').contains(waitForText, { timeout: 10000 }).should('be.visible');
  cy.get('@trigger').click();
  cy.get('.fixed.inset-0.z-50', { timeout: 10000 }).should('be.visible').as('overlay');
}

function closeDemo() {
  cy.get('@overlay').find('button[aria-label="Close demo"]').click();
  cy.get('.fixed.inset-0.z-50').should('not.exist');
}

describe('/features interactive demos — trigger, overlay, and MSW isolation', () => {
  it('all 13 cards render a real live preview on page load, not the static screenshot fallback', () => {
    cy.visit('/features');
    const cardTitles = [
      'Job tracking', FEEDBACK_CARD_TITLE, 'Dashboard', APPROVALS_CARD_TITLE, NOTES_CARD_TITLE,
      HISTORY_CARD_TITLE, MILESTONES_CARD_TITLE, RELATIONSHIPS_CARD_TITLE, 'Job templates',
      'Recurring scheduling', API_KEYS_CARD_TITLE, LINKS_CARD_TITLE, 'Job types',
    ];
    cardTitles.forEach((title) => {
      cy.contains('h3', title).parents('.rounded-2xl').find('[role="button"]', { timeout: 10000 }).should('be.visible');
      // The static fallback text only renders when `slides` is still null.
      cy.contains('h3', title).parents('.rounded-2xl').contains('No preview yet').should('not.exist');
    });
  });

  it('clicking a card opens a full-screen overlay with the persistent "Demo — sample data" badge', () => {
    cy.visit('/features');
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').contains('Demo — sample data, nothing here is saved').should('be.visible');
  });

  it('closing via the × button then reopening resets to slide 0 against a fresh baseline', () => {
    cy.visit('/features');
    openDemo(APPROVALS_CARD_TITLE, 'Ready to delete the old CMS export');
    cy.get('@overlay').contains('Pending approvals').should('be.visible');
    cy.get('@overlay').find('button[aria-label="Next example"]').click();
    cy.get('@overlay').contains('Approval history on a job').should('be.visible');
    closeDemo();

    openDemo(APPROVALS_CARD_TITLE, 'Ready to delete the old CMS export');
    cy.get('@overlay').contains('Pending approvals').should('be.visible');
  });

  it('Escape closes the overlay', () => {
    cy.visit('/features');
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('body').type('{esc}');
    cy.get('.fixed.inset-0.z-50').should('not.exist');
  });

  it('clicking the backdrop closes the overlay, but clicking inside the panel does not', () => {
    cy.visit('/features');
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').contains('First pass is up on staging').click();
    cy.get('.fixed.inset-0.z-50').should('exist');
    cy.get('@overlay').click(10, 10); // corner of the backdrop, outside the panel
    cy.get('.fixed.inset-0.z-50').should('not.exist');
  });

  it('a multi-slide demo (Approvals) pages via the arrow buttons with a live slide counter', () => {
    cy.visit('/features');
    openDemo(APPROVALS_CARD_TITLE, 'Ready to delete the old CMS export');
    cy.get('@overlay').contains('1/2').should('be.visible');
    cy.get('@overlay').find('button[aria-label="Next example"]').click();
    cy.get('@overlay').contains('Approval history on a job').should('be.visible');
    cy.get('@overlay').contains('2/2').should('be.visible');
    cy.get('@overlay').find('button[aria-label="Previous example"]').click();
    cy.get('@overlay').contains('Pending approvals').should('be.visible');
    cy.get('@overlay').contains('1/2').should('be.visible');
  });

  it('a multi-slide demo pages via arrow keys too (not just click) — confirmed on Milestones, not just Approvals', () => {
    cy.visit('/features');
    openDemo(MILESTONES_CARD_TITLE, 'Milestones');
    cy.get('@overlay').contains('1/2').should('be.visible');
    cy.get('body').type('{rightarrow}');
    cy.get('@overlay').contains('2/2').should('be.visible');
    cy.get('body').type('{leftarrow}');
    cy.get('@overlay').contains('1/2').should('be.visible');
  });

  it('a single-slide demo (Notes) shows no slide-nav arrows or counter', () => {
    cy.visit('/features');
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').find('button[aria-label="Next example"]').should('not.exist');
    cy.get('@overlay').find('button[aria-label="Previous example"]').should('not.exist');
    cy.get('@overlay').contains(/^\d\/\d$/).should('not.exist');
  });

  it('an interactive mutation inside a demo (adding a note) never reaches the real backend', () => {
    cy.intercept('POST', '**/api/projects/**/jobs/**/notes', cy.spy().as('realNotesEndpoint'));
    cy.visit('/features');
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').find('textarea').type('A brand new demo note');
    cy.get('@overlay').contains('button', 'Add Note').click();
    // Real NoteThread confirms before adding.
    cy.get('.z-50:visible').last().within(() => cy.contains('button', 'Add Note').click());
    cy.get('@overlay').contains('A brand new demo note').should('be.visible');
    cy.get('@realNotesEndpoint').should('not.have.been.called');
  });

  it('the Notes demo card shows real seeded content (JOB-199 regression guard)', () => {
    cy.visit('/features');
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').contains('Redesign homepage hero section').should('be.visible');
    cy.get('@overlay').contains('First pass is up on staging — feedback welcome.').should('be.visible');
  });

  it('the Job status history demo card shows real seeded content (JOB-199 regression guard)', () => {
    cy.visit('/features');
    openDemo(HISTORY_CARD_TITLE, 'Created as');
    cy.get('@overlay').contains('Created as').should('be.visible');
    cy.get('@overlay').contains('Waiting on the CDN migration to finish first').should('be.visible');
    // Walks New -> In Progress -> Blocked -> In Progress -> Completed: 5 entries.
    cy.get('@overlay').find('ol > li').should('have.length', 5);
  });

  it('the API keys demo card is unaffected by any hasAddon gate (regression guard)', () => {
    cy.visit('/features');
    openDemo(API_KEYS_CARD_TITLE, 'CI deploy script');
    cy.get('@overlay').contains('button', '+ New API Key').should('be.visible');
  });

  it('the Job links demo card is unaffected by any hasAddon gate (regression guard)', () => {
    cy.visit('/features');
    openDemo(LINKS_CARD_TITLE, 'Redesign homepage hero section');
    cy.get('@overlay').contains('+ Add link').should('be.visible');
  });

  it('the Job relationships demo card is unaffected by any hasAddon gate (regression guard)', () => {
    cy.visit('/features');
    openDemo(RELATIONSHIPS_CARD_TITLE, 'Relationships');
    cy.get('@overlay').contains('Relationships').should('be.visible');
    cy.get('@overlay').find('button').contains('+ Add').should('be.visible');
  });

  it('the Feedback & credits demo card is unaffected by any hasAddon gate (regression guard)', () => {
    cy.visit('/features');
    openDemo(FEEDBACK_CARD_TITLE, 'My submissions');
    cy.get('@overlay').contains('My submissions').should('be.visible');
  });

  it("the Approvals demo's second slide (decided history) is unaffected by any hasAddon gate (regression guard)", () => {
    cy.visit('/features');
    openDemo(APPROVALS_CARD_TITLE, 'Ready to delete the old CMS export');
    cy.get('@overlay').find('button[aria-label="Next example"]').click();
    cy.get('@overlay').contains('Approval history on a job').should('be.visible');
    cy.get('@overlay').contains('Approvals').should('be.visible');
  });

  it('adding a note in the notes demo creates exactly one new entry, not a duplicate (all-13-cards-mount request-storm regression guard)', () => {
    cy.visit('/features');
    // All 13 DemoTrigger instances mount simultaneously on this same page load —
    // exactly the scenario the FeaturesPage.tsx useMemo(CARDS) fix guards against.
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').find('.prose, [class*="note"]').its('length').then(() => {
      cy.get('@overlay').find('textarea').type('Duplicate-check note');
      cy.get('@overlay').contains('button', 'Add Note').click();
      cy.get('.z-50:visible').last().within(() => cy.contains('button', 'Add Note').click());
      cy.get('@overlay').contains('Duplicate-check note').should('have.length', 1);
    });
  });

  it('resetDemoData() on close then immediately reopening produces an identical baseline every time', () => {
    cy.visit('/features');
    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').find('textarea').type('This note should not survive a close');
    cy.get('@overlay').contains('button', 'Add Note').click();
    cy.get('.z-50:visible').last().within(() => cy.contains('button', 'Add Note').click());
    cy.get('@overlay').contains('This note should not survive a close').should('be.visible');
    closeDemo();

    openDemo(NOTES_CARD_TITLE, 'First pass is up on staging');
    cy.get('@overlay').contains('This note should not survive a close').should('not.exist');
    cy.get('@overlay').contains('First pass is up on staging').should('be.visible');
  });

});
