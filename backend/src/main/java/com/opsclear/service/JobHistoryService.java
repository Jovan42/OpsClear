package com.opsclear.service;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatusHistoryModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobHistoryService {

    private final JobStatusHistoryRepository jobStatusHistoryRepository;
    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public List<JobStatusHistoryModel> getHistory(UUID projectId, UUID jobId, UUID requesterId) {
        requireProjectExists(projectId);
        ProjectMemberModel member = requireMember(projectId, requesterId);
        JobModel job = requireJobInProject(jobId, projectId);
        requireJobAccess(member, job, requesterId);
        return jobStatusHistoryRepository.findByJobId(jobId);
    }

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private JobModel requireJobInProject(UUID jobId, UUID projectId) {
        JobModel job = jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
        if (!job.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Job.NOT_FOUND);
        }
        return job;
    }

    private void requireJobAccess(ProjectMemberModel member, JobModel job, UUID requesterId) {
        if (member.getRole() == ProjectMemberRole.MEMBER && !requesterId.equals(job.getAssignedTo())) {
            throw new ForbiddenException(ErrorMessages.Job.ACCESS_DENIED_NOT_ASSIGNED);
        }
    }
}
