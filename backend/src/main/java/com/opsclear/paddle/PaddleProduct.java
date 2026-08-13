package com.opsclear.paddle;

/** Only the fields this codebase actually reads from Paddle's Product response. */
public record PaddleProduct(String id, String name) {
}
