package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationPage;
import com.opsclear.model.NotificationType;
import com.opsclear.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification centre (ADR-0051), read side: list, unread count, mark read/unread, mark all read. Every method acts
 * only on the caller's own notifications, and the repository applies the read-time visibility filter (project-scoped
 * notifications only while the caller is still a project member). Fan-out (creating notifications) is added by
 * Backend 2.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final NotificationRepository notificationRepository;

    /**
     * The caller's notifications, newest first. {@code unread} true returns only unread ones; {@code type} null
     * means any type. {@code page} is zero-based (default 0), {@code size} 1 to 100 (default 20).
     */
    @Transactional(readOnly = true)
    public NotificationPage list(UUID callerId, Boolean unread, NotificationType type, Integer page, Integer size) {
        int p = page != null ? page : DEFAULT_PAGE;
        int s = size != null ? size : DEFAULT_SIZE;
        requireValidPage(p);
        requireValidSize(s);
        boolean unreadOnly = Boolean.TRUE.equals(unread);
        return NotificationPage.builder()
                .items(notificationRepository.findVisibleByRecipient(callerId, unreadOnly, type, (long) p * s, s))
                .page(p)
                .size(s)
                .totalElements(notificationRepository.countVisibleByRecipient(callerId, unreadOnly, type))
                .build();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID callerId) {
        return notificationRepository.countVisibleByRecipient(callerId, true, null);
    }

    /**
     * Marks one of the caller's notifications read or unread. Another user's ID, an unknown ID or a notification
     * hidden by the visibility filter is a 404 (not 403), so existence isn't revealed. Marking an already read
     * notification read keeps its original {@code read_at}.
     */
    @Transactional
    public NotificationModel markRead(UUID callerId, UUID id, boolean read) {
        NotificationModel notification = requireOwnNotification(callerId, id);
        if (read != notification.isRead()) {
            notificationRepository.updateReadAt(id, read ? Instant.now() : null);
            // Hook point for the SSE stream job: push a read-state event to the caller's other open streams here.
        }
        return requireOwnNotification(callerId, id);
    }

    /** Marks all of the caller's unread notifications read. Returns how many changed. */
    @Transactional
    public int markAllRead(UUID callerId) {
        int updated = notificationRepository.markAllRead(callerId);
        log.info("Marked {} notification(s) read for user {}", updated, callerId);
        // Hook point for the SSE stream job: push a read-state {all: true} event to the caller's open streams here.
        return updated;
    }

    // --- Guards ---

    private NotificationModel requireOwnNotification(UUID callerId, UUID id) {
        return notificationRepository.findVisibleByIdAndRecipient(id, callerId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Notification.NOT_FOUND));
    }

    private static void requireValidPage(int page) {
        if (page < 0) {
            throw new BadRequestException(ErrorMessages.Notification.INVALID_PAGE);
        }
    }

    private static void requireValidSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BadRequestException(String.format(ErrorMessages.Notification.INVALID_SIZE, MAX_SIZE));
        }
    }
}
