package com.opsclear.dto;

import com.opsclear.model.OrgInviteModel;
import com.opsclear.model.OrgInviteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Public (no auth) view of an invite for the invite landing page (JOB-398). The token is the only
// secret, so this carries just what the page shows: no ids, no token.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicInviteResponse {

    private String organisationName;
    private String inviterName;
    private String email;
    private Instant expiresAt;
    private OrgInviteStatus status;

    public static PublicInviteResponse from(OrgInviteModel invite, String organisationName) {
        return PublicInviteResponse.builder()
                .organisationName(organisationName)
                .inviterName(invite.getInvitedByName())
                .email(invite.getEmail())
                .expiresAt(invite.getExpiresAt())
                .status(invite.status())
                .build();
    }
}
