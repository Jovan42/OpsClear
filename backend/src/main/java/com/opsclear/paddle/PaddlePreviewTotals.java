package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The {@code totals} object inside a preview's {@code immediate_transaction.details}. */
public record PaddlePreviewTotals(
        String total, String discount, @JsonProperty("currency_code") String currencyCode) {
}
