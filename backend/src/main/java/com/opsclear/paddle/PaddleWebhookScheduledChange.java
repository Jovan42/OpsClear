package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * A subscription's {@code scheduled_change} — present when a pause, cancel, or
 * resume has been scheduled but not yet taken effect; {@code null} on the parent
 * {@link PaddleWebhookSubscriptionData} otherwise. Only {@code action == "cancel"}
 * is meaningful to this codebase (JOB-197).
 */
public record PaddleWebhookScheduledChange(
        String action,
        @JsonProperty("effective_at") Instant effectiveAt) {
}
