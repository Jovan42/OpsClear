package com.opsclear.event;

import java.util.List;
import java.util.UUID;

/**
 * A job that other jobs are {@code BLOCKED_BY} was completed (ADR-0051 {@code BLOCKER_CLEARED}). {@code actorId} is
 * who completed it, {@code waitingJobIds} the jobs blocked by it at that moment. Each waiting job's status and
 * assignee are read at fan-out time.
 */
public record BlockerClearedEvent(UUID blockerJobId, UUID projectId, UUID actorId, List<UUID> waitingJobIds) {
}
