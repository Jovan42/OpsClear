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
public class BlockReasonModel {

    private UUID id;
    private UUID projectId;
    private String reason;
    private Instant createdAt;
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
