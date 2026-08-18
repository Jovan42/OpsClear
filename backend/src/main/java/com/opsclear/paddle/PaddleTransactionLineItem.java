package com.opsclear.paddle;

/**
 * One entry in a Transaction's {@code details.line_items} array. Distinct from
 * {@link PaddleTransactionItem} (the top-level {@code items} array, which Paddle
 * echoes back from the request and carries no {@code id}) — this is the resource
 * Paddle actually assigns an id to ({@code txnitm_...}), and it's the id Adjustments'
 * {@code item_id} expects.
 */
public record PaddleTransactionLineItem(String id) {
}
