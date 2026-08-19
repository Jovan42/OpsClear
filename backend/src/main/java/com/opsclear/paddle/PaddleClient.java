package com.opsclear.paddle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
    // by Paddle's rules. recur:true + maximum_recurring_intervals:1 satisfies that
    // while still only actually applying to one billing period, which is the one-time
    // behavior we actually want. enabled_for_checkout:false keeps it from ever being
    // redeemable as a public coupon code.
    public PaddleDiscount createOneTimeDiscount(String amountMinorUnits, String currencyCode, String description) {
        Map<String, Object> body = Map.of(
                "type", "flat",
                "amount", amountMinorUnits,
                "currency_code", currencyCode,
                "description", description,
                "enabled_for_checkout", false,
                "recur", true,
                "maximum_recurring_intervals", 1);
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
    // drops the first one. Not yet solved (JOB-180) — flagged as a known limitation
    // pending live sandbox verification of how Paddle actually behaves here.
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
}
