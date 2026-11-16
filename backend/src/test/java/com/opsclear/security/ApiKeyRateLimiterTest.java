package com.opsclear.security;

import com.opsclear.config.RateLimitProperties;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.service.AddonAccessChecker;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyRateLimiter")
class ApiKeyRateLimiterTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private AddonAccessChecker addonAccessChecker;

    private FakeTimeMeter clock;
    private UUID keyId;
    private UUID userId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        clock = new FakeTimeMeter();
        keyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }

    private ApiKeyRateLimiter limiter(boolean enabled, long capacity, long refillPerMinute) {
        RateLimitProperties properties = new RateLimitProperties(
                enabled, new RateLimitProperties.ApiKey(capacity, refillPerMinute));
        return new ApiKeyRateLimiter(properties, organisationRepository, addonAccessChecker, clock);
    }

    private void userInRegularOrg(UUID user) {
        when(organisationRepository.findByMember(user))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(addonAccessChecker.isInternal(orgId)).thenReturn(false);
    }

    @Test
    @DisplayName("Should allow requests up to the bucket capacity and reject the next one")
    void tryConsume_shouldAllowUpToCapacity_thenReject() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 3, 120);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        }
        ApiKeyRateLimiter.Decision rejected = limiter.tryConsume(keyId, userId);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("The Spring constructor should use the system clock and limit as configured")
    void publicConstructor_shouldLimitWithSystemClock() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = new ApiKeyRateLimiter(
                new RateLimitProperties(true, new RateLimitProperties.ApiKey(1, 1)),
                organisationRepository, addonAccessChecker);

        assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        ApiKeyRateLimiter.Decision rejected = limiter.tryConsume(keyId, userId);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    @DisplayName("Allowed decisions carry no wait and no warning")
    void tryConsume_allowedDecision_shouldHaveNoWaitAndNoWarning() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter.Decision decision = limiter(true, 1, 1).tryConsume(keyId, userId);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.retryAfterSeconds()).isZero();
        assertThat(decision.shouldWarn()).isFalse();
    }

    @Test
    @DisplayName("Should refill tokens over time (greedy: 120/min gives one token every 500 ms)")
    void tryConsume_shouldRefillOverTime() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 1, 120);

        assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        assertThat(limiter.tryConsume(keyId, userId).allowed()).isFalse();

        clock.advance(Duration.ofMillis(499));
        assertThat(limiter.tryConsume(keyId, userId).allowed()).isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        assertThat(limiter.tryConsume(keyId, userId).allowed()).isFalse();
    }

    @Test
    @DisplayName("Retry-After should be the wait rounded up to whole seconds")
    void tryConsume_shouldRoundWaitUpToWholeSeconds() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 1, 1);

        limiter.tryConsume(keyId, userId);
        assertThat(limiter.tryConsume(keyId, userId).retryAfterSeconds()).isEqualTo(60);

        clock.advance(Duration.ofMillis(500));
        assertThat(limiter.tryConsume(keyId, userId).retryAfterSeconds()).isEqualTo(60);

        clock.advance(Duration.ofMillis(500));
        assertThat(limiter.tryConsume(keyId, userId).retryAfterSeconds()).isEqualTo(59);
    }

    @Test
    @DisplayName("Retry-After should be at least 1 second even when the wait is shorter")
    void tryConsume_shouldReturnAtLeastOneSecond() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 1, 120);

        limiter.tryConsume(keyId, userId);
        clock.advance(Duration.ofMillis(499));

        assertThat(limiter.tryConsume(keyId, userId).retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    @DisplayName("Each API key should have its own bucket")
    void tryConsume_shouldKeepSeparateBucketsPerKey() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 1, 1);
        UUID otherKeyId = UUID.randomUUID();

        assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        assertThat(limiter.tryConsume(keyId, userId).allowed()).isFalse();

        assertThat(limiter.tryConsume(otherKeyId, userId).allowed()).isTrue();
    }

    @Test
    @DisplayName("Keys of an internal org should never be limited, with the org looked up once per key")
    void tryConsume_shouldNeverLimit_internalOrgKey() {
        when(organisationRepository.findByMember(userId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(addonAccessChecker.isInternal(orgId)).thenReturn(true);
        ApiKeyRateLimiter limiter = limiter(true, 1, 1);

        for (int i = 0; i < 50; i++) {
            ApiKeyRateLimiter.Decision decision = limiter.tryConsume(keyId, userId);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.retryAfterSeconds()).isZero();
        }

        verify(organisationRepository, times(1)).findByMember(userId);
        verify(addonAccessChecker, times(1)).isInternal(orgId);
    }

    @Test
    @DisplayName("The org lookup for a limited key should run once, not on every request")
    void tryConsume_shouldCacheOrgLookup_forLimitedKey() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 10, 120);

        for (int i = 0; i < 5; i++) {
            limiter.tryConsume(keyId, userId);
        }

        verify(organisationRepository, times(1)).findByMember(userId);
        verify(addonAccessChecker, times(1)).isInternal(orgId);
    }

    @Test
    @DisplayName("A key whose user has no org should be limited")
    void tryConsume_shouldLimit_whenUserHasNoOrg() {
        when(organisationRepository.findByMember(userId)).thenReturn(Optional.empty());
        ApiKeyRateLimiter limiter = limiter(true, 1, 1);

        assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        assertThat(limiter.tryConsume(keyId, userId).allowed()).isFalse();
        verify(addonAccessChecker, never()).isInternal(any());
    }

    @Test
    @DisplayName("When disabled, every request should be allowed and no org lookup should run")
    void tryConsume_shouldAlwaysAllow_whenDisabled() {
        ApiKeyRateLimiter limiter = limiter(false, 1, 1);

        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        }

        verifyNoInteractions(organisationRepository, addonAccessChecker);
    }

    @Test
    @DisplayName("Should flag a warning on the first rejection of a key, then at most once per minute")
    void tryConsume_shouldFlagWarningAtMostOncePerMinute() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 1, 1);

        limiter.tryConsume(keyId, userId);
        assertThat(limiter.tryConsume(keyId, userId).shouldWarn()).isTrue();
        assertThat(limiter.tryConsume(keyId, userId).shouldWarn()).isFalse();

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.tryConsume(keyId, userId).shouldWarn()).isFalse();

        // At one minute a token is back; the request after it is rejected again and warns again.
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryConsume(keyId, userId).allowed()).isTrue();
        ApiKeyRateLimiter.Decision rejected = limiter.tryConsume(keyId, userId);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.shouldWarn()).isTrue();
    }

    @Test
    @DisplayName("Warnings should be tracked per key")
    void tryConsume_shouldTrackWarningsPerKey() {
        userInRegularOrg(userId);
        ApiKeyRateLimiter limiter = limiter(true, 1, 1);
        UUID otherKeyId = UUID.randomUUID();

        limiter.tryConsume(keyId, userId);
        limiter.tryConsume(otherKeyId, userId);

        assertThat(limiter.tryConsume(keyId, userId).shouldWarn()).isTrue();
        assertThat(limiter.tryConsume(otherKeyId, userId).shouldWarn()).isTrue();
    }

    private static final class FakeTimeMeter implements TimeMeter {

        private long nanos = Duration.ofDays(1).toNanos();

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
