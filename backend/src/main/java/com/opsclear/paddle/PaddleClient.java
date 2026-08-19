package com.opsclear.paddle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper over Paddle's REST API (ADR-0044). Deliberately minimal — only the
 * calls JOB-173 needs, not a general-purpose Paddle SDK. Uses the injected
 * {@link RestClient.Builder} (not the static {@code RestClient.builder()}) so it
 * picks up Spring Boot's Jackson auto-configuration.
 *
 * <p>Paddle does not support creating a Subscription directly via the API — Paddle
 * creates it automatically once a customer completes an embedded checkout (Paddle.js,
 * JOB-178). So there is no {@code createSubscription} here: {@link #createCustomer}
 * is the API-callable half of "initiate a subscription"; the Subscription record
 * itself only exists after checkout completes and JOB-174's webhook syncs it in.
 */
@Component
public class PaddleClient {

    private final RestClient restClient;

    public PaddleClient(RestClient.Builder builder,
                         @Value("${paddle.base-url}") String baseUrl,
                         @Value("${paddle.api-key}") String apiKey) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public PaddleCustomer createCustomer(String email, String name) {
        Map<String, Object> body = Map.of("email", email, "name", name);
        PaddleEnvelope<PaddleCustomer> response = restClient.post()
                .uri("/customers")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleCustomer>>() { });
        return response.data();
    }

    public PaddleSubscription updateSubscriptionItems(
            String subscriptionId, List<PaddleSubscriptionItem> items, String prorationBillingMode) {
        Map<String, Object> body = Map.of(
                "items", items,
                "proration_billing_mode", prorationBillingMode);
        PaddleEnvelope<PaddleSubscription> response = restClient.patch()
                .uri("/subscriptions/{id}", subscriptionId)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleSubscription>>() { });
        return response.data();
    }

    public PaddleSubscriptionPreview previewUpdateSubscriptionItems(
            String subscriptionId, List<PaddleSubscriptionItem> items, String prorationBillingMode) {
        Map<String, Object> body = Map.of(
                "items", items,
                "proration_billing_mode", prorationBillingMode);
        PaddleEnvelope<PaddleSubscriptionPreview> response = restClient.patch()
                .uri("/subscriptions/{id}/preview", subscriptionId)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleSubscriptionPreview>>() { });
        return response.data();
    }

    public PaddleProduct createProduct(String name) {
        Map<String, Object> body = Map.of("name", name, "tax_category", "saas");
        PaddleEnvelope<PaddleProduct> response = restClient.post()
                .uri("/products")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleProduct>>() { });
        return response.data();
    }

    public PaddlePrice createPrice(
            String productId, String description, String interval, String amountMinorUnits, String currencyCode) {
        Map<String, Object> body = Map.of(
                "description", description,
                "product_id", productId,
                "billing_cycle", Map.of("interval", interval, "frequency", 1),
                "unit_price", Map.of("amount", amountMinorUnits, "currency_code", currencyCode),
                // Paddle defaults to quantity 1-100 if omitted, which lets the checkout
                // overlay show a stepper for buying multiple of the same tier/add-on —
                // meaningless for a subscription plan, so lock it to exactly 1.
                "quantity", Map.of("minimum", 1, "maximum", 1));
        PaddleEnvelope<PaddlePrice> response = restClient.post()
                .uri("/prices")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddlePrice>>() { });
        return response.data();
    }

    public void archivePrice(String priceId) {
        restClient.patch()
                .uri("/prices/{id}", priceId)
                .body(Map.of("status", "archived"))
                .retrieve()
                .toBodilessEntity();
    }

    // No status filter — billing history should show failed/past-due attempts too, not
    // just successful ones, so the customer can see why they were charged (or not)
    // rather than a sanitized success-only view.
    public List<PaddleTransaction> listBillingHistory(String customerId) {
        PaddleEnvelope<List<PaddleTransaction>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/transactions")
                        .queryParam("customer_id", customerId)
                        .queryParam("order_by", "billed_at[DESC]")
                        .queryParam("per_page", 20)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<List<PaddleTransaction>>>() { });
        return response.data();
    }

    public PaddleSubscription cancelSubscription(String subscriptionId) {
        Map<String, Object> body = Map.of("effective_from", "next_billing_period");
        PaddleEnvelope<PaddleSubscription> response = restClient.post()
                .uri("/subscriptions/{id}/cancel", subscriptionId)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleSubscription>>() { });
        return response.data();
    }

    public PaddleSubscription removeScheduledCancellation(String subscriptionId) {
        // Map.of rejects a null value — this genuinely needs to send
        // {"scheduled_change": null} to clear it, not omit the field.
        Map<String, Object> body = Collections.singletonMap("scheduled_change", null);
        PaddleEnvelope<PaddleSubscription> response = restClient.patch()
                .uri("/subscriptions/{id}", subscriptionId)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleSubscription>>() { });
        return response.data();
    }

    public PaddleTransaction getUpdatePaymentMethodTransaction(String subscriptionId) {
        PaddleEnvelope<PaddleTransaction> response = restClient.get()
                .uri("/subscriptions/{id}/update-payment-method-transaction", subscriptionId)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleTransaction>>() { });
        return response.data();
    }

    // A one-time, non-checkout discount (JOB-180): unlike an Adjustment, this works
    // regardless of the subscription's collection mode, and reduces a future charge
    // rather than moving money right now — matching what ADR-0043 actually promised.
    // recur:false was the first attempt here and Paddle rejects it outright when
    // attaching to a subscription (subscription_one_off_discount_not_valid, confirmed
    // via a real sandbox 400) — a subscription-attached discount must be "recurring"
    // by Paddle's rules. recur:true + maximum_recurring_intervals:1 satisfies that, but
    // confirmed via a real sandbox test that it does NOT limit it to one transaction —
    // three immediate/prorated charges inside the same billing period each got the
    // full discount applied separately (verified against real Paddle transaction data:
    // discount_id present + a nonzero details.totals.discount on all three). recur/
    // maximum_recurring_intervals only govern which *billing periods* it's eligible in,
    // not how many transactions within a period can redeem it — usage_limit is the
    // separate field that actually caps total redemptions, confirmed as the fix here.
    // enabled_for_checkout:false keeps it from ever being redeemable as a public
    // coupon code.
    //
    // maximum_recurring_intervals:1 originally also meant the discount's "valid
    // until" was whatever the org's *current billing period* end happened to be —
    // fine for a monthly org granted a credit right after renewal, but far too short
    // for one granted right before renewal, and completely tied to billing-cycle
    // timing rather than to the credit itself. DISCOUNT_MAX_RECURRING_INTERVALS is
    // raised well past 1 so it's never the binding constraint (usage_limit:1 alone
    // already caps total redemptions to one, regardless of how many intervals the
    // discount is nominally eligible across); expires_at is the field that actually
    // governs how long a credit stays redeemable.
    private static final int DISCOUNT_VALIDITY_DAYS = 90;
    private static final int DISCOUNT_MAX_RECURRING_INTERVALS = 12;

    public PaddleDiscount createOneTimeDiscount(String amountMinorUnits, String currencyCode, String description) {
        Map<String, Object> body = Map.of(
                "type", "flat",
                "amount", amountMinorUnits,
                "currency_code", currencyCode,
                "description", description,
                "enabled_for_checkout", false,
                "recur", true,
                "maximum_recurring_intervals", DISCOUNT_MAX_RECURRING_INTERVALS,
                "usage_limit", 1,
                "expires_at", Instant.now().plus(DISCOUNT_VALIDITY_DAYS, ChronoUnit.DAYS).toString());
        PaddleEnvelope<PaddleDiscount> response = restClient.post()
                .uri("/discounts")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleDiscount>>() { });
        return response.data();
    }

    // A subscription has at most one discount attached at a time — attaching a new one
    // replaces whatever was there before. If a prior one-time credit discount hasn't
    // been consumed by a transaction yet when a second credit is granted, this silently
    // drops the first one — still a known limitation, unrelated to the multi-use bug
    // below, which is what removeDiscountFromSubscription solves.
    public PaddleSubscription attachDiscountToSubscription(String subscriptionId, String discountId) {
        // effective_from is required alongside id — confirmed via a real sandbox 400
        // ("effective_from is required") the first time this shipped. "immediately"
        // matches the intent (apply to whatever transaction comes next for the
        // subscription, not deferred to the following renewal specifically).
        Map<String, Object> body = Map.of(
                "discount", Map.of("id", discountId, "effective_from", "immediately"));
        PaddleEnvelope<PaddleSubscription> response = restClient.patch()
                .uri("/subscriptions/{id}", subscriptionId)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleSubscription>>() { });
        return response.data();
    }

    // recur:true + maximum_recurring_intervals:1 + usage_limit:1 (see
    // createOneTimeDiscount) turned out to still not be transaction-scoped —
    // confirmed via real Paddle transaction data that a discount applies to every
    // transaction within the same billing period, not just the first, and
    // usage_limit only decrements once per period regardless. This is the actual
    // fix: called from PaddleWebhookService as soon as a transaction.completed event
    // reports our discount was used, so no second transaction in the same period can
    // catch it too. Map.of rejects a null value — this genuinely needs to send
    // {"discount": null} to clear it, same pattern as removeScheduledCancellation.
    public PaddleSubscription removeDiscountFromSubscription(String subscriptionId) {
        Map<String, Object> body = Collections.singletonMap("discount", null);
        PaddleEnvelope<PaddleSubscription> response = restClient.patch()
                .uri("/subscriptions/{id}", subscriptionId)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleSubscription>>() { });
        return response.data();
    }
}
