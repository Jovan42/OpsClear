package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** Only the fields this codebase actually reads from Paddle's Transaction response. */
public record PaddleTransaction(
        String id,
        String status,
        List<PaddleTransactionItem> items,
        @JsonProperty("billed_at") Instant billedAt,
        @JsonProperty("currency_code") String currencyCode,
        PaddleTransactionDetails details) {
}
