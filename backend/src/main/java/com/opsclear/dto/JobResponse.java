package com.opsclear.dto;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
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
public class JobResponse {

    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private String client;
    private UUID assignedTo;
    private String assignedToName;
    private Instant deadline;
    private JobStatus status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    // Blocking metadata — null when not blocked
    private UUID blockedBy;
    private UUID blockedReasonId;
    private String blockedReason;
    private Instant blockedAt;

    public static JobResponse from(JobModel job) {
        return JobResponse.builder()
                .id(job.getId())
                .projectId(job.getProjectId())
                .title(job.getTitle())
                .description(job.getDescription())
                .client(job.getClient())
                .assignedTo(job.getAssignedTo())
                .assignedToName(job.getAssignedToName())
                .deadline(job.getDeadline())
                .status(job.getStatus())
                .createdBy(job.getCreatedBy())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .blockedBy(job.getBlockedBy())
                .blockedReasonId(job.getBlockedReasonId())
                .blockedReason(job.getBlockedReason())
                .blockedAt(job.getBlockedAt())
                .build();
    }
}
