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
    private Instant lastSentAt;

    public boolean isPending() {
        return status() == OrgInviteStatus.PENDING;
    }

    // Accept and revoke both need a pending invite, so at most one of the two is ever set
    public OrgInviteStatus status() {
        if (acceptedAt != null) {
            return OrgInviteStatus.ACCEPTED;
        }
        if (revokedAt != null) {
            return OrgInviteStatus.REVOKED;
        }
        if (!Instant.now().isBefore(expiresAt)) {
            return OrgInviteStatus.EXPIRED;
        }
        return OrgInviteStatus.PENDING;
    }
}
