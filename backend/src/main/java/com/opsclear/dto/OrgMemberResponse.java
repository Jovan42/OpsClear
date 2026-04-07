package com.opsclear.dto;

import com.opsclear.model.OrgMemberModel;
import com.opsclear.model.OrganisationRole;
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
public class OrgMemberResponse {

    private UUID organisationId;
    private UUID userId;
    private String userName;
    private String userEmail;
    private OrganisationRole role;
    private Instant joinedAt;

    public static OrgMemberResponse from(OrgMemberModel member) {
        return OrgMemberResponse.builder()
                .organisationId(member.getOrganisationId())
                .userId(member.getUserId())
                .userName(member.getUserName())
                .userEmail(member.getUserEmail())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
