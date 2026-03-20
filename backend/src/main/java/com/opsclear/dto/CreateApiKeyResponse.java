package com.opsclear.dto;

import com.opsclear.model.ApiKeyModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned once on key creation. Contains the raw key — never exposed again.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyResponse {

    private UUID id;
    private String name;
    private String key;
    private String keyPrefix;
    private Instant createdAt;
    private Instant expiresAt;

    public static CreateApiKeyResponse from(ApiKeyModel model, String rawKey) {
        return CreateApiKeyResponse.builder()
                .id(model.getId())
                .name(model.getName())
                .key(rawKey)
                .keyPrefix(model.getKeyPrefix())
                .createdAt(model.getCreatedAt())
                .expiresAt(model.getExpiresAt())
                .build();
    }
}
