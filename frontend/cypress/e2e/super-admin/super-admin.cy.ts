// ADR-0049 Appendix §25 (Super Admin — Pricing, Credit Grants, Customer Feedback).
//
// SCOPE NOTE (read before extending this file): the backend side of this section is
// already exhaustively covered — SuperAdminPricingIntegrationTest, FeedbackAndCredits
// IntegrationTest, CreditGrantPaddleSyncIntegrationTest (all integration-level) plus
// CreditServiceTest, SuperAdminPricingServiceTest, FeedbackServiceTest, and
// PaddleClientTest (unit-level) between them prove nearly every [b]/[c]-tagged bullet
// in the ADR: price validation (negative *and* missing, JOB-232 closed that second
// gap), the grant-amount floor, blank/overlong reason (JOB-232), cross-org submission
// rejection, already-reviewed 409s, the Paddle-discount folding/partial-redemption/
// carry-forward math, the 90-day discount expiry (locked in at PaddleClientTest's
// `createOneTimeDiscount` test), and the archive-not-delete price-sync behavior. None
// of that is re-proven here.
//
// This file is the [a]-tagged half: real browser interactions with the pricing
// console, the feedback/credit-grant admin UI, the member-facing feedback form, and
// the org-settings credit balance display — plus the [a/b] bullets that are more
// honestly proven end-to-end through the UI than via a second MockMvc call (grant
// origin — standalone vs. from a submission — showing up correctly in the ledger;
// the route-guard-via-403 "no hint the console exists" behavior; the client-side
// amount floor; the poll-driven balance; the no-notification-on-grant gap).
//
// PADDLE SANDBOX REACHABILITY: same constraint paddle-checkout.cy.ts documents at
// length — every server-side PaddleClient call is a real outbound HTTPS request in
// this environment and 500s without a real API key. The only credit-grant path this
// file drives is therefore the "org has no real, webhook-confirmed subscription yet"
// one (CreditService's own NO_PADDLE_SUBSCRIPTION skip branch) — a plain org with no
// subscription record at all satisfies that branch without any outbound Paddle call,
// so it's fully real and deterministic. The "real sync succeeds and a Discount gets
// attached" path is Paddle-reachability-gated the same way checkout is, and is
// already covered without a live sandbox by CreditGrantPaddleSyncIntegrationTest's
// mocked PaddleClient and CreditServiceTest at the unit level.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  userIdFor,
  createOrgAs,
  createOrgWithSubscription,
  addMember,
  makeSuperUser,
  getCatalogAs,
  API,
} from '../../support/orgApi';

