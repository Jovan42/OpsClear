// ADR-0049 Appendix §24 (Billing History & Past-Due State), part of MIL-033's billing
// backfill. Every case tagged [a]/[b]/[c] per ADR-0049 §2 — see that section for what
// each means.
//
// BACKEND COVERAGE ALREADY EXISTS for nearly every [b]-tagged validation bullet here:
// backend/src/test/java/com/opsclear/integration/PaddleSubscriptionIntegrationTest.java
// already has getBillingHistory_shouldReturn403_forNonOwner,
// ...shouldReturn400_forInternalOrg, ...shouldReturn200WithEmptyList_whenNoPaddleCustomerYet,
// ...shouldReturn200WithEmptyList_whenNoSubscriptionRecordAtAll, plus the parallel
// getUpdatePaymentMethodTransaction_* set. This spec does NOT duplicate those — it
// covers the [a]-tagged frontend rendering (badge colors, past-due banner, null-safe
// transaction fields, read-only-pages-load-fully) and the [b]-tagged PAST_DUE→CANCELED
// UI-lockout transition, which has no existing coverage anywhere.
//
// Fixture strategy (ADR-0049 §6's governing principle: prefer a fixture over a live
// external dependency everywhere except the test whose actual subject is that
// dependency): this spec's subject is UI rendering of billing state, not Paddle
// webhook delivery — that's JOB-230's subject, already exhaustively covered by
// PaddleWebhookIntegrationTest.java's hand-signed-HMAC tests. So PAST_DUE/CANCELED
// status is seeded directly via seedRealPaddleSubscription() (a DB write shaped
// exactly like what a real webhook produces — a real-looking paddle_subscription_id
// + subscription_status), not driven through a live webhook POST or a real Paddle
// sandbox checkout. No PADDLE_API_KEY is configured in this environment (checked
// before writing this spec), so a live sandbox call was never an option regardless —
// this fixture approach also happens to be the only viable one here, not just the
// ADR-preferred one.
//
// The one live network boundary this spec DOES need to cross — GET .../transactions,
// which the backend proxies straight from Paddle's own API — is intercepted via
// cy.intercept for the two tests that need real transaction data rendered (status
// badges, null-field handling). Every other test either uses the real (correctly
// empty, since no paddle_customer_id is seeded) response, or doesn't touch billing
// history at all.

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgAs,
  createProjectAs,
  seedRealPaddleSubscription,
  API,
} from '../../support/orgApi';

/** Selects `addonKeys` for `email`'s org via the real subscription PUT endpoint (not
 *  `cy.setUpOrgSubscription`, which always sends `addonIds: []`) — this spec needs
 *  specific addons genuinely active so PAST_DUE-vs-CANCELED addon-gating is
 *  observable, unlike `createOrgWithFullAccess`'s `is_internal` bypass, which skips
 *  hasRealBilling()/hasAddon() checks entirely and would make every case here a
 *  no-op. */
function stageSubscriptionWithAddons(email: string, orgId: string, addonKeys: string[]) {
  return tokenFor(email).then((token) =>
    cy
      .request({ method: 'GET', url: `${API}/api/subscriptions/catalog`, headers: { Authorization: `Bearer ${token}` } })
      .then(({ body }: { body: { tiers: Array<{ id: string }>; addons: Array<{ id: string; key: string }> } }) => {
        const addonIds = body.addons.filter((a) => addonKeys.includes(a.key)).map((a) => a.id);
        return cy.request({
          method: 'PUT',
          url: `${API}/api/organisations/${orgId}/subscription`,
          headers: { Authorization: `Bearer ${token}` },
          body: { tierId: body.tiers[0].id, billingCycle: 'MONTHLY', addonIds },
        });
      }),
  );
}

