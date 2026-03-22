package com.opsclear.dto;

import com.opsclear.model.JobRelationshipModel;
import com.opsclear.model.JobRelationshipType;
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
public class JobRelationshipResponse {

    private UUID id;
    private UUID sourceJobId;
    private UUID targetJobId;
    private JobRelationshipType type;
    private Instant createdAt;

    public static JobRelationshipResponse from(JobRelationshipModel model) {
        return JobRelationshipResponse.builder()
                .id(model.getId())
                .sourceJobId(model.getSourceJobId())
                .targetJobId(model.getTargetJobId())
                .type(model.getType())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
