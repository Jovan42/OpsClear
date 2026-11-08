package com.opsclear.event;

import com.opsclear.model.ApprovalStatus;

import java.util.UUID;

/**
 * An approval was approved or rejected (ADR-0051 {@code APPROVAL_DECIDED}). {@code actorId} is the decider,
 * {@code comment} may be null.
 */
public record ApprovalDecidedEvent(UUID approvalId, UUID projectId, UUID jobId, UUID actorId, UUID requesterId,
                                   ApprovalStatus decision, String comment) {
}
