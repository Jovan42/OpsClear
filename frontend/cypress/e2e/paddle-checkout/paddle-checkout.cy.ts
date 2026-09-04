// ADR-0049 Appendix §23 (Paddle Checkout / Upgrade / Downgrade / Cancellation).
//
// SCOPE NOTE vs. subscription-selection (§22): the tier/add-on picker's own
// mechanics (sliders, annual toggle, savings banner, locked-addon rendering) belong
// there — this file is about the CHECKOUT LIFECYCLE specifically: opening/abandoning
// checkout, the upgrade-preview-then-confirm flow, the pending-downgrade banner,
// cancel/resume, and the mixed-change client-side block.
//
// PADDLE SANDBOX REACHABILITY (read before extending this file — this took real
// investigation to pin down, don't rediscover it the hard way):
//
// This environment has no live Paddle sandbox credentials (no VITE_PADDLE_CLIENT_
// TOKEN client-side; PADDLE_API_KEY is wired from a GitHub secret in CI and is
// simply absent locally). Two DIFFERENT things depend on that, and they fail
// differently:
//
//   1. Client-side checkout (paddleCheckout.ts's initializePaddle/Checkout.open) —
//      without a real client token, Paddle.js's own init silently resolves to
//      `undefined`, so `paddle?.Checkout.open()` is a harmless no-op. The app's own
//      container (`.paddle-checkout-frame`) still mounts; nothing from Paddle ever
//      populates it. This is fully fine to drive in Cypress — the "opening and
//      abandoning" test below does, for real, no stubbing needed.
//
//   2. Every server-side PaddleClient call (subscription preview, update, cancel,
//      resume, initiate) makes a REAL outbound HTTPS call to Paddle's API — this
//      was empirically confirmed (not assumed): clicking Save on an upgrade against
//      the real local backend logged genuine Paddle API error bodies
//      ("authentication_malformed" / "invalid_url") from developer.paddle.com,
//      meaning the request actually left the building. Without a real key this
//      always 500s. So every one of those five flows is stubbed here via
//      `cy.intercept` on our OWN API (not Paddle's) — this proves the FRONTEND's
//      wiring (right endpoint, right payload, right modal copy, right post-success
//      state), which nothing else in the suite touches. It does not re-prove
//      Paddle's own proration math or that a real update actually round-trips to
//      Paddle — that's what PaddleSubscriptionIntegrationTest's preview_*/update_*/
//      cancel_*/resume_* tests already cover, mocking PaddleClient the same way.
//
// A THIRD option — simulating Paddle's webhook callback instead of stubbing our own
// API — was tried first and abandoned for a real reason, not convenience: signing a
// webhook body with `PADDLE_WEBHOOK_SECRET` empty (this env's default, per
// application.properties' `${PADDLE_WEBHOOK_SECRET:}`) doesn't yield a webhook the
// backend accepts — it crashes. `PaddleWebhookService#hmacSha256Hex` constructs a
// `javax.crypto.spec.SecretKeySpec` from the configured secret's bytes; Java's
// SecretKeySpec throws `IllegalArgumentException("Empty key")` for a zero-length
// key, uncaught, so EVERY webhook POST 500s in this exact configuration (confirmed
// via the backend's own log: "Unexpected error: Empty key"). This is a real,
// independently-filed bug (empty secret should fail signature verification
// cleanly, not crash) — see the linked bug PR — but fixing it doesn't unlock
// webhook-simulation here: a Java Mac genuinely cannot HMAC with an empty key, so
// there is no secret value this environment can sign with that the backend would
// also accept, short of provisioning a real non-empty PADDLE_WEBHOOK_SECRET for
// local/CI use — an infra decision out of scope for a single test file. So instead,
// wherever this file needs to assert how the UI *renders* durable, webhook-authored
// state (a scheduled cancellation, a PAST_DUE status), it seeds that state directly
// via `cy.task('queryDb', ...)` — the same fixture technique `orgApi.ts`'s
// `createOrgWithAddonPastDue` already uses, and honest about what's being tested:
// UI rendering of a given state, not the webhook pipeline that would normally
// produce it (already covered end-to-end by PaddleWebhookIntegrationTest).

import {
  uniqueEmail,
  uniqueSlug,
  tokenFor,
  createOrgWithSubscription,
  createOrgWithActivePaddleSubscription,
  API,
} from '../../support/orgApi';

