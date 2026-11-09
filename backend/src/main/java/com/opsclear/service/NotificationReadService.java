package com.opsclear.service;

import com.opsclear.dto.ReadStateEvent;
import com.opsclear.event.NotificationReadStateChangedEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationPage;
import com.opsclear.model.NotificationType;
import com.opsclear.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read side of the notification centre (ADR-0051): list, unread count, mark read/unread, mark all read. Every method
 * acts only on the caller's own notifications, and the repository applies the read-time visibility filter (still a
 * member of the notification's organisation; for project-scoped ones, still a project member and the project not
 * deleted).
 *
 * <p>A change of read state publishes a {@link NotificationReadStateChangedEvent}, which
 * {@code NotificationEventListener} pushes to the caller's open streams once the change commits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationReadService {

    static final int DEFAULT_PAGE = 0;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

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
     * notification read keeps its original {@code read_at}. Only an actual change pushes a {@code read-state} event.
     */
    @Transactional
    public NotificationModel markRead(UUID callerId, UUID id, boolean read) {
        NotificationModel notification = requireOwnNotification(callerId, id);
        if (read != notification.isRead()) {
            notificationRepository.updateReadAt(id, read ? Instant.now() : null);
            eventPublisher.publishEvent(
                    new NotificationReadStateChangedEvent(callerId, ReadStateEvent.of(List.of(id), read)));
        }
        return requireOwnNotification(callerId, id);
    }

    /**
     * Marks all of the caller's visible unread notifications read. Returns how many changed; if any did, pushes a
     * {@code read-state} {@code {all: true}} event.
     */
    @Transactional
    public int markAllRead(UUID callerId) {
        int updated = notificationRepository.markAllRead(callerId);
        log.info("Marked {} notification(s) read for user {}", updated, callerId);
        if (updated > 0) {
            eventPublisher.publishEvent(new NotificationReadStateChangedEvent(callerId, ReadStateEvent.allRead()));
        }
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
