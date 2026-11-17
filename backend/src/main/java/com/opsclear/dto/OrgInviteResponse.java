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
    // Owners/admins build the "Copy link" URL from it (ADR-0059); the invitee already has it.
    private String token;
    private Instant expiresAt;
    private Instant createdAt;

    public static OrgInviteResponse from(OrgInviteModel invite) {
        return OrgInviteResponse.builder()
                .id(invite.getId())
                .organisationId(invite.getOrganisationId())
                .email(invite.getEmail())
                .invitedBy(invite.getInvitedBy())
                .invitedByName(invite.getInvitedByName())
                .token(invite.getToken())
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
