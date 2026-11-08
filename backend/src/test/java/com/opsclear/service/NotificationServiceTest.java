package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationPage;
import com.opsclear.model.NotificationType;
import com.opsclear.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    private UUID callerId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
        callerId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
    }

    private NotificationModel notification(Instant readAt) {
        return NotificationModel.builder()
                .id(notificationId)
                .recipientId(callerId)
                .type(NotificationType.JOB_BLOCKED)
                .readAt(readAt)
                .createdAt(Instant.parse("2026-11-01T10:00:00Z"))
                .build();
    }

    // --- list ---

    @Test
    @DisplayName("list uses page 0 and size 20 by default, all types, read and unread")
    void list_shouldUseDefaults_whenParamsMissing() {
        List<NotificationModel> items = List.of(notification(null));
        when(notificationRepository.findVisibleByRecipient(callerId, false, null, 0L, 20)).thenReturn(items);
        when(notificationRepository.countVisibleByRecipient(callerId, false, null)).thenReturn(1L);

        NotificationPage page = notificationService.list(callerId, null, null, null, null);

        assertThat(page.getItems()).isEqualTo(items);
        assertThat(page.getPage()).isZero();
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("list passes the unread and type filters and computes the offset from page and size")
    void list_shouldPassFiltersAndOffset() {
        when(notificationRepository.findVisibleByRecipient(callerId, true, NotificationType.NOTE_ADDED, 10L, 5))
                .thenReturn(List.of());
        when(notificationRepository.countVisibleByRecipient(callerId, true, NotificationType.NOTE_ADDED))
                .thenReturn(12L);

        NotificationPage page = notificationService.list(callerId, true, NotificationType.NOTE_ADDED, 2, 5);

        assertThat(page.getItems()).isEmpty();
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(5);
        assertThat(page.getTotalElements()).isEqualTo(12L);
    }

    @Test
    @DisplayName("list treats unread=false as no unread filter")
    void list_shouldNotFilter_whenUnreadFalse() {
        when(notificationRepository.findVisibleByRecipient(callerId, false, null, 0L, 20)).thenReturn(List.of());
        when(notificationRepository.countVisibleByRecipient(callerId, false, null)).thenReturn(0L);

        notificationService.list(callerId, false, null, null, null);

        verify(notificationRepository).findVisibleByRecipient(callerId, false, null, 0L, 20);
    }

    @Test
    @DisplayName("list computes a large offset without int overflow")
    void list_shouldComputeLongOffset() {
        when(notificationRepository.findVisibleByRecipient(callerId, false, null, 214_748_364_700L, 100))
                .thenReturn(List.of());
        when(notificationRepository.countVisibleByRecipient(callerId, false, null)).thenReturn(0L);

        notificationService.list(callerId, null, null, Integer.MAX_VALUE, 100);

        verify(notificationRepository).findVisibleByRecipient(callerId, false, null, 214_748_364_700L, 100);
    }

    @Test
    @DisplayName("list rejects a negative page with 400")
    void list_shouldThrow_whenPageNegative() {
        assertThatThrownBy(() -> notificationService.list(callerId, null, null, -1, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.Notification.INVALID_PAGE);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("list rejects size 0 with 400")
    void list_shouldThrow_whenSizeZero() {
        assertThatThrownBy(() -> notificationService.list(callerId, null, null, null, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(String.format(ErrorMessages.Notification.INVALID_SIZE, 100));
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("list rejects size above 100 with 400")
    void list_shouldThrow_whenSizeTooLarge() {
        assertThatThrownBy(() -> notificationService.list(callerId, null, null, null, 101))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(notificationRepository);
    }

    // --- unreadCount ---

    @Test
    @DisplayName("unreadCount counts the caller's visible unread notifications of any type")
    void unreadCount_shouldCountVisibleUnread() {
        when(notificationRepository.countVisibleByRecipient(callerId, true, null)).thenReturn(7L);

        assertThat(notificationService.unreadCount(callerId)).isEqualTo(7L);
    }

    // --- markRead ---

    @Test
    @DisplayName("markRead true sets read_at on an unread notification and returns it re-fetched")
    void markRead_shouldSetReadAt_whenUnread() {
        NotificationModel after = notification(Instant.now());
        when(notificationRepository.findVisibleByIdAndRecipient(notificationId, callerId))
                .thenReturn(Optional.of(notification(null)), Optional.of(after));

        NotificationModel result = notificationService.markRead(callerId, notificationId, true);

        ArgumentCaptor<Instant> readAt = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).updateReadAt(eq(notificationId), readAt.capture());
        assertThat(readAt.getValue()).isNotNull();
        assertThat(result).isSameAs(after);
    }

    @Test
    @DisplayName("markRead false clears read_at on a read notification")
    void markRead_shouldClearReadAt_whenRead() {
        when(notificationRepository.findVisibleByIdAndRecipient(notificationId, callerId))
                .thenReturn(Optional.of(notification(Instant.now())), Optional.of(notification(null)));

        NotificationModel result = notificationService.markRead(callerId, notificationId, false);

        verify(notificationRepository).updateReadAt(eq(notificationId), isNull());
        assertThat(result.isRead()).isFalse();
    }

    @Test
    @DisplayName("markRead true on an already read notification keeps its original read_at")
    void markRead_shouldNotUpdate_whenAlreadyRead() {
        NotificationModel read = notification(Instant.parse("2026-11-02T08:00:00Z"));
        when(notificationRepository.findVisibleByIdAndRecipient(notificationId, callerId))
                .thenReturn(Optional.of(read));

        NotificationModel result = notificationService.markRead(callerId, notificationId, true);

        verify(notificationRepository, never()).updateReadAt(any(), any());
        assertThat(result.getReadAt()).isEqualTo(Instant.parse("2026-11-02T08:00:00Z"));
    }

    @Test
    @DisplayName("markRead false on an unread notification changes nothing")
    void markRead_shouldNotUpdate_whenAlreadyUnread() {
        when(notificationRepository.findVisibleByIdAndRecipient(notificationId, callerId))
                .thenReturn(Optional.of(notification(null)));

        notificationService.markRead(callerId, notificationId, false);

        verify(notificationRepository, never()).updateReadAt(any(), any());
    }

    @Test
    @DisplayName("markRead throws 404 when the notification isn't the caller's, doesn't exist or is hidden")
    void markRead_shouldThrowNotFound_whenNotOwnNotification() {
        when(notificationRepository.findVisibleByIdAndRecipient(notificationId, callerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(callerId, notificationId, true))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.Notification.NOT_FOUND);
        verify(notificationRepository, never()).updateReadAt(any(), any());
    }

    // --- markAllRead ---

    @Test
    @DisplayName("markAllRead marks only the caller's unread notifications and returns the count")
    void markAllRead_shouldDelegateForCaller() {
        when(notificationRepository.markAllRead(callerId)).thenReturn(3);

        assertThat(notificationService.markAllRead(callerId)).isEqualTo(3);
        verify(notificationRepository).markAllRead(callerId);
        verify(notificationRepository, never()).findVisibleByRecipient(any(), eq(false), any(), anyLong(), anyInt());
    }
}
