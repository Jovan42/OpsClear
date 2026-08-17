package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response of {@code PATCH /subscriptions/{id}/preview} (JOB-198) — same shape as a
 * real update, but nothing is actually applied or billed. Only the fields this
 * codebase reads: the current period (for the downgrade "takes effect on" date) and
 * the immediate transaction (for the upgrade "charged now" amount, when present).
 */
public record PaddleSubscriptionPreview(
        @JsonProperty("current_billing_period") PaddleSubscriptionBillingPeriod currentBillingPeriod,
        @JsonProperty("immediate_transaction") PaddlePreviewImmediateTransaction immediateTransaction) {
}