// Mirrors landing-page.cy.ts's own setRangeValue — React's controlled <input type=
// "range"> only reacts to a real 'input' event dispatched after the native value
// setter runs (a plain .val()/.trigger('change') leaves React's internal value
// tracker out of sync, per that spec's own comment).
function setRangeValue(alias: string, value: number) {
  cy.get(alias).then(($el) => {
    const el = $el[0] as HTMLInputElement;
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')!.set!;
    setter.call(el, String(value));
    el.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

describe('Paddle Checkout — opening and abandoning', () => {
  it('"Continue to payment" opens the inline checkout container with the selected item list and total; abandoning returns to the picker with no subscription created', () => {
    const email = uniqueEmail('checkout-open');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithSubscription(email, 'Falcon Corp', uniqueSlug()).then((orgId) => {
      cy.loginAs(email);
      cy.visit('/org/settings');

      cy.contains('button', 'Continue to payment').should('be.visible').click();

      cy.get('.paddle-checkout-frame').should('exist');
      cy.contains('Total').should('be.visible');
      cy.contains('button', 'Back').should('be.visible');

      cy.contains('button', 'Back').click();
      cy.get('.paddle-checkout-frame').should('not.exist');
      cy.contains('button', 'Continue to payment').should('be.visible');

      tokenFor(email).then((token) => {
        cy.request({
          method: 'GET',
          url: `${API}/api/organisations/${orgId}/subscription`,
          headers: { Authorization: `Bearer ${token}` },
        }).then((res) => {
          expect(res.body.paddleSubscriptionId ?? null).to.equal(null);
        });
      });
    });

    cy.deleteKeycloakUser(email);
  });
});

describe('Paddle Checkout — mixed-change client-side block', () => {
  it('adding a pricier add-on while also moving to a cheaper base plan is blocked before any request fires', () => {
    const email = uniqueEmail('mixed-change');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    // tierIndex 2 = (5 members, 10 projects) per V016's seed order — starts with
    // room to move the project slider DOWN to a cheaper tier.
    createOrgWithActivePaddleSubscription(email, 'Falcon Corp', uniqueSlug(), 2).then(() => {
      cy.loginAs(email);
      cy.visit('/org/settings');

      // No cy.intercept assertion needed to prove no request fires: isMixedAddonChange
      // is checked before either the debounced live-preview effect or handleSave's own
      // previewPaddleUpdate call, so a real network request is unreachable from this
      // state by construction, not just by timing — see SubscriptionSection.tsx's own
      // `if (isMixedAddonChange(...)) return;` guard ahead of both call sites.
      cy.get('input[type="range"]').eq(1).as('projectSlider');
      setRangeValue('@projectSlider', 0); // fewer projects -> cheaper base plan
      cy.get('.grid.grid-cols-1.sm\\:grid-cols-2 button').first().click(); // add an add-on

      cy.contains("You're adding something more expensive and removing something cheaper").should('be.visible');
      cy.contains('button', 'Save subscription').should('be.disabled');
    });

    cy.deleteKeycloakUser(email);
  });
});

describe('Paddle Checkout — upgrade preview and confirm', () => {
  it('moving to a pricier tier previews the immediate charge, then confirming submits the update with the right payload', () => {
    const email = uniqueEmail('upgrade');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    // tierIndex 0 = (5 members, 3 projects, cheapest) — room to move UP.
    createOrgWithActivePaddleSubscription(email, 'Falcon Corp', uniqueSlug(), 0).then(() => {
      cy.loginAs(email);
      cy.visit('/org/settings');

      cy.intercept('POST', '**/subscription/paddle/preview', {
        statusCode: 200,
        body: { upgrade: true, immediateChargeAmount: 2000, creditApplied: null, currency: 'EUR', effectiveAt: null },
      }).as('preview');
      cy.intercept('PUT', '**/subscription/paddle', { statusCode: 200, body: {} }).as('update');

      cy.get('input[type="range"]').first().as('memberSlider');
      setRangeValue('@memberSlider', 1); // 5 -> 10 members: strictly pricier, no addon change

      cy.contains('button', 'Save subscription').should('not.be.disabled').click();
      cy.wait('@preview');

      cy.get('.z-50:visible').within(() => {
        cy.contains('Confirm upgrade').should('be.visible');
        cy.contains('Charged now').should('be.visible');
        cy.contains('2.000 EUR').should('be.visible'); // fmt() uses sr-RS grouping
        cy.contains('button', 'Confirm change').click();
      });

      cy.wait('@update').its('request.body').then((body) => {
        expect(body).to.have.property('tierId');
        expect(body.addonIds).to.deep.equal([]);
      });

      cy.contains('Subscription saved.').should('be.visible');
      cy.get('.z-50:visible').should('not.exist');
    });

    cy.deleteKeycloakUser(email);
  });
});

describe('Paddle Checkout — downgrade previews a no-charge-now change', () => {
  it('moving to a cheaper tier shows a no-charge-now preview naming the effective date, and confirming submits the update', () => {
    const email = uniqueEmail('downgrade');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    // tierIndex 2 = (5 members, 10 projects) — room to move DOWN.
    createOrgWithActivePaddleSubscription(email, 'Falcon Corp', uniqueSlug(), 2).then(() => {
      cy.loginAs(email);
      cy.visit('/org/settings');

      cy.intercept('POST', '**/subscription/paddle/preview', {
        statusCode: 200,
        body: { upgrade: false, immediateChargeAmount: 0, creditApplied: null, currency: 'EUR', effectiveAt: '2099-02-01T00:00:00.000Z' },
      }).as('preview');
      cy.intercept('PUT', '**/subscription/paddle', { statusCode: 200, body: {} }).as('update');

      cy.get('input[type="range"]').eq(1).as('projectSlider');
      setRangeValue('@projectSlider', 0); // 10 -> 3 projects: strictly cheaper

      cy.contains('button', 'Save subscription').should('not.be.disabled').click();
      cy.wait('@preview');

      cy.get('.z-50:visible').within(() => {
        cy.contains('Confirm plan change').should('be.visible');
        cy.contains("No charge now. You'll keep your current plan until").should('be.visible');
        cy.contains('button', 'Confirm change').click();
      });

      cy.wait('@update').its('request.body').then((body) => {
        expect(body).to.have.property('tierId');
      });

      cy.contains('Subscription saved.').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('a subscription with a real pending downgrade already scheduled (however it got there — normally the webhook path, JOB-198) renders the pending-change banner with its line items and date', () => {
    const email = uniqueEmail('pending-downgrade');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    // tierIndex 2 = current (5, 10 projects); tierIndex 0 = cheaper pending target.
    createOrgWithActivePaddleSubscription(email, 'Falcon Corp', uniqueSlug(), 2).then(({ orgId }) => {
      tokenFor(email).then((token) =>
        cy
          .request({
            method: 'GET',
            url: `${API}/api/subscriptions/catalog`,
            headers: { Authorization: `Bearer ${token}` },
          })
          .then(({ body: catalog }: { body: { tiers: Array<{ id: string }> } }) =>
            cy.task('queryDb', {
              sql: `UPDATE org_subscriptions
                    SET pending_tier_id = $2, paddle_pending_downgrade_effective_at = '2099-03-01T00:00:00Z'
                    WHERE org_id = $1`,
              params: [orgId, catalog.tiers[0].id],
            }),
          ),
      );

      cy.loginAs(email);
      cy.visit('/org/settings');

      cy.contains('Plan change scheduled').should('be.visible');
      cy.contains('Your plan will switch to the following on').should('be.visible');
      cy.contains('button', 'Cancel pending downgrade').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });
});

describe('Paddle Checkout — cancellation and resume', () => {
  it('cancelling shows an immediate confirmation and reveals Resume; resuming shows a confirmation toast', () => {
    const email = uniqueEmail('cancel-resume');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithActivePaddleSubscription(email, 'Falcon Corp', uniqueSlug()).then(() => {
      cy.loginAs(email);
      cy.visit('/org/settings');

      cy.intercept('POST', '**/subscription/paddle/cancel', { statusCode: 200, body: {} }).as('cancel');
      cy.intercept('POST', '**/subscription/paddle/resume', { statusCode: 200, body: {} }).as('resume');

      cy.contains('button', 'Cancel subscription').should('be.visible').click();
      cy.get('.z-50:visible').within(() => {
        cy.contains('h2', 'Cancel subscription?').should('be.visible');
        cy.contains('button', 'Cancel subscription').click();
      });
      cy.wait('@cancel');

      cy.contains('Your subscription will remain active until the end of your current billing period').should('be.visible');
      cy.contains('button', 'Resume subscription').should('be.visible').click();
      cy.wait('@resume');

      cy.contains('Cancellation removed').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('a subscription with a real scheduled cancellation (however it got there — normally the webhook path, JOB-197) renders the durable "cancels on {date}" banner', () => {
    const email = uniqueEmail('scheduled-cancel');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithActivePaddleSubscription(email, 'Falcon Corp', uniqueSlug()).then(({ orgId }) => {
      cy.task('queryDb', {
        sql: "UPDATE org_subscriptions SET paddle_scheduled_cancellation_at = '2099-01-15T00:00:00Z' WHERE org_id = $1",
        params: [orgId],
      });

      cy.loginAs(email);
      cy.visit('/org/settings');

      cy.contains('Your subscription is set to cancel on').should('be.visible');
      cy.contains('button', 'Resume subscription').should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });

  it('a subscription.updated webhook setting status=past_due (simulated here via a direct DB write — see this file\'s header comment for why the webhook itself can\'t be signed in this environment) is reflected in the billing status badge', () => {
    const email = uniqueEmail('pastdue');
    cy.createKeycloakUser(email, 'E2E', 'Tester');
    createOrgWithActivePaddleSubscription(email, 'Falcon Corp', uniqueSlug()).then(({ orgId }) => {
      cy.task('queryDb', {
        sql: "UPDATE org_subscriptions SET subscription_status = 'PAST_DUE' WHERE org_id = $1",
        params: [orgId],
      });

      cy.loginAs(email);
      cy.visit('/org/settings');
      cy.contains('Past due').should('be.visible');
      cy.contains("Your last payment didn't go through").should('be.visible');
    });

    cy.deleteKeycloakUser(email);
  });
});
