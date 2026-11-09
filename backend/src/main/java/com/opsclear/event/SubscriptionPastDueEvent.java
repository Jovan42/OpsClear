package com.opsclear.event;

import java.util.UUID;

/**
 * An organisation's subscription entered {@code PAST_DUE} (ADR-0051 {@code SUBSCRIPTION_PAST_DUE}). Published only on
 * the transition, not on repeated webhooks while already past-due. A system event with no actor.
 */
public record SubscriptionPastDueEvent(UUID orgId) {
}
