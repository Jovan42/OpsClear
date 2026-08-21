package com.opsclear.dto;

import com.opsclear.model.ProjectDirectoryEntryModel;
import com.opsclear.model.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDirectoryEntryResponse {

    private UUID id;
    private String friendlyId;
    private String name;
    private UUID ownerId;
    private String ownerName;
    private ProjectStatus status;
    private int memberCount;

    public static ProjectDirectoryEntryResponse from(ProjectDirectoryEntryModel entry) {
        return ProjectDirectoryEntryResponse.builder()
                .id(entry.getId())
                .friendlyId(entry.getFriendlyId())
                .name(entry.getName())
                .ownerId(entry.getOwnerId())
                .ownerName(entry.getOwnerName())
                .status(entry.getStatus())
                .memberCount(entry.getMemberCount())
                .build();
    }
}
