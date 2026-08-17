package com.opsclear.dto;

import com.opsclear.paddle.PaddleTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaddleBillingTransactionResponse {

    private String id;
    private String status;
    private Instant billedAt;
    private String currency;
    private Integer totalAmount;

    public static PaddleBillingTransactionResponse from(PaddleTransaction transaction) {
        String rawTotal = transaction.details() != null && transaction.details().totals() != null
                ? transaction.details().totals().total()
                : null;

        return PaddleBillingTransactionResponse.builder()
                .id(transaction.id())
                .status(transaction.status())
                .billedAt(transaction.billedAt())
                .currency(transaction.currencyCode())
                .totalAmount(rawTotal != null ? Integer.parseInt(rawTotal) / 100 : null)
                .build();
    }
}
