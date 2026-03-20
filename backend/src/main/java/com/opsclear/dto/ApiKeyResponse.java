package com.opsclear.dto;

import com.opsclear.model.ApiKeyModel;
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
public class ApiKeyResponse {

    private UUID id;
    private String name;
    private String keyPrefix;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant revokedAt;

    public static ApiKeyResponse from(ApiKeyModel model) {
        return ApiKeyResponse.builder()
                .id(model.getId())
                .name(model.getName())
                .keyPrefix(model.getKeyPrefix())
                .createdAt(model.getCreatedAt())
                .lastUsedAt(model.getLastUsedAt())
                .expiresAt(model.getExpiresAt())
                .revokedAt(model.getRevokedAt())
                .build();
    }
}
