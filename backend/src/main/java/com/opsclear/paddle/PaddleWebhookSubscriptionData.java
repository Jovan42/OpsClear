package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code data} object on a {@code subscription.*} webhook event — every such
 * event carries the subscription's full current state (not a delta), so
 * {@code status} is always the authoritative, current value at the time Paddle sent
 * the event. Unrecognized fields (e.g. a transaction event's {@code data}, which
 * has no {@code customer_id}/{@code status} in this shape) are ignored by Jackson's
 * default configuration rather than failing deserialization.
 */
public record PaddleWebhookSubscriptionData(
        String id,
        @JsonProperty("customer_id") String customerId,
        String status,
        @JsonProperty("scheduled_change") PaddleWebhookScheduledChange scheduledChange,
        @JsonProperty("current_billing_period") PaddleWebhookBillingPeriod currentBillingPeriod) {
}
