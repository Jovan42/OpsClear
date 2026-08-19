package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The envelope for a {@code transaction.*} webhook event — same top-level shape as
 *  {@link PaddleWebhookEvent}, but {@code data} is a transaction, not a subscription. */
public record PaddleWebhookTransactionEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        PaddleWebhookTransactionData data) {
}
