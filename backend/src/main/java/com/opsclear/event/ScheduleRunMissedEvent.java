package com.opsclear.event;

import java.time.Instant;
import java.util.UUID;

/**
 * A recurring schedule recorded a missed run (ADR-0051 {@code SCHEDULE_RUN_MISSED}). {@code missedAt} is when the run
 * should have happened. A system event: no actor.
 */
public record ScheduleRunMissedEvent(UUID scheduleId, UUID projectId, String scheduleName, Instant missedAt) {
}