describe('Super Admin Pricing — inline price editing', () => {
  it('commits an edited price on blur, reverts on Escape or non-numeric input, and fires no request when the value is unchanged', () => {
    const email = uniqueEmail('pricing-admin');
    cy.createKeycloakUser(email, 'E2E', 'SuperAdmin');
    makeSuperUser(email).then(() => {
      cy.loginAs(email);
      cy.visit('/admin/pricing');
      cy.contains('h1', 'Pricing Configuration').should('be.visible');

      getCatalogAs(email).then((catalog) => {
        const tier = catalog.tiers[0];

        cy.intercept('PUT', `${API}/api/super-admin/pricing/tiers/${tier.id}`).as('updateTier');

        // Same value committed — EditablePrice's own commit() requires parsed !==
        // value before calling onSave, so this must not reach the network at all.
        cy.get('table').first().find('tbody tr').first().find('td').eq(1).find('button').click();
        cy.get('input[type="number"]').clear();
        cy.get('input[type="number"]').type(String(tier.priceMonthly));
        cy.get('input[type="number"]').blur();
        cy.get('input[type="number"]').should('not.exist');
        cy.get('@updateTier.all').should('have.length', 0);

        // Non-numeric input — a real <input type="number"> rejects non-digit
        // keystrokes outright (confirmed empirically: typing letters never changes
        // its value), so the field is left holding the original value and commit()'s
        // own parsed !== value check is false regardless — no save attempted.
        cy.get('table').first().find('tbody tr').first().find('td').eq(1).find('button').click();
        cy.get('input[type="number"]').type('abc');
        cy.get('input[type="number"]').should('have.value', String(tier.priceMonthly));
        cy.get('input[type="number"]').blur();
        cy.get('input[type="number"]').should('not.exist');
        cy.get('@updateTier.all').should('have.length', 0);

        // Escape reverts the draft and exits edit mode without ever calling commit().
        cy.get('table').first().find('tbody tr').first().find('td').eq(1).find('button').click();
        cy.get('input[type="number"]').clear();
        cy.get('input[type="number"]').type(String(tier.priceMonthly + 5));
        cy.get('input[type="number"]').type('{esc}');
        cy.get('input[type="number"]').should('not.exist');
        cy.get('@updateTier.all').should('have.length', 0);

        // A real edit (Enter commits, same as blur) does dispatch the PUT with the
        // right payload — asserted on the intercepted REQUEST, not its response.
        // updateTierPrice has no NO_PADDLE_SUBSCRIPTION-style skip branch (unlike a
        // credit grant): every real price update unconditionally calls
        // PaddleSubscriptionService.syncTierPriceToPaddle, a genuine outbound Paddle
        // API call — same reachability constraint paddle-checkout.cy.ts documents at
        // length, so it 500s in this environment (confirmed empirically: Paddle's own
        // "authentication_malformed" error, no real API key configured locally). The
        // successful round trip is already covered without a live sandbox by
        // SuperAdminPricingIntegrationTest's own mocked-PaddleClient tests.
        const nextPrice = tier.priceMonthly + 1;
        cy.get('table').first().find('tbody tr').first().find('td').eq(1).find('button').click();
        cy.get('input[type="number"]').clear();
        cy.get('input[type="number"]').type(`${nextPrice}{enter}`);
        cy.wait('@updateTier').its('request.body').should('deep.equal', {
          priceMonthly: nextPrice,
          priceAnnual: tier.priceAnnual,
        });
        // commit() calls setEditing(false) unconditionally before the mutation even
        // resolves, so edit mode closes immediately regardless of the (Paddle-
        // dependent) response — not contingent on the request actually succeeding.
        cy.get('input[type="number"]').should('not.exist');
      });
    });

    cy.deleteKeycloakUser(email);
  });
});

