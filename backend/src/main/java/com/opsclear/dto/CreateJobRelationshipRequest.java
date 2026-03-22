package com.opsclear.dto;

import com.opsclear.model.JobRelationshipType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateJobRelationshipRequest {

    @NotNull(message = "Target job ID is required")
    private UUID targetJobId;

    @NotNull(message = "Relationship type is required")
    private JobRelationshipType type;
}
