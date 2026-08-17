package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * A subscription's {@code current_billing_period} on a REST API response — not the
 * webhook shape, see {@link PaddleWebhookBillingPeriod}.
 */
public record PaddleSubscriptionBillingPeriod(
        @JsonProperty("starts_at") Instant startsAt,
        @JsonProperty("ends_at") Instant endsAt) {
}
