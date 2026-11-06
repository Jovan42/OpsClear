package com.opsclear.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeadlineRules")
class DeadlineRulesTest {

    private static final Instant DEADLINE = Instant.parse("2026-11-04T00:00:00Z");

    @Test
    @DisplayName("isPastDeadline — false before the deadline day")
    void isPastDeadline_shouldBeFalse_beforeDeadlineDay() {
        assertThat(DeadlineRules.isPastDeadline(DEADLINE, Instant.parse("2026-11-03T23:59:59Z"))).isFalse();
    }

    @Test
    @DisplayName("isPastDeadline — false during the whole deadline day")
    void isPastDeadline_shouldBeFalse_duringDeadlineDay() {
        assertThat(DeadlineRules.isPastDeadline(DEADLINE, DEADLINE)).isFalse();
        assertThat(DeadlineRules.isPastDeadline(DEADLINE, Instant.parse("2026-11-04T15:29:00Z"))).isFalse();
        assertThat(DeadlineRules.isPastDeadline(DEADLINE, Instant.parse("2026-11-04T23:59:59.999Z"))).isFalse();
    }

    @Test
    @DisplayName("isPastDeadline — true from the next midnight UTC onwards")
    void isPastDeadline_shouldBeTrue_fromNextMidnightUtc() {
        assertThat(DeadlineRules.isPastDeadline(DEADLINE, Instant.parse("2026-11-05T00:00:00Z"))).isTrue();
        assertThat(DeadlineRules.isPastDeadline(DEADLINE, Instant.parse("2026-11-06T12:00:00Z"))).isTrue();
    }

    @Test
    @DisplayName("isPastDeadline — a non-midnight deadline counts by its UTC date: past at the next midnight UTC")
    void isPastDeadline_shouldUseUtcDate_whenDeadlineIsNotMidnight() {
        Instant eveningDeadline = Instant.parse("2026-11-04T18:00:00Z");

        assertThat(DeadlineRules.isPastDeadline(eveningDeadline, Instant.parse("2026-11-04T23:59:59Z"))).isFalse();
        assertThat(DeadlineRules.isPastDeadline(eveningDeadline, Instant.parse("2026-11-05T00:00:00Z"))).isTrue();
        assertThat(DeadlineRules.isPastDeadline(eveningDeadline, Instant.parse("2026-11-05T17:59:59Z"))).isTrue();
    }
}
