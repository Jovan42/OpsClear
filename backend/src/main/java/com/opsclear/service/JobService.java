package com.opsclear.service;

import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.exception.BadRequestException;
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

        if (request.getAssignedTo() != null) {
            userRepository.findById(request.getAssignedTo())
                    .orElseThrow(() -> new NotFoundException("Assigned user not found"));
        }

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

        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        requireJobInProject(job, projectId);

        if (requester.getRole() == ProjectMemberRole.MEMBER
                && !requesterId.equals(job.getAssignedTo())) {
            throw new ForbiddenException("Access denied: you are not assigned to this job");
        }

        return job;
    }

    @Transactional
    public JobModel update(UUID projectId, UUID jobId, UpdateJobRequest request, UUID requesterId) {
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, requesterId);

        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        requireJobInProject(job, projectId);

        if (request.getAssignedTo() != null) {
            userRepository.findById(request.getAssignedTo())
                    .orElseThrow(() -> new NotFoundException("Assigned user not found"));
        }

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

        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found"));

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

        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        requireJobInProject(job, projectId);

        job.softDelete();
        jobRepository.save(job);
        log.info("Soft-deleted job '{}' from project {}", jobId, projectId);
    }

    // --- Status transition rules (Phase 3) ---

    private void validateTransition(JobStatus from, JobStatus to,
                                    ProjectMemberModel requester, UUID assignedTo, UUID requesterId) {
        // 1. Block Phase 4 transitions early
        if (from == JobStatus.BLOCKED || to == JobStatus.BLOCKED) {
            throw new BadRequestException("Blocking is not supported in this phase");
        }

        // 2. Structural validity
        boolean valid = (from == JobStatus.NEW        && to == JobStatus.IN_PROGRESS)
                     || (from == JobStatus.IN_PROGRESS && to == JobStatus.COMPLETED)
                     || (from == JobStatus.COMPLETED   && to == JobStatus.IN_PROGRESS);

        if (!valid) {
            throw new BadRequestException("Invalid transition: " + from + " → " + to);
        }

        // 3. Permission check
        boolean isOwnerOrAdmin = requester.getRole() == ProjectMemberRole.OWNER
                || requester.getRole() == ProjectMemberRole.ADMIN;

        if (from == JobStatus.COMPLETED && !isOwnerOrAdmin) {
            throw new ForbiddenException("Only OWNER or ADMIN can reopen a completed job");
        }
        if (!isOwnerOrAdmin && !requesterId.equals(assignedTo)) {
            throw new ForbiddenException("Only OWNER, ADMIN, or the assigned member can change job status");
        }
    }

    // --- Guards ---

    private void requireJobInProject(JobModel job, UUID projectId) {
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException("Job not found");
        }
    }

    // --- Permission helpers ---

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this project"));
    }

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = requireMember(projectId, userId);
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException("Insufficient permissions: OWNER or ADMIN role required");
        }
    }
}
