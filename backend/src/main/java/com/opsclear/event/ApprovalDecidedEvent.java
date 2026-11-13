package com.opsclear.event;

import com.opsclear.model.ApprovalKind;
import com.opsclear.model.ApprovalStatus;

import java.util.UUID;

/**
 * An approval was approved or rejected (ADR-0051 {@code APPROVAL_DECIDED}). {@code actorId} is the decider,
 * {@code comment} may be null. For an approved completion approval (ADR-0052), {@code completionApplied} says whether
 * the job was completed and {@code completionNotAppliedReason} is the job status that stopped it; both are null
 * otherwise.
 */
public record ApprovalDecidedEvent(UUID approvalId, UUID projectId, UUID jobId, UUID actorId, UUID requesterId,
                                   ApprovalStatus decision, String comment, ApprovalKind kind,
                                   Boolean completionApplied, String completionNotAppliedReason) {
}
