package com.opsclear.service;

import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.repository.ApprovalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Cancels pending completion approvals (ADR-0052): withdrawal through {@link ApprovalService#cancel}, and the automatic
 * cancellation when a job is completed directly or deleted ({@link JobService}). A separate component so
 * {@code JobService} doesn't depend on {@code ApprovalService} for this, and neither service calls the other.
 *
 * <p>When the canceller isn't the requester, the requester is told with {@code APPROVAL_DECIDED}, decision
 * {@code CANCELLED} (ADR-0051). Withdrawing your own request notifies nobody. Callers lock the job row first and run
 * inside their own transaction, so cancelling never races a decision on the same job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompletionApprovalCanceller {

    static final String COMPLETED_DIRECTLY_COMMENT = "Completed directly by ";
    static final String JOB_DELETED_COMMENT = "Job deleted by ";

    private final ApprovalRepository approvalRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Withdraws {@code approval}, a completion approval, on behalf of {@code cancelledBy}.
     *
     * @throws ConflictException if it's no longer pending
     */
    public ApprovalModel withdraw(UUID projectId, ApprovalModel approval, UUID cancelledBy) {
        int rows = approvalRepository.cancelPendingCompletion(approval.getId(), cancelledBy, null, Instant.now());
        requireCancelled(rows);
        ApprovalModel cancelled = approvalRepository.findByIdAndJobId(approval.getId(), approval.getJobId())
                .orElseThrow();
        log.info("Completion approval {} on job {} withdrawn by user {}",
                cancelled.getId(), cancelled.getJobId(), cancelledBy);
        publishIfNotRequester(projectId, cancelled, cancelledBy);
        return cancelled;
    }

    /** Cancels the job's pending completion approval, if any, because {@code completedBy} completed it directly. */
    public void cancelOnDirectCompletion(JobModel job, ProjectMemberModel completedBy) {
        cancelPending(job, completedBy, COMPLETED_DIRECTLY_COMMENT + completedBy.getUserName());
    }

    /** Cancels the job's pending completion approval, if any, because {@code deletedBy} deleted the job. */
    public void cancelOnDeletion(JobModel job, ProjectMemberModel deletedBy) {
        cancelPending(job, deletedBy, JOB_DELETED_COMMENT + deletedBy.getUserName());
    }

    private void cancelPending(JobModel job, ProjectMemberModel cancelledBy, String comment) {
        UUID cancelledById = cancelledBy.getUserId();
        approvalRepository.cancelPendingCompletionByJobId(job.getId(), cancelledById, comment, Instant.now())
                .ifPresent(cancelled -> {
                    log.info("Completion approval {} on job {} cancelled automatically by user {}: {}",
                            cancelled.getId(), job.getId(), cancelledById, comment);
                    publishIfNotRequester(job.getProjectId(), cancelled, cancelledById);
                });
    }

    private void publishIfNotRequester(UUID projectId, ApprovalModel cancelled, UUID cancelledBy) {
        if (cancelledBy.equals(cancelled.getRequesterId())) {
            return;
        }
        eventPublisher.publishEvent(new ApprovalDecidedEvent(cancelled.getId(), projectId, cancelled.getJobId(),
                cancelledBy, cancelled.getRequesterId(), ApprovalStatus.CANCELLED, cancelled.getComment(),
                cancelled.getKind(), null, null));
    }

    // --- Guards ---

    private void requireCancelled(int rows) {
        if (rows == 0) {
            throw new ConflictException(ErrorMessages.Approval.ALREADY_DECIDED);
        }
    }
}
