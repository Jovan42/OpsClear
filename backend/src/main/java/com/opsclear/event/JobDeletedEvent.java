package com.opsclear.event;

import java.util.UUID;

/**
 * A job was soft-deleted (ADR-0051 {@code JOB_DELETED}). {@code actorId} is who deleted it. The fan-out reads the
 * deleted job for the payload snapshot, so the notification still renders after deletion.
 */
public record JobDeletedEvent(UUID jobId, UUID projectId, UUID actorId) {
}
