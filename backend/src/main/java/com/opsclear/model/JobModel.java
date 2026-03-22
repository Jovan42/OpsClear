package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobModel {

    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private String client;
    private UUID assignedTo;
    private String assignedToName;
    private Instant deadline;
    private JobStatus status;
    @Builder.Default
    private JobPriority priority = JobPriority.MEDIUM;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    private UUID milestoneId;
    private String milestoneName; // resolved via JOIN, not a DB column

    // Populated by JobService.getById — not stored in DB, empty on list responses
    @Builder.Default
    private List<JobRelationshipEntry> relationships = new ArrayList<>();

    // Blocking metadata — all null when not blocked
    private UUID blockedBy;
    private UUID blockedReasonId;
    private String blockedReason; // resolved via JOIN, not a DB column
    private Instant blockedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
