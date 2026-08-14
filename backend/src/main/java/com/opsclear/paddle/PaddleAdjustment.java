package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the fields this codebase actually reads from Paddle's Adjustment response. */
public record PaddleAdjustment(String id, @JsonProperty("credit_applied_to_balance") boolean creditAppliedToBalance) {
}
