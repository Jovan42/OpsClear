package com.opsclear.service;

import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.event.ApprovalRequestedEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ApprovalKind;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalService {

    static final String DEFAULT_COMPLETION_DESCRIPTION = "Requesting to complete this job";

    private final ApprovalRepository approvalRepository;
    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JobStatusTransitioner jobStatusTransitioner;

    @Transactional
    public ApprovalModel request(UUID projectId, UUID jobId, String description, UUID callerId) {
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        JobModel job = requireJobInProject(jobId, projectId);
        ProjectMemberModel caller = requireMember(projectId, callerId);
        requireCanRequestApproval(caller, job, callerId);

        ApprovalModel approval = approvalRepository.insert(jobId, callerId, description.strip());
        log.info("Approval {} requested on job {} in project {} by user {}",
                approval.getId(), jobId, projectId, callerId);
        eventPublisher.publishEvent(new ApprovalRequestedEvent(
                approval.getId(), projectId, jobId, callerId, approval.getDescription(), ApprovalKind.MANUAL));
        return approval;
    }

    /**
     * Creates a completion approval for a Member completing {@code job} on a gated project (ADR-0052), instead of
     * completing it. Called by {@link JobService#updateStatus}, which has resolved the project, membership and job and
     * validated the transition. The description is {@code message}, or a default when it's blank.
     *
     * @throws ConflictException if the job already has a pending completion approval (the unique partial index
     *                           {@code uq_approvals_one_pending_completion} also catches concurrent requests)
     */
    @Transactional
    public ApprovalModel createCompletionApproval(ProjectModel project, JobModel job, ProjectMemberModel caller,
                                                  String message) {
        requireProjectNotCompleted(project);
        UUID callerId = caller.getUserId();
        requireCanRequestApproval(caller, job, callerId);

        String description = message != null && !message.isBlank() ? message.strip() : DEFAULT_COMPLETION_DESCRIPTION;
        ApprovalModel approval;
        try {
            approval = approvalRepository.insert(job.getId(), callerId, description, ApprovalKind.COMPLETION);
        } catch (DuplicateKeyException e) {
            throw new ConflictException(ErrorMessages.Approval.COMPLETION_ALREADY_PENDING);
        }
        log.info("Completion approval {} requested on job {} in project {} by user {}",
                approval.getId(), job.getId(), project.getId(), callerId);
        eventPublisher.publishEvent(new ApprovalRequestedEvent(approval.getId(), project.getId(), job.getId(),
                callerId, approval.getDescription(), ApprovalKind.COMPLETION));
        return approval;
    }

    @Transactional
    public ApprovalModel decide(UUID projectId, UUID jobId, UUID approvalId,
                                ApprovalStatus status, String comment, UUID callerId) {
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        ProjectMemberModel decider = requireOwnerOrAdmin(projectId, callerId);
        // Locks the job row before touching the approval (ADR-0052), so a decision racing a status change sees its
        // result; every path locks the job first, then the approval
        JobModel job = requireJobInProjectForUpdate(jobId, projectId);
        ApprovalModel approval = requireApprovalExists(approvalId, jobId);
        requireStatusIsDecision(status);

        int rows = approvalRepository.updateDecision(approvalId, callerId, status, comment, Instant.now());
        requireDecisionApplied(rows);
        if (approval.getKind() == ApprovalKind.COMPLETION && status == ApprovalStatus.APPROVED) {
            applyCompletion(approvalId, job, decider);
        }

        log.info("Approval {} on job {} {} by user {}", approvalId, jobId, status, callerId);
        ApprovalModel decided = approvalRepository.findByIdAndJobId(approvalId, jobId).orElseThrow();
        eventPublisher.publishEvent(new ApprovalDecidedEvent(approvalId, projectId, jobId, callerId,
                decided.getRequesterId(), status, decided.getComment(), decided.getKind(),
                decided.getCompletionApplied(), decided.getCompletionNotAppliedReason()));
        return decided;
    }

    // ADR-0052: an approved completion approval completes the job, in the decision's transaction, if the job (read
    // under a row lock by decide) is still In Progress. Otherwise the approval stays approved but "not applied", with
    // the job's current status as the reason.
    private void applyCompletion(UUID approvalId, JobModel job, ProjectMemberModel decider) {
        if (job.getStatus() == JobStatus.IN_PROGRESS) {
            jobStatusTransitioner.transition(job, JobStatus.COMPLETED, null, decider);
            approvalRepository.updateCompletionOutcome(approvalId, true, null);
            log.info("Completion approval {} completed job {}", approvalId, job.getId());
        } else {
            approvalRepository.updateCompletionOutcome(approvalId, false, job.getStatus().name());
            log.info("Completion approval {} approved but not applied: job {} is {}",
                    approvalId, job.getId(), job.getStatus());
        }
    }

    @Transactional(readOnly = true)
    public List<ApprovalModel> listByJob(UUID projectId, UUID jobId, UUID callerId) {
        requireProjectExistsById(projectId);
        requireJobInProject(jobId, projectId);
        requireMember(projectId, callerId);
        return approvalRepository.findByJobId(jobId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalModel> listPendingByProject(UUID projectId, UUID callerId) {
        requireProjectExistsById(projectId);
        requireOwnerOrAdmin(projectId, callerId);
        return approvalRepository.findPendingByProjectId(projectId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalModel> findPendingAcrossOrgs(UUID callerId) {
        return approvalRepository.findPendingAcrossOrgs(callerId);
    }

    // --- Guards ---

    private ProjectModel requireProjectExists(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireProjectExistsById(UUID projectId) {
        requireProjectExists(projectId);
    }

    private void requireProjectNotCompleted(ProjectModel project) {
        if (project.isCompleted()) {
            throw new ConflictException(ErrorMessages.Project.COMPLETED_NO_MUTATIONS);
        }
    }

    private JobModel requireJobInProject(UUID jobId, UUID projectId) {
        return requireInProject(jobRepository.findByIdAndDeletedAtIsNull(jobId), projectId);
    }

    private JobModel requireJobInProjectForUpdate(UUID jobId, UUID projectId) {
        return requireInProject(jobRepository.findByIdForUpdate(jobId), projectId);
    }

    private JobModel requireInProject(Optional<JobModel> found, UUID projectId) {
        JobModel job = found.orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Job.NOT_FOUND);
        }
        return job;
    }

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private ProjectMemberModel requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel member = requireMember(projectId, userId);
        if (member.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
        return member;
    }

    private void requireCanRequestApproval(ProjectMemberModel caller, JobModel job, UUID callerId) {
        if (caller.getRole() == ProjectMemberRole.MEMBER
                && !callerId.equals(job.getAssignedTo())) {
            throw new ForbiddenException(ErrorMessages.Approval.ACCESS_DENIED_NOT_ASSIGNED);
        }
    }

    private ApprovalModel requireApprovalExists(UUID approvalId, UUID jobId) {
        return approvalRepository.findByIdAndJobId(approvalId, jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Approval.NOT_FOUND));
    }

    private void requireStatusIsDecision(ApprovalStatus status) {
        if (status == ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorMessages.Approval.CANNOT_SET_PENDING);
        }
    }

    private void requireDecisionApplied(int rows) {
        if (rows == 0) {
            throw new ConflictException(ErrorMessages.Approval.ALREADY_DECIDED);
        }
    }
}
