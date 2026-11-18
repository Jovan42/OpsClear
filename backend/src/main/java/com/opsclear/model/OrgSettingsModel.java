package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgSettingsModel {

    private UUID orgId;
    private String projectPrefix;
    private String jobPrefix;
    private String milestonePrefix;
    private String templatePrefix;
    private Instant createdAt;
    private Instant updatedAt;

    // resolved from org_sequences, not a DB column: preview of the next friendly ID per type
    @Builder.Default
    private Map<FriendlyIdEntityType, String> nextIds = new EnumMap<>(FriendlyIdEntityType.class);

    public String getPrefix(FriendlyIdEntityType type) {
        return switch (type) {
            case PROJECT -> projectPrefix;
            case JOB -> jobPrefix;
            case MILESTONE -> milestonePrefix;
            case TEMPLATE -> templatePrefix;
        };
    }
}
