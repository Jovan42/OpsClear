package com.opsclear.event;

import com.opsclear.config.AsyncConfig;
import com.opsclear.service.NotificationPublisher;
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
 *
 * <p>Live push (JOB-280): each {@code notifyXxx} call commits its own transaction and returns the saved notifications,
 * which are then pushed to the recipients' open SSE streams. If the fan-out throws or rolls back, nothing is pushed.
 * Read-state changes are pushed the same way, after the mark-read transaction commits.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final NotificationPublisher notificationPublisher;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalRequested(ApprovalRequestedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyApprovalRequested(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApprovalDecided(ApprovalDecidedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyApprovalDecided(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoteAdded(NoteAddedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyNoteAdded(event));
    }

    // --- Job lifecycle (JOB-277) ---

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobAssigned(JobAssignedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyJobAssigned(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobStatusChanged(JobStatusChangedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyJobStatusChanged(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobBlocked(JobBlockedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyJobBlocked(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBlockerCleared(BlockerClearedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyBlockerCleared(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobUpdated(JobUpdatedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyJobUpdated(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobDeleted(JobDeletedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyJobDeleted(event));
    }

    // --- Project, organisation and billing events (JOB-279) ---

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProjectMemberAdded(ProjectMemberAddedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyProjectMemberAdded(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInviteAccepted(InviteAcceptedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyInviteAccepted(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFeedbackResolved(FeedbackResolvedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyFeedbackResolved(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscriptionPastDue(SubscriptionPastDueEvent event) {
        notificationPublisher.publishCreated(notificationService.notifySubscriptionPastDue(event));
    }

    // --- Scheduled and system events (JOB-278) ---

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeadlineAlert(DeadlineAlertEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyDeadlineAlert(event));
    }

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduleRunMissed(ScheduleRunMissedEvent event) {
        notificationPublisher.publishCreated(notificationService.notifyScheduleRunMissed(event));
    }

    // --- Live push only (JOB-280) ---

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadStateChanged(NotificationReadStateChangedEvent event) {
        notificationPublisher.publishReadState(event.userId(), event.state());
    }
}
