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
public class ProjectModel {

    private UUID id;
    private String friendlyId;
    private String name;
    private String description;
    private UUID ownerId;
    private String ownerName;
    private UUID organisationId;
    @Builder.Default
    private ProjectStatus status = ProjectStatus.ACTIVE;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    // Populated by ProjectService on both list and getById — not stored in DB
    @Builder.Default
    private List<ProjectLinkModel> links = new ArrayList<>();

    public boolean isCompleted() {
        return status == ProjectStatus.COMPLETED;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
