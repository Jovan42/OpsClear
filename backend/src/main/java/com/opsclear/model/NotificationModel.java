package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One notification for one recipient (ADR-0051). No soft delete: rows are hard-deleted by retention.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationModel {

    private UUID id;
    private UUID orgId;
    private UUID recipientId;
    private NotificationType type;
    private UUID actorId;
    private String actorName; // resolved via JOIN, not a DB column
    private UUID projectId;
    private String projectFriendlyId; // resolved via JOIN, not a DB column
    private UUID jobId;
    private String jobFriendlyId; // resolved via JOIN, not a DB column
    private UUID approvalId;
    // Display snapshot as a raw JSON object string; null is stored as {}.
    private String payload;
    private Instant readAt;
    private Instant createdAt;

    public boolean isRead() {
        return readAt != null;
    }
}
