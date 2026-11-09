package com.opsclear.event;

import com.opsclear.config.AsyncConfig;
import com.opsclear.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into notifications (ADR-0051). Runs only after the triggering transaction commits, so a
 * rolled-back action never notifies anyone, and on the notification executor, so fan-out never slows the request.
 * Failures are logged by Spring's async exception handler and don't affect the action.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRequested(ApprovalRequestedEvent event) {
        notificationService.notifyApprovalRequested(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalDecided(ApprovalDecidedEvent event) {
        notificationService.notifyApprovalDecided(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoteAdded(NoteAddedEvent event) {
        notificationService.notifyNoteAdded(event);
    }

    // --- Job lifecycle (JOB-277) ---

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobAssigned(JobAssignedEvent event) {
        notificationService.notifyJobAssigned(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobStatusChanged(JobStatusChangedEvent event) {
        notificationService.notifyJobStatusChanged(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobBlocked(JobBlockedEvent event) {
        notificationService.notifyJobBlocked(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBlockerCleared(BlockerClearedEvent event) {
        notificationService.notifyBlockerCleared(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobUpdated(JobUpdatedEvent event) {
        notificationService.notifyJobUpdated(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobDeleted(JobDeletedEvent event) {
        notificationService.notifyJobDeleted(event);
    }

    // --- Project, organisation and billing events (JOB-279) ---

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectMemberAdded(ProjectMemberAddedEvent event) {
        notificationService.notifyProjectMemberAdded(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteAccepted(InviteAcceptedEvent event) {
        notificationService.notifyInviteAccepted(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackResolved(FeedbackResolvedEvent event) {
        notificationService.notifyFeedbackResolved(event);
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionPastDue(SubscriptionPastDueEvent event) {
        notificationService.notifySubscriptionPastDue(event);
    }
}
