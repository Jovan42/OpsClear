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
public class ApiKeyModel {

    private UUID id;
    private UUID userId;
    private String name;
    private String keyHash;
    private String keyPrefix;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }
}
