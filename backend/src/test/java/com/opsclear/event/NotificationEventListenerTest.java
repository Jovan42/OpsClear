package com.opsclear.event;

import com.opsclear.model.ApprovalStatus;
import com.opsclear.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener")
class NotificationEventListenerTest {

    @Mock private NotificationService notificationService;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationService);
    }

    @Test
    @DisplayName("hands APPROVAL_REQUESTED events to the fan-out")
    void onApprovalRequested_shouldFanOut() {
        ApprovalRequestedEvent event = new ApprovalRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Need a decision");

        listener.onApprovalRequested(event);

        verify(notificationService).notifyApprovalRequested(event);
    }

    @Test
    @DisplayName("hands APPROVAL_DECIDED events to the fan-out")
    void onApprovalDecided_shouldFanOut() {
        ApprovalDecidedEvent event = new ApprovalDecidedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ApprovalStatus.APPROVED, null);

        listener.onApprovalDecided(event);

        verify(notificationService).notifyApprovalDecided(event);
    }

    @Test
    @DisplayName("hands NOTE_ADDED events to the fan-out")
    void onNoteAdded_shouldFanOut() {
        NoteAddedEvent event = new NoteAddedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());

        listener.onNoteAdded(event);

        verify(notificationService).notifyNoteAdded(event);
    }
}
