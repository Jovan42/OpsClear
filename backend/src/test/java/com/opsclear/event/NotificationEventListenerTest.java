package com.opsclear.event;

import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    @Test
    @DisplayName("hands job lifecycle events (JOB-277) to their fan-out methods")
    void onJobLifecycleEvents_shouldFanOut() {
        UUID jobId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        JobAssignedEvent assigned = new JobAssignedEvent(jobId, projectId, actorId, UUID.randomUUID());
        JobStatusChangedEvent statusChanged =
                new JobStatusChangedEvent(jobId, projectId, actorId, JobStatus.NEW, JobStatus.IN_PROGRESS);
        JobBlockedEvent blocked = new JobBlockedEvent(jobId, projectId, actorId, "Waiting for client");
        BlockerClearedEvent cleared = new BlockerClearedEvent(jobId, projectId, actorId, List.of(UUID.randomUUID()));
        JobUpdatedEvent updated = new JobUpdatedEvent(jobId, projectId, actorId, List.of(JobUpdatedEvent.PRIORITY),
                null, JobPriority.HIGH);
        JobDeletedEvent deleted = new JobDeletedEvent(jobId, projectId, actorId);

        listener.onJobAssigned(assigned);
        listener.onJobStatusChanged(statusChanged);
        listener.onJobBlocked(blocked);
        listener.onBlockerCleared(cleared);
        listener.onJobUpdated(updated);
        listener.onJobDeleted(deleted);

        verify(notificationService).notifyJobAssigned(assigned);
        verify(notificationService).notifyJobStatusChanged(statusChanged);
        verify(notificationService).notifyJobBlocked(blocked);
        verify(notificationService).notifyBlockerCleared(cleared);
        verify(notificationService).notifyJobUpdated(updated);
        verify(notificationService).notifyJobDeleted(deleted);
    }
}
