package com.opsclear.dto;

import com.opsclear.model.OrganisationModel;
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
public class OrganisationResponse {

    private UUID id;
    private String name;
    private String slug;
    private UUID createdBy;
    private String createdByName;
    private Instant createdAt;

    public static OrganisationResponse from(OrganisationModel org) {
        return OrganisationResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .createdBy(org.getCreatedBy())
                .createdByName(org.getCreatedByName())
                .createdAt(org.getCreatedAt())
                .build();
    }
}
