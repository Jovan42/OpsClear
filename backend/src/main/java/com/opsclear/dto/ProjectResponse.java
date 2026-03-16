package com.opsclear.dto;

import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID ownerId;
    private String ownerName;
    private ProjectStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProjectResponse from(ProjectModel project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .ownerId(project.getOwnerId())
                .ownerName(project.getOwnerName())
                .status(project.getStatus())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
