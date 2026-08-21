package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalModel {

    private UUID id;
    private UUID jobId;
    private String jobTitle;
    private String jobFriendlyId;
    private UUID requesterId;
    private UUID approverId;
    private String description;
    private ApprovalStatus status;
    private String comment;
    private Instant requestedAt;
    private Instant decidedAt;

    // Populated only by ApprovalRepository#findPendingAcrossOrgs (JOB-190/ADR-0046) —
    // null on every other query, where the caller already knows which project they're in.
    private UUID projectId;
    private String projectFriendlyId;
    private String projectName;
}
