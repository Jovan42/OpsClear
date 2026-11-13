package com.opsclear.event;

import com.opsclear.model.ApprovalKind;

import java.util.UUID;

/**
 * An approval was requested on a job (ADR-0051 {@code APPROVAL_REQUESTED}). {@code actorId} is the requester,
 * {@code kind} tells manual requests from completion requests (ADR-0052).
 */
public record ApprovalRequestedEvent(UUID approvalId, UUID projectId, UUID jobId, UUID actorId, String description,
                                     ApprovalKind kind) {
}
