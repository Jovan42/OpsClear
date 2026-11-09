package com.opsclear.event;

import com.opsclear.model.DeadlineAlertKind;

import java.time.Instant;
import java.util.UUID;

/**
 * The daily deadline check found a job due tomorrow or past its deadline day (ADR-0051 {@code JOB_DEADLINE_SOON} /
 * {@code JOB_OVERDUE}). {@code deadline} is the deadline the alert was recorded for. A system event: no actor.
 */
public record DeadlineAlertEvent(UUID jobId, UUID projectId, DeadlineAlertKind kind, Instant deadline) {
}
