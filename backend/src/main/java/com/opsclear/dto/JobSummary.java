package com.opsclear.dto;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSummary {

    private UUID id;
    private String title;
    private String client;
    private UUID assignedTo;
    private String assignedToName;
    private Instant deadline;
    private JobStatus status;
    private String blockedReason;
    private Instant blockedAt;
    private UUID blockedBy;

    public static JobSummary from(JobModel model) {
        return JobSummary.builder()
                .id(model.getId())
                .title(model.getTitle())
                .client(model.getClient())
                .assignedTo(model.getAssignedTo())
                .assignedToName(model.getAssignedToName())
                .deadline(model.getDeadline())
                .status(model.getStatus())
                .blockedReason(model.getBlockedReason())
                .blockedAt(model.getBlockedAt())
                .blockedBy(model.getBlockedBy())
                .build();
    }
}
