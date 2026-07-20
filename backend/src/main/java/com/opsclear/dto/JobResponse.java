package com.opsclear.dto;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResponse {

    private UUID id;
    private String friendlyId;
    private UUID projectId;
    private String title;
    private String description;
    private String client;
    private UUID assignedTo;
    private String assignedToName;
    private Instant deadline;
    private JobStatus status;
    private JobPriority priority;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    private UUID milestoneId;
    private String milestoneName;

    // Blocking metadata — null when not blocked
    private UUID blockedBy;
    private String blockedReason;
    private Instant blockedAt;

    // Populated only on getById — empty list on list responses
    private List<JobRelationshipView> relationships;

    private UUID sourceScheduleId;

    public static JobResponse from(JobModel job) {
        return JobResponse.builder()
                .id(job.getId())
                .friendlyId(job.getFriendlyId())
                .projectId(job.getProjectId())
                .title(job.getTitle())
                .description(job.getDescription())
                .client(job.getClient())
                .assignedTo(job.getAssignedTo())
                .assignedToName(job.getAssignedToName())
                .deadline(job.getDeadline())
                .status(job.getStatus())
                .priority(job.getPriority())
                .createdBy(job.getCreatedBy())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .milestoneId(job.getMilestoneId())
                .milestoneName(job.getMilestoneName())
                .blockedBy(job.getBlockedBy())
                .blockedReason(job.getBlockedReason())
                .blockedAt(job.getBlockedAt())
                .relationships(job.getRelationships().stream()
                        .map(JobRelationshipView::from)
                        .toList())
                .sourceScheduleId(job.getSourceScheduleId())
                .build();
    }
}
