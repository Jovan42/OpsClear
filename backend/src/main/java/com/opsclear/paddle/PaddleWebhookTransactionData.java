package com.opsclear.paddle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The {@code data} object on a {@code transaction.*} webhook event — a different
 * shape from {@link PaddleWebhookSubscriptionData} (no {@code customer_id}/
 * {@code status} in this shape), only read for {@code transaction.completed} to
 * detach a one-time credit discount once it's actually been consumed (JOB-180).
 *
 * <p>{@code details} reuses {@link PaddlePreviewTransactionDetails} — a real webhook
 * delivery's {@code details.totals} object has the same shape as a preview's, and
 * {@code totals.discount} is what lets {@link com.opsclear.service.CreditService}
 * tell how much of the discount this specific transaction actually redeemed, versus
 * the full amount it was created for (confirmed via real sandbox data that Paddle
 * caps and fully consumes a flat discount at the transaction total, with no
 * rollover of the difference — see CreditService#consumeCredit).
 */
public record PaddleWebhookTransactionData(
        String id,
        @JsonProperty("subscription_id") String subscriptionId,
        @JsonProperty("discount_id") String discountId,
        PaddlePreviewTransactionDetails details) {
}
