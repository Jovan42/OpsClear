package com.opsclear.event;

import java.util.UUID;

/**
 * Someone accepted an organisation invite (ADR-0051 {@code INVITE_ACCEPTED}). {@code actorId} is the new member,
 * {@code inviterId} the invite's {@code invited_by} (may be null).
 */
public record InviteAcceptedEvent(UUID inviteId, UUID orgId, UUID actorId, UUID inviterId) {
}
