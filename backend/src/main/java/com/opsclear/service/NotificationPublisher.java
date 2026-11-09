package com.opsclear.service;

import com.opsclear.dto.NotificationResponse;
import com.opsclear.dto.ReadStateEvent;
import com.opsclear.model.NotificationModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;

/**
 * Turns notification changes into SSE events on the recipients' open streams (ADR-0051 §SSE stream). Pushes are
 * best-effort and live only: a client that isn't connected re-fetches when it reconnects. Callers push only committed
 * changes ({@code NotificationEventListener} calls this after the fan-out or read-state transaction commits).
 */
@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    public static final String NOTIFICATION_EVENT = "notification";
    public static final String READ_STATE_EVENT = "read-state";

    private final NotificationStreamRegistry streamRegistry;

    /**
     * Pushes a {@code notification} event (same shape as {@code GET /api/notifications} items) for each new
     * notification to its recipient's open streams, and to no one else's.
     */
    public void publishCreated(Collection<NotificationModel> notifications) {
        for (NotificationModel notification : notifications) {
            streamRegistry.sendToUser(notification.getRecipientId(), NOTIFICATION_EVENT,
                    NotificationResponse.from(notification));
        }
    }

    /** Pushes a {@code read-state} event to the user's open streams, so their other tabs update. */
    public void publishReadState(UUID userId, ReadStateEvent state) {
        streamRegistry.sendToUser(userId, READ_STATE_EVENT, state);
    }
}
