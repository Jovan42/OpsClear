package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
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
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public ApprovalModel request(UUID projectId, UUID jobId, String description, UUID callerId) {
        requireProjectExists(projectId);
        JobModel job = requireJobInProject(jobId, projectId);
        ProjectMemberModel caller = requireMember(projectId, callerId);
        requireCanRequestApproval(caller, job, callerId);

        ApprovalModel approval = approvalRepository.insert(jobId, callerId, description.strip());
        log.info("Approval {} requested on job {} in project {} by user {}",
                approval.getId(), jobId, projectId, callerId);
        return approval;
    }

    @Transactional
    public ApprovalModel decide(UUID projectId, UUID jobId, UUID approvalId,
                                ApprovalStatus status, String comment, UUID callerId) {
        requireProjectExists(projectId);
        requireJobInProject(jobId, projectId);
        requireOwnerOrAdmin(projectId, callerId);
        requireApprovalExists(approvalId, jobId);
        requireStatusIsDecision(status);

        int rows = approvalRepository.updateDecision(approvalId, callerId, status, comment, Instant.now());
        requireDecisionApplied(rows);

        log.info("Approval {} on job {} {} by user {}", approvalId, jobId, status, callerId);
        return approvalRepository.findByIdAndJobId(approvalId, jobId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ApprovalModel> listByJob(UUID projectId, UUID jobId, UUID callerId) {
        requireProjectExists(projectId);
        requireJobInProject(jobId, projectId);
        requireMember(projectId, callerId);
        return approvalRepository.findByJobId(jobId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalModel> listPendingByProject(UUID projectId, UUID callerId) {
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, callerId);
        return approvalRepository.findPendingByProjectId(projectId);
    }

    // --- Guards ---

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private JobModel requireJobInProject(UUID jobId, UUID projectId) {
        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Job.NOT_FOUND);
        }
        return job;
    }

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel member = requireMember(projectId, userId);
        if (member.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    private void requireCanRequestApproval(ProjectMemberModel caller, JobModel job, UUID callerId) {
        if (caller.getRole() == ProjectMemberRole.MEMBER
                && !callerId.equals(job.getAssignedTo())) {
            throw new ForbiddenException(ErrorMessages.Approval.ACCESS_DENIED_NOT_ASSIGNED);
        }
    }

    private void requireApprovalExists(UUID approvalId, UUID jobId) {
        approvalRepository.findByIdAndJobId(approvalId, jobId)
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
