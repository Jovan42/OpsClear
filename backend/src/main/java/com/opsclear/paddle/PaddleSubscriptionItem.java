package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One line item in a Paddle Subscription's {@code items} array — a Price + quantity. */
public record PaddleSubscriptionItem(@JsonProperty("price_id") String priceId, int quantity) {
}
