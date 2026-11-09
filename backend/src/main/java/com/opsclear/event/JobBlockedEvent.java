package com.opsclear.event;

import java.util.UUID;

/** A job was moved to Blocked (ADR-0051 {@code JOB_BLOCKED}). {@code actorId} is who blocked it. */
public record JobBlockedEvent(UUID jobId, UUID projectId, UUID actorId, String blockReason) {
}
