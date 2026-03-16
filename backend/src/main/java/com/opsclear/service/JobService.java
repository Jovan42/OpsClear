package com.opsclear.service;

import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.exception.ConflictException;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final MilestoneRepository milestoneRepository;
    private final BlockReasonService blockReasonService;

    @Transactional
    public JobModel create(UUID projectId, CreateJobRequest request, UUID requesterId) {
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        requireMember(projectId, requesterId);
        requireAssignedUserExists(request.getAssignedTo());
        requireMilestoneInProject(request.getMilestoneId(), projectId);

        JobModel job = JobModel.builder()
                .projectId(projectId)
                .title(request.getTitle())
                .description(request.getDescription())
                .client(request.getClient())
                .assignedTo(request.getAssignedTo())
                .deadline(request.getDeadline())
                .status(JobStatus.NEW)
                .priority(request.getPriority() != null ? request.getPriority() : JobPriority.MEDIUM)
                .milestoneId(request.getMilestoneId())
                .createdBy(requesterId)
                .build();

        JobModel saved = jobRepository.save(job);
        log.info("Created job '{}' in project {} by user {}", saved.getTitle(), projectId, requesterId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<JobModel> list(UUID projectId, UUID requesterId, String q,
                               JobPriority priority, UUID milestoneId) {
        requireProjectExistsById(projectId);
        ProjectMemberModel requester = requireMember(projectId, requesterId);
        boolean isMember = requester.getRole() == ProjectMemberRole.MEMBER;
        UUID assignedTo = isMember ? requesterId : null;
        String trimmedQ = (q != null && !q.isBlank()) ? q.trim() : null;
        return jobRepository.findByFilters(projectId, assignedTo, trimmedQ, priority, milestoneId);
    }

    @Transactional(readOnly = true)
    public JobModel getById(UUID projectId, UUID jobId, UUID requesterId) {
        requireProjectExistsById(projectId);
        ProjectMemberModel requester = requireMember(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);

        if (requester.getRole() == ProjectMemberRole.MEMBER
                && !requesterId.equals(job.getAssignedTo())) {
            throw new ForbiddenException(ErrorMessages.Job.ACCESS_DENIED_NOT_ASSIGNED);
        }

        return job;
    }

    @Transactional
    public JobModel update(UUID projectId, UUID jobId, UpdateJobRequest request, UUID requesterId) {
        requireProjectExistsById(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);
        requireAssignedUserExists(request.getAssignedTo());
        requireMilestoneInProject(request.getMilestoneId(), projectId);

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setClient(request.getClient());
        job.setAssignedTo(request.getAssignedTo());
        job.setDeadline(request.getDeadline());
        if (request.getPriority() != null) {
            job.setPriority(request.getPriority());
        }
        job.setMilestoneId(request.getMilestoneId());

        JobModel updated = jobRepository.save(job);
        log.info("Updated job '{}' in project {}", jobId, projectId);
        return updated;
    }

    @Transactional
    public JobModel updateStatus(UUID projectId, UUID jobId, JobStatus newStatus, String reason, UUID requesterId) {
        ProjectModel project = requireProjectExists(projectId);
        requireProjectNotCompleted(project);
        ProjectMemberModel requester = requireMember(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);

        JobStatus oldStatus = job.getStatus();
        validateTransition(oldStatus, newStatus, requester, job.getAssignedTo(), requesterId);

        if (newStatus == JobStatus.BLOCKED) {
            if (reason == null || reason.isBlank()) {
                throw new BadRequestException(ErrorMessages.Job.BLOCK_REASON_REQUIRED);
            }
            BlockReasonModel blockReason = blockReasonService.findOrCreate(projectId, reason);
            job.setStatus(JobStatus.BLOCKED);
            job.setBlockedBy(requesterId);
            job.setBlockedReasonId(blockReason.getId());
            job.setBlockedAt(Instant.now());
        } else if (oldStatus == JobStatus.BLOCKED) {
            job.setStatus(newStatus);
            job.setBlockedBy(null);
            job.setBlockedReasonId(null);
            job.setBlockedAt(null);
        } else {
            job.setStatus(newStatus);
        }

        JobModel updated = jobRepository.save(job);
        log.info("Job {} status changed from {} to {} by user {}", jobId, oldStatus, newStatus, requesterId);
        return updated;
    }

    @Transactional
    public void softDelete(UUID projectId, UUID jobId, UUID requesterId) {
        requireProjectExistsById(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);

        job.softDelete();
        jobRepository.save(job);
        log.info("Soft-deleted job '{}' from project {}", jobId, projectId);
    }

    // --- Status transition rules ---

    private void validateTransition(JobStatus from, JobStatus to,
                                    ProjectMemberModel requester, UUID assignedTo, UUID requesterId) {
        boolean valid = (from == JobStatus.NEW && to == JobStatus.IN_PROGRESS)
                || (from == JobStatus.IN_PROGRESS && to == JobStatus.COMPLETED)
                || (from == JobStatus.IN_PROGRESS && to == JobStatus.BLOCKED)
                || (from == JobStatus.BLOCKED && to == JobStatus.IN_PROGRESS)
                || (from == JobStatus.COMPLETED && to == JobStatus.IN_PROGRESS);

        if (!valid) {
            throw new BadRequestException(ErrorMessages.Job.INVALID_TRANSITION + from + " → " + to);
        }

        boolean isOwnerOrAdmin = requester.getRole() == ProjectMemberRole.OWNER
                || requester.getRole() == ProjectMemberRole.ADMIN;

        if (from == JobStatus.COMPLETED && !isOwnerOrAdmin) {
            throw new ForbiddenException(ErrorMessages.Job.ONLY_OWNER_ADMIN_CAN_REOPEN);
        }
        if (!isOwnerOrAdmin && !requesterId.equals(assignedTo)) {
            throw new ForbiddenException(ErrorMessages.Job.ONLY_OWNER_ADMIN_OR_ASSIGNEE_CAN_CHANGE_STATUS);
        }
    }

    // --- Guards ---

    private JobModel requireJob(UUID jobId) {
        return jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
    }

    private void requireMilestoneInProject(UUID milestoneId, UUID projectId) {
        if (milestoneId == null) {
            return;
        }
        milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)
                .filter(m -> m.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Milestone.NOT_FOUND));
    }

    private void requireAssignedUserExists(UUID userId) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.ASSIGNED_USER_NOT_FOUND));
    }

    private void requireJobInProject(JobModel job, UUID projectId) {
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Job.NOT_FOUND);
        }
    }

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

    // --- Permission helpers ---

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = requireMember(projectId, userId);
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }
}
