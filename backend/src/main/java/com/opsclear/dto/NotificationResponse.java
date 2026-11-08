package com.opsclear.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification shape of ADR-0051 §API. {@code actor} is null for system-triggered notifications; {@code payload} is
 * the stored JSON snapshot, written through as-is.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private UUID id;
    private NotificationType type;
    private boolean read;
    private Instant createdAt;
    private Actor actor;
    private String projectFriendlyId;
    private String jobFriendlyId;
    private UUID approvalId;
    @JsonRawValue
    private String payload;

    public static NotificationResponse from(NotificationModel model) {
        return NotificationResponse.builder()
                .id(model.getId())
                .type(model.getType())
                .read(model.isRead())
                .createdAt(model.getCreatedAt())
                .actor(model.getActorId() != null ? new Actor(model.getActorId(), model.getActorName()) : null)
                .projectFriendlyId(model.getProjectFriendlyId())
                .jobFriendlyId(model.getJobFriendlyId())
                .approvalId(model.getApprovalId())
                .payload(model.getPayload())
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Actor {
        private UUID id;
        private String name;
    }
}
