package com.opsclear.paddle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public Optional<PaddleTransaction> findLatestCompletedTransaction(String customerId) {
        PaddleEnvelope<List<PaddleTransaction>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/transactions")
                        .queryParam("customer_id", customerId)
                        .queryParam("status", "completed")
                        .queryParam("order_by", "billed_at[DESC]")
                        .queryParam("per_page", 1)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<List<PaddleTransaction>>>() { });
        return response.data().stream().findFirst();
    }

    // No status filter, unlike findLatestCompletedTransaction — billing history should
    // show failed/past-due attempts too, not just successful ones, so the customer can
    // see why they were charged (or not) rather than a sanitized success-only view.
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

    public PaddleAdjustment createCreditAdjustment(
            String transactionId, String itemId, String amountMinorUnits, String reason) {
        Map<String, Object> body = Map.of(
                "action", "credit",
                "type", "partial",
                "transaction_id", transactionId,
                "reason", reason,
                "items", List.of(Map.of("item_id", itemId, "type", "partial", "amount", amountMinorUnits)));
        PaddleEnvelope<PaddleAdjustment> response = restClient.post()
                .uri("/adjustments")
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<PaddleEnvelope<PaddleAdjustment>>() { });
        return response.data();
    }
}
