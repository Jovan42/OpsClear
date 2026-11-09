package com.opsclear.event;

import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.FeedbackStatus;
import com.opsclear.model.ProjectMemberRole;
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

    @Test
    @DisplayName("hands PROJECT_MEMBER_ADDED events to the fan-out")
    void onProjectMemberAdded_shouldFanOut() {
        ProjectMemberAddedEvent event = new ProjectMemberAddedEvent(UUID.randomUUID(), UUID.randomUUID(),
                ProjectMemberRole.MEMBER, UUID.randomUUID());

        listener.onProjectMemberAdded(event);

        verify(notificationService).notifyProjectMemberAdded(event);
    }

    @Test
    @DisplayName("hands INVITE_ACCEPTED events to the fan-out")
    void onInviteAccepted_shouldFanOut() {
        InviteAcceptedEvent event = new InviteAcceptedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());

        listener.onInviteAccepted(event);

        verify(notificationService).notifyInviteAccepted(event);
    }

    @Test
    @DisplayName("hands FEEDBACK_RESOLVED events to the fan-out")
    void onFeedbackResolved_shouldFanOut() {
        FeedbackResolvedEvent event = new FeedbackResolvedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Idea", FeedbackStatus.CREDITED, 500);

        listener.onFeedbackResolved(event);

        verify(notificationService).notifyFeedbackResolved(event);
    }

    @Test
    @DisplayName("hands SUBSCRIPTION_PAST_DUE events to the fan-out")
    void onSubscriptionPastDue_shouldFanOut() {
        SubscriptionPastDueEvent event = new SubscriptionPastDueEvent(UUID.randomUUID());

        listener.onSubscriptionPastDue(event);

        verify(notificationService).notifySubscriptionPastDue(event);
    }
}
