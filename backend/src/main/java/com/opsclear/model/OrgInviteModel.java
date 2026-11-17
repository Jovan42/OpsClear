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
public class OrgInviteModel {

    private UUID id;
    private UUID organisationId;
    private String email;
    private UUID invitedBy;
    private String invitedByName;
    private String token;
    private Instant expiresAt;
    private Instant acceptedAt;
    private Instant revokedAt;
    private Instant createdAt;

    public boolean isPending() {
        return acceptedAt == null && revokedAt == null && Instant.now().isBefore(expiresAt);
    }
}
