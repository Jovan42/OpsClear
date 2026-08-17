package com.opsclear.paddle;

/** One entry in a {@code subscription.*} webhook's {@code items} array. */
public record PaddleWebhookSubscriptionItem(PaddleWebhookItemPrice price) {
}
