package com.opsclear.event;

import com.opsclear.model.ProjectMemberRole;

import java.util.UUID;

/**
 * A user was added to a project (ADR-0051 {@code PROJECT_MEMBER_ADDED}). {@code actorId} is the Owner/Admin who added
 * them, {@code userId} the added user.
 */
public record ProjectMemberAddedEvent(UUID projectId, UUID userId, ProjectMemberRole role, UUID actorId) {
}
