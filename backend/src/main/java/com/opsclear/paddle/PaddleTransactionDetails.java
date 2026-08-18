package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** The {@code details} object inside a Transaction response. */
public record PaddleTransactionDetails(
        PaddleTransactionTotals totals,
        @JsonProperty("line_items") List<PaddleTransactionLineItem> lineItems) {
}
