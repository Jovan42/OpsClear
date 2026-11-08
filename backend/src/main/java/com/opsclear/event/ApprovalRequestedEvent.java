package com.opsclear.event;

import java.util.UUID;

/** An approval was requested on a job (ADR-0051 {@code APPROVAL_REQUESTED}). {@code actorId} is the requester. */
public record ApprovalRequestedEvent(UUID approvalId, UUID projectId, UUID jobId, UUID actorId, String description) {
}
