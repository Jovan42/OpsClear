package com.opsclear.dto;

import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
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
public class ApprovalResponse {

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
    private UUID projectId;
    private String projectFriendlyId;
    private String projectName;

    public static ApprovalResponse from(ApprovalModel model) {
        return ApprovalResponse.builder()
                .id(model.getId())
                .jobId(model.getJobId())
                .jobTitle(model.getJobTitle())
                .jobFriendlyId(model.getJobFriendlyId())
                .requesterId(model.getRequesterId())
                .approverId(model.getApproverId())
                .description(model.getDescription())
                .status(model.getStatus())
                .comment(model.getComment())
                .requestedAt(model.getRequestedAt())
                .decidedAt(model.getDecidedAt())
                .projectId(model.getProjectId())
                .projectFriendlyId(model.getProjectFriendlyId())
                .projectName(model.getProjectName())
                .build();
    }
}
