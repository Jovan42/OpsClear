package com.opsclear.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * In-app rate limits (ADR-0058). Set in {@code application.properties}, each overridable by env
 * ({@code OPSCLEAR_RATE_LIMIT_ENABLED}, {@code OPSCLEAR_RATE_LIMIT_API_KEY_CAPACITY},
 * {@code OPSCLEAR_RATE_LIMIT_API_KEY_REFILL_PER_MINUTE}).
 *
 * @param enabled {@code false} turns every in-app limit off
 * @param apiKey  the per-API-key token bucket
 */
@Validated
@ConfigurationProperties("opsclear.rate-limit")
public record RateLimitProperties(boolean enabled, @Valid @NotNull ApiKey apiKey) {

    /**
     * One token bucket per API key, with greedy refill.
     *
     * @param capacity        the burst size: requests allowed back to back on a full bucket
     * @param refillPerMinute tokens added back per minute, spread evenly over the minute
     */
    public record ApiKey(@Positive long capacity, @Positive long refillPerMinute) {
    }
}