describe('Super Admin console — route guard', () => {
  it('redirects a non-super_user to /projects with no error toast, for both console routes', () => {
    const email = uniqueEmail('not-super-admin');
    cy.createKeycloakUser(email, 'E2E', 'Regular');
    createOrgAs(email, 'Regular Org', uniqueSlug()).then(() => {
      cy.loginAs(email);

      cy.visit('/admin/pricing');
      cy.location('pathname').should('eq', '/projects');
      cy.get('[data-sonner-toast]').should('not.exist');

      cy.visit('/admin/feedback');
      cy.location('pathname').should('eq', '/projects');
      cy.get('[data-sonner-toast]').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });
});

describe('Feedback submission — type templates and status', () => {
  it('auto-populates a template per type, preserves a hand edit across a later type switch, and lists the new submission as Pending', () => {
    const email = uniqueEmail('feedback-member');
    cy.createKeycloakUser(email, 'E2E', 'Member');
    createOrgWithSubscription(email, 'Feedback Org', uniqueSlug()).then(() => {
      cy.loginAs(email);
      cy.visit('/feedback');

      cy.contains(/No submissions yet\.?/).should('be.visible');
      cy.contains('button', 'New submission').click();

      // Default type is OTHER (empty template) — switching to Bug on a still-empty
      // field auto-inserts its template.
      cy.contains('button', 'Bug').click();
      cy.get('textarea[placeholder="Markdown supported…"]')
        .should('contain.value', 'Steps to reproduce')
        .should('contain.value', 'Expected behavior');

      // Hand-edit the auto-inserted template.
      const userEdit = 'Steps to reproduce\nMY OWN NOTES HERE';
      cy.get('textarea[placeholder="Markdown supported…"]').clear();
      cy.get('textarea[placeholder="Markdown supported…"]').type(userEdit);

      // Switching type again must NOT clobber the hand edit, since the field no
      // longer equals the last-applied template.
      cy.contains('button', 'Feature').click();
      cy.get('textarea[placeholder="Markdown supported…"]').should('have.value', userEdit);
      cy.contains('button', 'Feature').should(($btn) => {
        expect($btn.attr('class')).to.match(/border-\[var\(--brand\)\]/);
      });

      cy.get('#feedback-title').type('My bug report');
      cy.contains('button', 'Submit feedback').click();

      cy.contains('button', 'My submissions').should(($btn) => {
        expect($btn.attr('class')).to.match(/bg-gray-900|dark:bg-white/);
      });
      cy.contains('p', 'My bug report').should('be.visible');
      cy.contains('Pending review').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('caps the title field at 255 characters client-side', () => {
    const email = uniqueEmail('feedback-title-cap');
    cy.createKeycloakUser(email, 'E2E', 'Member');
    createOrgWithSubscription(email, 'Feedback Org', uniqueSlug()).then(() => {
      cy.loginAs(email);
      cy.visit('/feedback');
      cy.contains('button', 'New submission').click();

      // .type() dispatches real keystrokes, so it goes through the browser's native
      // maxLength enforcement the same way an actual user typing/pasting would — a
      // programmatic .val() set bypasses that entirely and isn't a faithful repro.
      cy.get('#feedback-title').type('x'.repeat(260), { delay: 0 });
      cy.get('#feedback-title').invoke('val').then((val) => {
        expect((val as string).length).to.equal(255);
      });
    });

    cy.deleteKeycloakUser(email);
  });
});

describe('Super Admin — feedback review and credit grants', () => {
  it('lets a super admin decline a submission, grant credit against another (org with no real billing yet), and grant a standalone credit — with no notification toast on success', () => {
    const memberEmail = uniqueEmail('feedback-target-member');
    const superEmail = uniqueEmail('feedback-super');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');
    cy.createKeycloakUser(superEmail, 'E2E', 'SuperAdmin');

    createOrgAs(memberEmail, 'Reviewed Org', uniqueSlug()).then((orgId) => {
      makeSuperUser(superEmail).then(() => {
        tokenFor(memberEmail).then((token) => {
          const submit = (title: string) =>
            cy
              .request({
                method: 'POST',
                url: `${API}/api/feedback`,
                headers: { Authorization: `Bearer ${token}` },
                body: { type: 'OTHER', title, description: 'Some detail here' },
              })
              .then((res) => res.body.id as string);

          submit('Please decline this one').then(() => {
            submit('Please credit this one').then(() => {
              cy.loginAs(superEmail);
              cy.visit('/admin/feedback');
              cy.contains('h1', 'Feedback & Credits').should('be.visible');

              // Decline
              cy.contains('p', 'Please decline this one')
                .closest('.space-y-2')
                .within(() => cy.contains('button', 'Decline').click());
              cy.contains('p', 'Please decline this one').should('not.exist'); // hideResolved default true

              // Grant against the other submission — this org has no subscription
              // record at all, so CreditService's NO_PADDLE_SUBSCRIPTION skip branch
              // fires: no outbound Paddle call, and the grant still succeeds.
              cy.contains('p', 'Please credit this one')
                .closest('.space-y-2')
                .within(() => cy.contains('button', 'Grant credit').click());

              cy.get('#grant-amount').type('50');
              cy.get('#grant-reason').type('Great bug report');
              cy.get('#grant-reason').closest('form').find('button[type="submit"]').click();

              cy.contains('Credit recorded, but not reflected in Paddle yet').should('be.visible');
              cy.contains("This org has no real, active Paddle subscription yet").should('be.visible');
              cy.get('[data-sonner-toast]').should('not.exist');

              cy.contains('button', 'Close').click(); // close the modal (syncWarning state)
              cy.contains('p', 'Please credit this one').should('not.exist'); // now CREDITED, hidden

              // Standalone grant via the page-level button (no submission link).
              cy.get('button').contains('Grant credit').first().click();
              cy.get('#grant-amount').closest('form').find('select').select(orgId);
              cy.get('#grant-amount').type('75');
              cy.get('#grant-reason').type('Discretionary goodwill credit');
              cy.get('#grant-reason').closest('form').find('button[type="submit"]').click();
              cy.contains('Credit recorded, but not reflected in Paddle yet').should('be.visible');
              cy.get('[data-sonner-toast]').should('not.exist');
              cy.get('button').contains('×').click();

              // Ledger shows both entries with the correct origin.
              cy.get('select').last().select(orgId);
              cy.contains('td', '50').should('be.visible');
              cy.contains('td', '75').should('be.visible');
              cy.contains('From submission').should('be.visible');
              cy.contains('Discretionary').should('be.visible');
            });
          });
        });
      });
    });

    cy.deleteKeycloakUser(memberEmail);
    cy.deleteKeycloakUser(superEmail);
  });

  it('disables the submit button for a below-minimum, zero, negative, or non-integer amount', () => {
    const superEmail = uniqueEmail('feedback-super-validation');
    const targetEmail = uniqueEmail('grant-target-org');
    cy.createKeycloakUser(superEmail, 'E2E', 'SuperAdmin');
    cy.createKeycloakUser(targetEmail, 'E2E', 'Target');

    createOrgAs(targetEmail, 'Validation Org', uniqueSlug()).then((orgId) => {
      makeSuperUser(superEmail).then(() => {
        cy.loginAs(superEmail);
        cy.visit('/admin/feedback');

        cy.get('button').contains('Grant credit').first().click();
        cy.get('#grant-amount').closest('form').find('select').select(orgId);
        cy.get('#grant-reason').type('Testing validation');
        const submitButton = () => cy.get('#grant-reason').closest('form').find('button[type="submit"]');

        for (const amount of ['4', '0', '-5', '3.5']) {
          cy.get('#grant-amount').clear();
          if (amount) cy.get('#grant-amount').type(amount);
          submitButton().should('be.disabled');
        }

        cy.get('#grant-amount').clear();
        cy.get('#grant-amount').type('5');
        submitButton().should('not.be.disabled');
      });
    });

    cy.deleteKeycloakUser(superEmail);
    cy.deleteKeycloakUser(targetEmail);
  });
});

describe('Org Settings — credit balance', () => {
  it('shows the balance to the owner after a grant, updates it via poll without a manual reload, and hides it entirely from a plain member', () => {
    const ownerEmail = uniqueEmail('balance-owner');
    const memberEmail = uniqueEmail('balance-member');
    const superEmail = uniqueEmail('balance-super');
    cy.createKeycloakUser(ownerEmail, 'E2E', 'Owner');
    cy.createKeycloakUser(memberEmail, 'E2E', 'Member');
    cy.createKeycloakUser(superEmail, 'E2E', 'SuperAdmin');

    createOrgWithSubscription(ownerEmail, 'Balance Org', uniqueSlug()).then((orgId) => {
      makeSuperUser(superEmail);
      userIdFor(memberEmail).then((memberId) => addMember(orgId, ownerEmail, memberId, 'MEMBER'));

      cy.loginAs(ownerEmail);
      cy.visit('/org/settings');
      cy.contains('Credit balance').should('not.exist'); // nothing granted yet

      tokenFor(superEmail).then((superToken) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/super-admin/credits/grant`,
          headers: { Authorization: `Bearer ${superToken}` },
          body: { orgId, amount: 20, reason: 'Initial grant' },
        });
      });

      cy.visit('/org/settings');
      cy.contains('Credit balance').should('be.visible');
      cy.contains('20').should('be.visible');

      // The balance query polls every 10s (useOrgCreditBalance's own
      // CREDIT_BALANCE_POLL_MS) rather than requiring a reload — grant a second
      // credit via the API without touching the page, then wait past one poll
      // interval and assert the DOM picked it up on its own.
      tokenFor(superEmail).then((superToken) => {
        cy.request({
          method: 'POST',
          url: `${API}/api/super-admin/credits/grant`,
          headers: { Authorization: `Bearer ${superToken}` },
          body: { orgId, amount: 30, reason: 'Second grant, proves the poll' },
        });
      });
      cy.contains('50', { timeout: 15000 }).should('be.visible');

      // A plain MEMBER never even fires the balance query (isOwnerOrAdmin gates it),
      // so the section — which the owner just saw above — isn't rendered at all.
      cy.loginAs(memberEmail);
      cy.visit('/org/settings');
      cy.contains('Credit balance').should('not.exist');
    });

    cy.deleteKeycloakUser(ownerEmail);
    cy.deleteKeycloakUser(memberEmail);
    cy.deleteKeycloakUser(superEmail);
  });
});
