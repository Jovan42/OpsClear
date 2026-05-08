package com.opsclear.dto;

import com.opsclear.model.JobTemplateModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTemplateResponse {

    private UUID id;
    private String friendlyId;
    private UUID projectId;
    private String name;
    private String title;
    private String description;
    private String client;
    private String priority;
    private String assigneeMode;
    private UUID assigneeId;
    private String assigneeName;
    private UUID milestoneId;
    private String milestoneName;
    private Integer deadlineOffsetDays;
    private int occurrenceCount;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobTemplateResponse from(JobTemplateModel m) {
        return JobTemplateResponse.builder()
                .id(m.getId())
                .friendlyId(m.getFriendlyId())
                .projectId(m.getProjectId())
                .name(m.getName())
                .title(m.getTitle())
                .description(m.getDescription())
                .client(m.getClient())
                .priority(m.getPriority())
                .assigneeMode(m.getAssigneeMode())
                .assigneeId(m.getAssigneeId())
                .assigneeName(m.getAssigneeName())
                .milestoneId(m.getMilestoneId())
                .milestoneName(m.getMilestoneName())
                .deadlineOffsetDays(m.getDeadlineOffsetDays())
                .occurrenceCount(m.getOccurrenceCount())
                .createdBy(m.getCreatedBy())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
