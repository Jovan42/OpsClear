package com.opsclear.model;

/**
 * The 16 notification types of ADR-0051. Names match the {@code chk_notifications_type} check constraint.
 */
public enum NotificationType {
    APPROVAL_REQUESTED,
    APPROVAL_DECIDED,
    JOB_ASSIGNED,
    JOB_STATUS_CHANGED,
    JOB_BLOCKED,
    BLOCKER_CLEARED,
    JOB_UPDATED,
    JOB_DELETED,
    NOTE_ADDED,
    JOB_DEADLINE_SOON,
    JOB_OVERDUE,
    PROJECT_MEMBER_ADDED,
    SCHEDULE_RUN_MISSED,
    INVITE_ACCEPTED,
    FEEDBACK_RESOLVED,
    SUBSCRIPTION_PAST_DUE;

    /** Whether a user may mute this type (ADR-0051 §Muting: billing problems affect the whole organisation). */
    public boolean isMutable() {
        return this != SUBSCRIPTION_PAST_DUE;
    }
}
