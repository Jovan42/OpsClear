package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * A subscription's {@code current_billing_period} on a webhook event — {@code null}
 * for paused/canceled subscriptions per Paddle's docs, present otherwise. Used
 * (JOB-198) to detect when a period has actually rolled over, by comparing
 * {@code startsAt} against the previously-known value.
 */
public record PaddleWebhookBillingPeriod(@JsonProperty("starts_at") Instant startsAt) {
}
