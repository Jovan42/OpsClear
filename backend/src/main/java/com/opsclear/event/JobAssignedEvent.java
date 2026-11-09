package com.opsclear.event;

import java.util.UUID;

/**
 * A job was assigned or reassigned, or created already assigned (ADR-0051 {@code JOB_ASSIGNED}). {@code actorId} is
 * who made the change, {@code assigneeId} the new assignee.
 */
public record JobAssignedEvent(UUID jobId, UUID projectId, UUID actorId, UUID assigneeId) {
}
