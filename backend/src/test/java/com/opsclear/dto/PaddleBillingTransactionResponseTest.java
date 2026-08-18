package com.opsclear.dto;

import com.opsclear.paddle.PaddleTransaction;
import com.opsclear.paddle.PaddleTransactionDetails;
import com.opsclear.paddle.PaddleTransactionTotals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaddleBillingTransactionResponse")
class PaddleBillingTransactionResponseTest {

    @Test
    @DisplayName("from — maps id/status/billedAt/currency and converts the total from minor units")
    void from_shouldMapAllFields() {
        PaddleTransaction transaction = new PaddleTransaction(
                "txn_123", "completed", List.of(), Instant.parse("2026-08-01T00:00:00Z"), "EUR",
                new PaddleTransactionDetails(new PaddleTransactionTotals("2900"), List.of()));

        PaddleBillingTransactionResponse result = PaddleBillingTransactionResponse.from(transaction);

        assertThat(result.getId()).isEqualTo("txn_123");
        assertThat(result.getStatus()).isEqualTo("completed");
        assertThat(result.getBilledAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(result.getCurrency()).isEqualTo("EUR");
        assertThat(result.getTotalAmount()).isEqualTo(29);
    }

    @Test
    @DisplayName("from — leaves totalAmount null when details is null")
    void from_shouldLeaveTotalAmountNull_whenDetailsIsNull() {
        PaddleTransaction transaction = new PaddleTransaction(
                "txn_123", "draft", List.of(), null, null, null);

        PaddleBillingTransactionResponse result = PaddleBillingTransactionResponse.from(transaction);

        assertThat(result.getTotalAmount()).isNull();
    }

    @Test
    @DisplayName("from — leaves totalAmount null when totals is null")
    void from_shouldLeaveTotalAmountNull_whenTotalsIsNull() {
        PaddleTransaction transaction = new PaddleTransaction(
                "txn_123", "draft", List.of(), null, null, new PaddleTransactionDetails(null, List.of()));

        PaddleBillingTransactionResponse result = PaddleBillingTransactionResponse.from(transaction);

        assertThat(result.getTotalAmount()).isNull();
    }
}
