package com.opsclear.event;

import com.opsclear.dto.ReadStateEvent;

import java.util.UUID;

/**
 * A user marked notifications read or unread (ADR-0051 §SSE stream). Pushed to that user's open streams as a
 * {@code read-state} event after the change commits, so their other tabs update.
 */
public record NotificationReadStateChangedEvent(UUID userId, ReadStateEvent state) {
}
