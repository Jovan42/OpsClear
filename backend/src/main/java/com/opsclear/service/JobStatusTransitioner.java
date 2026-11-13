package com.opsclear.service;

import com.opsclear.event.BlockerClearedEvent;
import com.opsclear.event.JobBlockedEvent;
import com.opsclear.event.JobStatusChangedEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobRelationshipType;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Job status transition rules, shared by {@link JobService} and (ADR-0052) {@code ApprovalService}, so neither
 * service calls the other (ADR-0017 §4). Validates the transition, updates the job, writes the
 * {@code job_status_history} row and publishes the status notification events (ADR-0051).
 * Callers resolve the project, membership and job first and run inside their own transaction.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobStatusTransitioner {

    private final JobRepository jobRepository;
    private final JobStatusHistoryRepository jobStatusHistoryRepository;
    private final JobRelationshipRepository jobRelationshipRepository;
    private final BlockReasonService blockReasonService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Moves {@code job} to {@code newStatus} on behalf of {@code changedBy}.
     *
     * @param reason block reason, required when {@code newStatus} is {@code BLOCKED}, ignored otherwise
     * @throws BadRequestException if the transition is illegal or a block reason is missing
     * @throws ForbiddenException  if {@code changedBy} may not make this transition
     */
    public JobModel transition(JobModel job, JobStatus newStatus, String reason, ProjectMemberModel changedBy) {
        JobStatus oldStatus = job.getStatus();
        validate(job, newStatus, changedBy);
        requireBlockReasonIfBlocking(newStatus, reason);

        UUID jobId = job.getId();
        UUID projectId = job.getProjectId();
        UUID changedById = changedBy.getUserId();
        String historyBlockReason = null;
        if (newStatus == JobStatus.BLOCKED) {
            BlockReasonModel blockReason = blockReasonService.findOrCreate(projectId, reason);
            job.setStatus(JobStatus.BLOCKED);
            job.setBlockedBy(changedById);
            job.setBlockedReasonId(blockReason.getId());
            job.setBlockedAt(Instant.now());
            historyBlockReason = blockReason.getReason();
        } else if (oldStatus == JobStatus.BLOCKED) {
            job.setStatus(newStatus);
            job.setBlockedBy(null);
            job.setBlockedReasonId(null);
            job.setBlockedAt(null);
        } else {
            job.setStatus(newStatus);
        }

        JobModel updated = jobRepository.save(job);
        jobStatusHistoryRepository.insert(jobId, oldStatus.name(), newStatus.name(), changedById, historyBlockReason);
        log.info("Job {} status changed from {} to {} by user {}", jobId, oldStatus, newStatus, changedById);
        publishStatusEvents(projectId, jobId, oldStatus, newStatus, historyBlockReason, changedById);
        return updated;
    }

    /**
     * Checks that moving {@code job} to {@code to} is a legal transition and that {@code changedBy} may make it,
     * without changing anything.
     *
     * @throws BadRequestException if the transition is illegal
     * @throws ForbiddenException  if {@code changedBy} may not make this transition
     */
    public void validate(JobModel job, JobStatus to, ProjectMemberModel changedBy) {
        JobStatus from = job.getStatus();
        boolean valid = (from == JobStatus.NEW && to == JobStatus.IN_PROGRESS)
                || (from == JobStatus.IN_PROGRESS && to == JobStatus.COMPLETED)
                || (from == JobStatus.IN_PROGRESS && to == JobStatus.BLOCKED)
                || (from == JobStatus.BLOCKED && to == JobStatus.IN_PROGRESS)
                || (from == JobStatus.COMPLETED && to == JobStatus.IN_PROGRESS);

        if (!valid) {
            throw new BadRequestException(ErrorMessages.Job.INVALID_TRANSITION + from + " → " + to);
        }

        boolean isOwnerOrAdmin = changedBy.getRole() == ProjectMemberRole.OWNER
                || changedBy.getRole() == ProjectMemberRole.ADMIN;

        if (from == JobStatus.COMPLETED && !isOwnerOrAdmin) {
            throw new ForbiddenException(ErrorMessages.Job.ONLY_OWNER_ADMIN_CAN_REOPEN);
        }
        if (!isOwnerOrAdmin && !changedBy.getUserId().equals(job.getAssignedTo())) {
            throw new ForbiddenException(ErrorMessages.Job.ONLY_OWNER_ADMIN_OR_ASSIGNEE_CAN_CHANGE_STATUS);
        }
    }

    // --- Notification events (ADR-0051) ---

    // Blocked sends only JOB_BLOCKED; any other status sends JOB_STATUS_CHANGED, and Completed also sends
    // BLOCKER_CLEARED when other jobs are BLOCKED_BY this one.
    private void publishStatusEvents(UUID projectId, UUID jobId, JobStatus oldStatus, JobStatus newStatus,
                                     String blockReason, UUID changedById) {
        if (newStatus == JobStatus.BLOCKED) {
            eventPublisher.publishEvent(new JobBlockedEvent(jobId, projectId, changedById, blockReason));
            return;
        }
        eventPublisher.publishEvent(new JobStatusChangedEvent(jobId, projectId, changedById, oldStatus, newStatus));
        if (newStatus == JobStatus.COMPLETED) {
            List<UUID> waitingJobIds = jobRelationshipRepository.findSourceJobIdsByTargetJobIdAndType(
                    jobId, JobRelationshipType.BLOCKED_BY);
            if (!waitingJobIds.isEmpty()) {
                eventPublisher.publishEvent(new BlockerClearedEvent(jobId, projectId, changedById, waitingJobIds));
            }
        }
    }

    // --- Guards ---

    private void requireBlockReasonIfBlocking(JobStatus newStatus, String reason) {
        if (newStatus == JobStatus.BLOCKED && (reason == null || reason.isBlank())) {
            throw new BadRequestException(ErrorMessages.Job.BLOCK_REASON_REQUIRED);
        }
    }
}
