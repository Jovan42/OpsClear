package com.opsclear.service;

import java.time.Duration;
import java.time.Instant;

/**
 * The one rule for when a deadline has passed, shared by the Dashboard overdue list and Stats "completed
 * late" so the two pages always agree (JOB-320, ADR-0050).
 *
 * <p>A deadline is a date stored as midnight UTC and covers that whole day: it has passed only from the next
 * midnight UTC, i.e. at or after {@code deadline + 1 day}. Finishing during the deadline day is on time.
 */
public final class DeadlineRules {

    private static final Duration DEADLINE_DAY = Duration.ofDays(1);

    private DeadlineRules() {
    }

    /**
     * Whether the deadline day is over at the given instant.
     *
     * @param deadline the job's deadline (midnight UTC of the deadline date), not null
     * @param at the instant to check: completion time for "late", now for "overdue", not null
     * @return true if {@code at} is at or after the end of the deadline day
     */
    public static boolean isPastDeadline(Instant deadline, Instant at) {
        return !at.isBefore(deadline.plus(DEADLINE_DAY));
    }
}
