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
public class OrgMemberModel {

    private UUID organisationId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private OrganisationRole role;
    private Instant joinedAt;
}
