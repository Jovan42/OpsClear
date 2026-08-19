package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code data} object on a {@code transaction.*} webhook event — a different
 * shape from {@link PaddleWebhookSubscriptionData} (no {@code customer_id}/
 * {@code status} in this shape), only read for {@code transaction.completed} to
 * detach a one-time credit discount once it's actually been consumed (JOB-180).
 */
public record PaddleWebhookTransactionData(
        String id,
        @JsonProperty("subscription_id") String subscriptionId,
        @JsonProperty("discount_id") String discountId) {
}
