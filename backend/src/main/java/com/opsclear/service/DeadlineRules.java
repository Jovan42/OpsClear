package com.opsclear.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The one rule for when a deadline has passed, shared by the Dashboard overdue list and Stats "completed
 * late" so the two pages always agree (JOB-320, ADR-0050).
 *
 * <p>A deadline is a date and covers that whole UTC day: it has passed only from the next midnight UTC, i.e. at
 * or after {@code startOfUtcDay(deadline) + 1 day}. Finishing during the deadline day is on time. Deadlines are
 * normally stored as midnight UTC, but the API accepts any instant, so the deadline is truncated to its UTC day
 * first.
 */
public final class DeadlineRules {

    private static final Duration DEADLINE_DAY = Duration.ofDays(1);

    private DeadlineRules() {
    }

    /**
     * Whether the deadline day is over at the given instant.
     *
     * @param deadline the job's deadline; only its UTC date matters, not null
     * @param at the instant to check: completion time for "late", now for "overdue", not null
     * @return true if {@code at} is at or after the end of the deadline day
     */
    public static boolean isPastDeadline(Instant deadline, Instant at) {
        return !at.isBefore(deadline.truncatedTo(ChronoUnit.DAYS).plus(DEADLINE_DAY));
    }
}
