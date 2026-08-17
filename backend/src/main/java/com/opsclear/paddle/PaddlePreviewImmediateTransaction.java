package com.opsclear.paddle;

/**
 * A preview's {@code immediate_transaction} — present only when the previewed
 * {@code proration_billing_mode} would actually charge something right away
 * ({@code prorated_immediately}/{@code full_immediately}); {@code null} for modes
 * like {@code full_next_billing_period} that defer billing, confirmed against a
 * real sandbox preview call.
 */
public record PaddlePreviewImmediateTransaction(PaddlePreviewTransactionDetails details) {
}
