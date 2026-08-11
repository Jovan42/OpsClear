package com.opsclear.paddle;

/** Only the fields this codebase actually reads from Paddle's Customer response. */
public record PaddleCustomer(String id, String email) {
}
