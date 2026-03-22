package com.opsclear.dto;

import com.opsclear.model.JobRelationshipEntry;
import com.opsclear.model.JobRelationshipType;
import com.opsclear.model.JobRelationshipDirection;
import com.opsclear.model.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRelationshipView {

    private UUID id;
    private JobRelationshipType type;
    private JobRelationshipDirection direction;
    private JobRef job;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JobRef {
        private UUID id;
        private String title;
        private JobStatus status;
    }

    public static JobRelationshipView from(JobRelationshipEntry entry) {
        return JobRelationshipView.builder()
                .id(entry.getId())
                .type(entry.getType())
                .direction(entry.getDirection())
                .job(JobRef.builder()
                        .id(entry.getLinkedJobId())
                        .title(entry.getLinkedJobTitle())
                        .status(entry.getLinkedJobStatus())
                        .build())
                .build();
    }
}
