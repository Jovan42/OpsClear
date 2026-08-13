package com.opsclear.paddle;

/** Only the fields this codebase actually reads from Paddle's Price response. */
public record PaddlePrice(String id, String status) {
}