describe('Billing History & Past-Due State', () => {
  it('billing history renders live transactions with status-colored badges [a]', () => {
    const email = uniqueEmail('history-render');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Falcon Corp', uniqueSlug()).then((orgId) => {
      stageSubscriptionWithAddons(email, orgId, []).then(() => {
        seedRealPaddleSubscription(orgId, 'ACTIVE').then(() => {
          cy.intercept('GET', '**/subscription/paddle/transactions', {
            statusCode: 200,
            body: [
              { id: 'txn_1', status: 'completed', billedAt: '2026-06-15T00:00:00Z', currency: 'EUR', totalAmount: 29 },
              { id: 'txn_2', status: 'past_due', billedAt: '2026-07-15T00:00:00Z', currency: 'EUR', totalAmount: 29 },
              { id: 'txn_3', status: 'draft', billedAt: '2026-08-15T00:00:00Z', currency: 'EUR', totalAmount: 29 },
            ],
          }).as('transactions');

          cy.loginAs(email);
          cy.visit('/org/settings');
          cy.contains('button', 'Billing history').click();
          cy.wait('@transactions');

          cy.contains('completed').closest('div.py-3').find('span').should('have.class', 'bg-green-100');
          cy.contains('past_due').closest('div.py-3').find('span').should('have.class', 'bg-red-100');
          cy.contains('draft').closest('div.py-3').find('span').should('have.class', 'bg-gray-100');
          cy.contains('29 EUR').should('be.visible');
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('an org with no Paddle customer yet shows an empty billing history, not an error [a/b]', () => {
    const email = uniqueEmail('history-empty');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Nimbus Corp', uniqueSlug()).then((orgId) => {
      stageSubscriptionWithAddons(email, orgId, []).then(() => {
        cy.loginAs(email);
        cy.visit('/org/settings');
        cy.contains('button', 'Billing history').click();
        cy.contains('No billing history yet.').should('be.visible');
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('a transaction with null billedAt/totalAmount renders a placeholder, not a broken date or NaN [a]', () => {
    const email = uniqueEmail('history-nulls');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Atlas Corp', uniqueSlug()).then((orgId) => {
      stageSubscriptionWithAddons(email, orgId, []).then(() => {
        seedRealPaddleSubscription(orgId, 'ACTIVE').then(() => {
          cy.intercept('GET', '**/subscription/paddle/transactions', {
            statusCode: 200,
            body: [{ id: 'txn_draft', status: 'draft', billedAt: null, currency: null, totalAmount: null }],
          }).as('transactions');

          cy.loginAs(email);
          cy.visit('/org/settings');
          cy.contains('button', 'Billing history').click();
          cy.wait('@transactions');

          cy.contains('Not yet billed').should('be.visible');
          cy.contains('NaN').should('not.exist');
          cy.contains('draft').parents('div.py-3').contains('—').should('be.visible');
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('PAST_DUE shows an amber banner (app-wide and in settings) and keeps subscription management fully reachable [a]', () => {
    const email = uniqueEmail('past-due-banner');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Vega Corp', uniqueSlug()).then((orgId) => {
      stageSubscriptionWithAddons(email, orgId, ['MILESTONES']).then(() => {
        seedRealPaddleSubscription(orgId, 'PAST_DUE').then(() => {
          cy.loginAs(email);

          // App-wide banner (AppLayout's PastDueBanner) — visible on ANY page, not
          // just org settings.
          cy.visit('/projects');
          cy.contains("Your last payment didn't go through").should('be.visible');
          cy.contains('a', 'Update payment method').should('be.visible');

          cy.visit('/org/settings');
          cy.contains('span', 'Past due').should('have.class', 'bg-amber-100');
          cy.contains("Your last payment didn't go through. Update your payment method").should('be.visible');
          // Management stays reachable — both buttons present, not replaced by a
          // locked/read-only state.
          cy.contains('button', 'Update payment method').should('be.visible').and('not.be.disabled');
          cy.contains('button', 'Cancel subscription').should('be.visible').and('not.be.disabled');
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it("a past-due org's addon-gated pages load fully — PAST_DUE alone doesn't degrade them into a locked-feature UI [a]", () => {
    const email = uniqueEmail('past-due-reads');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Comet Corp', uniqueSlug()).then((orgId) => {
      stageSubscriptionWithAddons(email, orgId, ['MILESTONES', 'DASHBOARD']).then(() => {
        seedRealPaddleSubscription(orgId, 'PAST_DUE').then(() => {
          createProjectAs(email, 'Comet Project').then((projectId) => {
            cy.loginAs(email);

            cy.visit(`/projects/${projectId}/dashboard`);
            cy.contains('is not included in your plan').should('not.exist');
            // "Summary" (SummaryCards) always renders, unlike the status-donut chart,
            // which returns null for a job-less project — so this is the reliable
            // "the real dashboard rendered" signal regardless of fixture job count.
            cy.contains('Summary').should('be.visible');

            cy.visit(`/projects/${projectId}/milestones`);
            cy.contains('is not included in your plan').should('not.exist');
            cy.contains('button', '+ New Milestone').should('be.visible');

            cy.visit(`/projects/${projectId}/jobs`);
            cy.contains('is not included in your plan').should('not.exist');
          });
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });

  it('PAST_DUE → CANCELED: addon-gated pages switch to full lockout only once CANCELED, not while merely PAST_DUE [b]', () => {
    const email = uniqueEmail('past-due-to-canceled');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgAs(email, 'Sable Corp', uniqueSlug()).then((orgId) => {
      stageSubscriptionWithAddons(email, orgId, ['MILESTONES']).then(() => {
        createProjectAs(email, 'Sable Project').then((projectId) => {
          seedRealPaddleSubscription(orgId, 'PAST_DUE').then(() => {
            cy.loginAs(email);
            cy.visit(`/projects/${projectId}/milestones`);
            // Not yet locked — PAST_DUE alone doesn't revoke addon access.
            cy.contains('is not included in your plan').should('not.exist');
            cy.contains('button', '+ New Milestone').should('be.visible');

            // The dunning-exhausted transition — a real webhook would drive this
            // (already covered end-to-end by PaddleWebhookIntegrationTest.java's
            // "subscription.canceled sets CANCELED"); seeded directly here since this
            // test's subject is the UI's reaction to the resulting state, not the
            // webhook delivery itself.
            seedRealPaddleSubscription(orgId, 'CANCELED').then(() => {
              cy.visit(`/projects/${projectId}/milestones`);
              cy.contains('is not included in your plan').should('be.visible');
              cy.contains('button', '+ New Milestone').should('not.exist');

              // The billing section itself also disappears once CANCELED —
              // hasRealPaddleBilling() (frontend) requires status !== 'CANCELED'.
              cy.visit('/org/settings');
              cy.contains('span', 'Past due').should('not.exist');
              cy.contains('button', 'Update payment method').should('not.exist');
            });
          });
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });
});
