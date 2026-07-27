package com.opsclear.dto;

import com.opsclear.model.JobTypeColor;
import com.opsclear.model.JobTypeModel;
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
public class JobTypeResponse {

    private UUID id;
    private UUID projectId;
    private String name;
    private JobTypeColor color;
    private int displayOrder;
    private Instant createdAt;

    public static JobTypeResponse from(JobTypeModel m) {
        return JobTypeResponse.builder()
                .id(m.getId())
                .projectId(m.getProjectId())
                .name(m.getName())
                .color(m.getColor())
                .displayOrder(m.getDisplayOrder())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
