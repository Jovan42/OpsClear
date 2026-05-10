package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTemplateModel {

    private UUID id;
    private String friendlyId;
    private UUID projectId;
    private UUID orgId;
    private JobTemplateScope scope;
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
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }
}
