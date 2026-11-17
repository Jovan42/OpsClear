package com.opsclear.event;

import java.util.UUID;

/**
 * An invite email should go out (ADR-0059). Published when an invite is created; {@link InviteEmailListener} sends it
 * after the transaction commits. {@code language} is the inviter's UI language, {@code en} or {@code sr}.
 */
public record InviteEmailRequestedEvent(UUID inviteId, String language) {
}
