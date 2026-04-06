package com.opsclear.model;

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
public class OrganisationModel {

    private UUID id;
    private String name;
    private String slug;
    private UUID createdBy;
    private String createdByName;
    private Instant createdAt;
    private Instant deletedAt;

    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
