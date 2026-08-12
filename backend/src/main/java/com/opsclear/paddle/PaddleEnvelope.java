package com.opsclear.paddle;

/**
 * Every Paddle API response wraps its payload in {@code {"data": ..., "meta": {...}}}.
 * {@code meta} is intentionally not modeled — Spring Boot's default Jackson config
 * ignores unknown properties, and nothing here reads it.
 */
public record PaddleEnvelope<T>(T data) {
}
