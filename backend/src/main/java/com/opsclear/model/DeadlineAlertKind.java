package com.opsclear.model;

/**
 * The two daily deadline alerts of ADR-0051, as stored in {@code job_deadline_alerts.kind}, and the notification type
 * each one sends.
 */
public enum DeadlineAlertKind {
    DEADLINE_SOON(NotificationType.JOB_DEADLINE_SOON),
    OVERDUE(NotificationType.JOB_OVERDUE);

    private final NotificationType notificationType;

    DeadlineAlertKind(NotificationType notificationType) {
        this.notificationType = notificationType;
    }

    public NotificationType notificationType() {
        return notificationType;
    }
}
