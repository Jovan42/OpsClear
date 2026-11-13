package com.opsclear.model;

/** {@code CANCELLED} is set only on withdrawn or auto-cancelled completion approvals in V1 (ADR-0052). */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
