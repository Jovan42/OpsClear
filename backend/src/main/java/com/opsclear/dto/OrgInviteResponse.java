package com.opsclear.dto;

import com.opsclear.model.OrgInviteModel;
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
public class OrgInviteResponse {

    private UUID id;
    private UUID organisationId;
    private String email;
    private UUID invitedBy;
    private String invitedByName;
    private Instant expiresAt;
    private Instant createdAt;

    public static OrgInviteResponse from(OrgInviteModel invite) {
        return OrgInviteResponse.builder()
                .id(invite.getId())
                .organisationId(invite.getOrganisationId())
                .email(invite.getEmail())
                .invitedBy(invite.getInvitedBy())
                .invitedByName(invite.getInvitedByName())
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
