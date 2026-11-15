package com.opsclear.model;

/**
 * Optional job-detail sections a project can hide from its job screen (ADR-0053).
 * Stored in {@code projects.hidden_job_sections}. A display preference only: no endpoint enforces it.
 */
public enum JobSection {
    NOTES,
    APPROVALS,
    RELATIONSHIPS,
    LINKS,
    STATUS_HISTORY
}
