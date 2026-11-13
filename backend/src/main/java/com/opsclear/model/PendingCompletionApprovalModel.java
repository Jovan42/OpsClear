package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A job's pending completion approval (ADR-0052), shown on job detail and in job lists. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingCompletionApprovalModel {

    private UUID id;
    private UUID jobId;
    private UUID requesterId;
    private String requesterName; // resolved via JOIN, not a DB column
    private Instant requestedAt;
}
