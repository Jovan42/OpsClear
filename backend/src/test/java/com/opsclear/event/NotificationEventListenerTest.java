package com.opsclear.event;

import com.opsclear.model.ApprovalKind;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.DeadlineAlertKind;
import com.opsclear.model.FeedbackStatus;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.dto.ReadStateEvent;
import com.opsclear.model.NotificationModel;
import com.opsclear.service.NotificationPublisher;
import com.opsclear.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventListener")
class NotificationEventListenerTest {

    @Mock private NotificationService notificationService;
    @Mock private NotificationPublisher notificationPublisher;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationService, notificationPublisher);
    }

    @Test
    @DisplayName("hands APPROVAL_REQUESTED events to the fan-out")
    void onApprovalRequested_shouldFanOut() {
        ApprovalRequestedEvent event = new ApprovalRequestedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Need a decision", ApprovalKind.MANUAL);

        listener.onApprovalRequested(event);

        verify(notificationService).notifyApprovalRequested(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands APPROVAL_DECIDED events to the fan-out")
    void onApprovalDecided_shouldFanOut() {
        ApprovalDecidedEvent event = new ApprovalDecidedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ApprovalStatus.APPROVED, null, ApprovalKind.MANUAL, null, null);

        listener.onApprovalDecided(event);

        verify(notificationService).notifyApprovalDecided(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands NOTE_ADDED events to the fan-out")
    void onNoteAdded_shouldFanOut() {
        NoteAddedEvent event = new NoteAddedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());

        listener.onNoteAdded(event);

        verify(notificationService).notifyNoteAdded(event);
        verify(notificationPublisher).publishCreated(List.of());
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
        verify(notificationPublisher, times(6)).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands PROJECT_MEMBER_ADDED events to the fan-out")
    void onProjectMemberAdded_shouldFanOut() {
        ProjectMemberAddedEvent event = new ProjectMemberAddedEvent(UUID.randomUUID(), UUID.randomUUID(),
                ProjectMemberRole.MEMBER, UUID.randomUUID());

        listener.onProjectMemberAdded(event);

        verify(notificationService).notifyProjectMemberAdded(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands INVITE_ACCEPTED events to the fan-out")
    void onInviteAccepted_shouldFanOut() {
        InviteAcceptedEvent event = new InviteAcceptedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        listener.onInviteAccepted(event);

        verify(notificationService).notifyInviteAccepted(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands FEEDBACK_RESOLVED events to the fan-out")
    void onFeedbackResolved_shouldFanOut() {
        FeedbackResolvedEvent event = new FeedbackResolvedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Idea", FeedbackStatus.CREDITED, 500);

        listener.onFeedbackResolved(event);

        verify(notificationService).notifyFeedbackResolved(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands SUBSCRIPTION_PAST_DUE events to the fan-out")
    void onSubscriptionPastDue_shouldFanOut() {
        SubscriptionPastDueEvent event = new SubscriptionPastDueEvent(UUID.randomUUID());

        listener.onSubscriptionPastDue(event);

        verify(notificationService).notifySubscriptionPastDue(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands deadline alert events to the fan-out")
    void onDeadlineAlert_shouldFanOut() {
        DeadlineAlertEvent event = new DeadlineAlertEvent(UUID.randomUUID(), UUID.randomUUID(),
                DeadlineAlertKind.OVERDUE, Instant.parse("2026-11-08T00:00:00Z"));

        listener.onDeadlineAlert(event);

        verify(notificationService).notifyDeadlineAlert(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("hands SCHEDULE_RUN_MISSED events to the fan-out")
    void onScheduleRunMissed_shouldFanOut() {
        ScheduleRunMissedEvent event = new ScheduleRunMissedEvent(UUID.randomUUID(), UUID.randomUUID(),
                "Weekly report", Instant.parse("2026-11-09T09:00:00Z"));

        listener.onScheduleRunMissed(event);

        verify(notificationService).notifyScheduleRunMissed(event);
        verify(notificationPublisher).publishCreated(List.of());
    }

    @Test
    @DisplayName("pushes the notifications a fan-out saved to the recipients' streams")
    void onEvent_shouldPushSavedNotifications() {
        NoteAddedEvent event = new NoteAddedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());
        List<NotificationModel> saved = List.of(NotificationModel.builder().id(UUID.randomUUID()).build());
        when(notificationService.notifyNoteAdded(event)).thenReturn(saved);

        listener.onNoteAdded(event);

        verify(notificationPublisher).publishCreated(saved);
    }

    @Test
    @DisplayName("pushes read-state changes to the user's streams")
    void onReadStateChanged_shouldPushReadState() {
        UUID userId = UUID.randomUUID();
        ReadStateEvent state = ReadStateEvent.allRead();

        listener.onReadStateChanged(new NotificationReadStateChangedEvent(userId, state));

        verify(notificationPublisher).publishReadState(userId, state);
    }
}
