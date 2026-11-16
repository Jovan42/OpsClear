package com.opsclear.security;

import com.opsclear.config.RateLimitProperties;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.service.AddonAccessChecker;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-API-key rate limit (ADR-0058): one in-memory Bucket4j token bucket per API key ID, with greedy refill.
 * <ul>
 *   <li>The entry for a key is created on its first request and kept until the backend restarts.</li>
 *   <li>Keys whose user belongs to an internal org (ADR-0030) get an unlimited entry. The org lookup runs once per
 *       key per backend run, so a change to {@code is_internal} takes effect on restart. A user with no org is
 *       limited.</li>
 *   <li>With {@code opsclear.rate-limit.enabled=false} every request is allowed.</li>
 * </ul>
 */
@Component
@Slf4j
public class ApiKeyRateLimiter {

    private static final long WARN_INTERVAL_NANOS = Duration.ofMinutes(1).toNanos();
    private static final long NEVER_WARNED = -1L;

    private final RateLimitProperties properties;
    private final OrganisationRepository organisationRepository;
    private final AddonAccessChecker addonAccessChecker;
    private final TimeMeter timeMeter;
    private final Map<UUID, KeyLimit> limits = new ConcurrentHashMap<>();

    @Autowired
    public ApiKeyRateLimiter(RateLimitProperties properties,
                             OrganisationRepository organisationRepository,
                             AddonAccessChecker addonAccessChecker) {
        this(properties, organisationRepository, addonAccessChecker, TimeMeter.SYSTEM_MILLISECONDS);
    }

    ApiKeyRateLimiter(RateLimitProperties properties,
                      OrganisationRepository organisationRepository,
                      AddonAccessChecker addonAccessChecker,
                      TimeMeter timeMeter) {
        this.properties = properties;
        this.organisationRepository = organisationRepository;
        this.addonAccessChecker = addonAccessChecker;
        this.timeMeter = timeMeter;
    }

    /**
     * Takes one token from the key's bucket.
     *
     * @param keyId  the API key ID (the bucket key)
     * @param userId the key's user, used once per key to check for an internal org
     * @return allowed, or rejected with the wait until the next token
     */
    public Decision tryConsume(UUID keyId, UUID userId) {
        if (!properties.enabled()) {
            return Decision.ALLOWED;
        }

        // The org lookup runs inside computeIfAbsent, so it happens exactly once per key, on its first request.
        KeyLimit limit = limits.computeIfAbsent(keyId, id -> newKeyLimit(userId));

        if (limit.isUnlimited()) {
            return Decision.ALLOWED;
        }

        ConsumptionProbe probe = limit.bucket().tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return Decision.ALLOWED;
        }
        return Decision.rejected(toRetryAfterSeconds(probe.getNanosToWaitForRefill()), limit.claimWarning());
    }

    private KeyLimit newKeyLimit(UUID userId) {
        if (isInternalUser(userId)) {
            return new KeyLimit(null, null);
        }
        RateLimitProperties.ApiKey config = properties.apiKey();
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(config.capacity())
                .refillGreedy(config.refillPerMinute(), Duration.ofMinutes(1))
                .build();
        Bucket bucket = Bucket.builder()
                .addLimit(bandwidth)
                .withCustomTimePrecision(timeMeter)
                .build();
        return new KeyLimit(bucket, new AtomicLong(NEVER_WARNED));
    }

    private boolean isInternalUser(UUID userId) {
        return organisationRepository.findByMember(userId)
                .map(OrganisationModel::getId)
                .map(addonAccessChecker::isInternal)
                .orElse(false);
    }

    private static long toRetryAfterSeconds(long nanosToWait) {
        long seconds = (nanosToWait + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1);
        return Math.max(1, seconds);
    }

    /**
     * The limiter's answer for one request.
     *
     * @param allowed           whether the request may proceed
     * @param retryAfterSeconds whole seconds until the next token (at least 1); {@code 0} when allowed
     * @param shouldWarn        {@code true} for the first rejection of a key in each minute, so the caller logs a
     *                          runaway key without flooding the logs
     */
    public record Decision(boolean allowed, long retryAfterSeconds, boolean shouldWarn) {

        static final Decision ALLOWED = new Decision(true, 0, false);

        static Decision rejected(long retryAfterSeconds, boolean shouldWarn) {
            return new Decision(false, retryAfterSeconds, shouldWarn);
        }
    }

    /** A key's bucket, or the unlimited marker when {@code bucket} is {@code null}. */
    private final class KeyLimit {

        private final Bucket bucket;
        private final AtomicLong lastWarnNanos;

        private KeyLimit(Bucket bucket, AtomicLong lastWarnNanos) {
            this.bucket = bucket;
            this.lastWarnNanos = lastWarnNanos;
        }

        private boolean isUnlimited() {
            return bucket == null;
        }

        private Bucket bucket() {
            return bucket;
        }

        /** {@code true} at most once per minute for this key; only one of several racing callers wins. */
        private boolean claimWarning() {
            long now = timeMeter.currentTimeNanos();
            long last = lastWarnNanos.get();
            boolean due = last == NEVER_WARNED || now - last >= WARN_INTERVAL_NANOS;
            return due && lastWarnNanos.compareAndSet(last, now);
        }
    }
}
