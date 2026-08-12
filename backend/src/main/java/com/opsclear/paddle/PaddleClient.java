package com.opsclear.paddle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
}
