package com.opsclear.dto;

import com.opsclear.model.MilestoneModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilestoneResponse {

    private UUID id;
    private String friendlyId;
    private UUID projectId;
    private String name;
    private String description;
    private LocalDate deadline;
    private Instant createdAt;

    public static MilestoneResponse from(MilestoneModel m) {
        return MilestoneResponse.builder()
                .id(m.getId())
                .friendlyId(m.getFriendlyId())
                .projectId(m.getProjectId())
                .name(m.getName())
                .description(m.getDescription())
                .deadline(m.getDeadline())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
