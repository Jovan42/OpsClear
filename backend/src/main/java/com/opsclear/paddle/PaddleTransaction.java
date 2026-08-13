package com.opsclear.paddle;

import java.util.List;

/** Only the fields this codebase actually reads from Paddle's Transaction response. */
public record PaddleTransaction(String id, String status, List<PaddleTransactionItem> items) {
}
