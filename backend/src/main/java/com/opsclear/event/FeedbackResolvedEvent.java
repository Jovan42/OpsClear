package com.opsclear.event;

import com.opsclear.model.FeedbackStatus;

import java.util.UUID;

/**
 * A super admin credited or declined a feedback submission (ADR-0051 {@code FEEDBACK_RESOLVED}). A system event: it
 * has no actor from the organisation, so the UI shows "OpsClear". {@code creditAmount} is null when declined.
 */
public record FeedbackResolvedEvent(UUID submissionId, UUID orgId, UUID submitterId, String title,
                                    FeedbackStatus outcome, Integer creditAmount) {
}
