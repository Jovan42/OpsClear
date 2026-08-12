package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The envelope every Paddle webhook event shares, regardless of type: an id, a
 * type, and a {@code data} object whose shape depends on {@code eventType}. Only
 * {@code subscription.*} events are read as {@link PaddleWebhookSubscriptionData}
 * here — for any other event type, {@code data}'s fields are simply left unused.
 */
public record PaddleWebhookEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        PaddleWebhookSubscriptionData data) {
}
