package com.opsclear.model;

/** Why an approval exists (ADR-0052): a manual request, or a Member's request to complete a job on a gated project. */
public enum ApprovalKind {
    MANUAL,
    COMPLETION
}
