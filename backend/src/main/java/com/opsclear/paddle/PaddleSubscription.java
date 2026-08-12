package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the fields this codebase actually reads from Paddle's Subscription response. */
public record PaddleSubscription(String id, String status, @JsonProperty("customer_id") String customerId) {
}
