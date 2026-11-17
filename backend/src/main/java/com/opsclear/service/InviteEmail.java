package com.opsclear.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything an invite email needs (ADR-0059). {@code inviteId} is only there for logging, because the address and
 * the token must never be logged.
 *
 * @param inviteId    the invite, for logs
 * @param to          the invitee's email address
 * @param language    {@code en} or {@code sr}; anything else falls back to {@code en}
 * @param orgName     the organisation the invite is for
 * @param inviterName the person who sent the invite
 * @param token       the invite token, used in the accept link
 * @param expiresAt   when the invite expires
 */
public record InviteEmail(
        UUID inviteId,
        String to,
        String language,
        String orgName,
        String inviterName,
        String token,
        Instant expiresAt) {
}
