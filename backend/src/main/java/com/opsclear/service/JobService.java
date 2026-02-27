package com.opsclear.service;

import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public JobModel create(UUID projectId, CreateJobRequest request, UUID requesterId) {
        requireProjectExists(projectId);
        requireMember(projectId, requesterId);
        requireAssignedUserExists(request.getAssignedTo());

        JobModel job = JobModel.builder()
                .projectId(projectId)
                .title(request.getTitle())
                .description(request.getDescription())
                .client(request.getClient())
                .assignedTo(request.getAssignedTo())
                .deadline(request.getDeadline())
                .status(JobStatus.NEW)
                .createdBy(requesterId)
                .build();

        JobModel saved = jobRepository.save(job);
        log.info("Created job '{}' in project {} by user {}", saved.getTitle(), projectId, requesterId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<JobModel> list(UUID projectId, UUID requesterId) {
        requireProjectExists(projectId);
        ProjectMemberModel requester = requireMember(projectId, requesterId);

        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            return jobRepository.findByProjectIdAndAssignedToAndDeletedAtIsNull(projectId, requesterId);
        }
        return jobRepository.findByProjectIdAndDeletedAtIsNull(projectId);
    }

    @Transactional(readOnly = true)
    public JobModel getById(UUID projectId, UUID jobId, UUID requesterId) {
        requireProjectExists(projectId);
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
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);
        requireAssignedUserExists(request.getAssignedTo());

        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setClient(request.getClient());
        job.setAssignedTo(request.getAssignedTo());
        job.setDeadline(request.getDeadline());

        JobModel updated = jobRepository.save(job);
        log.info("Updated job '{}' in project {}", jobId, projectId);
        return updated;
    }

    @Transactional
    public JobModel updateStatus(UUID projectId, UUID jobId, JobStatus newStatus, UUID requesterId) {
        requireProjectExists(projectId);
        ProjectMemberModel requester = requireMember(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);

        validateTransition(job.getStatus(), newStatus, requester, job.getAssignedTo(), requesterId);

        job.setStatus(newStatus);
        JobModel updated = jobRepository.save(job);
        log.info("Job {} status changed from {} to {} by user {}", jobId, job.getStatus(), newStatus, requesterId);
        return updated;
    }

    @Transactional
    public void softDelete(UUID projectId, UUID jobId, UUID requesterId) {
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        JobModel job = requireJob(jobId);
        requireJobInProject(job, projectId);

        job.softDelete();
        jobRepository.save(job);
        log.info("Soft-deleted job '{}' from project {}", jobId, projectId);
    }

    // --- Status transition rules (Phase 3) ---

    private void validateTransition(JobStatus from, JobStatus to,
                                    ProjectMemberModel requester, UUID assignedTo, UUID requesterId) {
        if (from == JobStatus.BLOCKED || to == JobStatus.BLOCKED) {
            throw new BadRequestException(ErrorMessages.Job.BLOCKING_NOT_SUPPORTED);
        }

        boolean valid = (from == JobStatus.NEW        && to == JobStatus.IN_PROGRESS)
                     || (from == JobStatus.IN_PROGRESS && to == JobStatus.COMPLETED)
                     || (from == JobStatus.COMPLETED   && to == JobStatus.IN_PROGRESS);

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

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
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
