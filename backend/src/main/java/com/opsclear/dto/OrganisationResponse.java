package com.opsclear.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opsclear.model.OrgAccessModel;
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
    // ADR-0067: set on the caller's own org responses; left out of lists (super-admin org picker).
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private OrgAccessResponse access;

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

    public static OrganisationResponse from(OrganisationModel org, OrgAccessModel access) {
        OrganisationResponse response = from(org);
        response.setAccess(OrgAccessResponse.from(access));
        return response;
    }
}
