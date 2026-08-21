package com.opsclear.model;

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
public class ProjectDirectoryEntryModel {

    private UUID id;
    private String friendlyId;
    private String name;
    private UUID ownerId;
    private String ownerName;
    private ProjectStatus status;
    private int memberCount;
}
