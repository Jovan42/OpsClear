package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobRelationshipModel;
import com.opsclear.model.JobRelationshipType;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobRelationshipService {

    private final JobRelationshipRepository jobRelationshipRepository;
    private final JobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public JobRelationshipModel create(UUID projectId, UUID sourceJobId,
                                       UUID targetJobId, JobRelationshipType type,
                                       UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);

        if (sourceJobId.equals(targetJobId)) {
            throw new BadRequestException(ErrorMessages.JobRelationship.SELF_REFERENCE);
        }

        JobModel sourceJob = requireJobInProject(sourceJobId, projectId);
        JobModel targetJob = requireJobInProject(targetJobId, projectId);

        if (jobRelationshipRepository.existsBySourceJobIdAndTargetJobIdAndType(sourceJobId, targetJobId, type)) {
            throw new ConflictException(ErrorMessages.JobRelationship.ALREADY_EXISTS);
        }

        JobRelationshipModel relationship = JobRelationshipModel.builder()
                .sourceJobId(sourceJob.getId())
                .targetJobId(targetJob.getId())
                .type(type)
                .createdBy(requesterId)
                .build();
        JobRelationshipModel saved = jobRelationshipRepository.save(relationship);
        log.info("Created {} relationship from job {} to job {} in project {} by user {}",
                type, sourceJobId, targetJobId, projectId, requesterId);
        return saved;
    }

    @Transactional
    public void delete(UUID projectId, UUID jobId, UUID relationshipId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);

        jobRelationshipRepository.findById(relationshipId)
                .filter(r -> r.getSourceJobId().equals(jobId) || r.getTargetJobId().equals(jobId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobRelationship.NOT_FOUND));

        jobRelationshipRepository.deleteById(relationshipId);
        log.info("Deleted relationship {} from job {} in project {} by user {}",
                relationshipId, jobId, projectId, requesterId);
    }

    // --- Guards ---

    private void requireProject(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireMember(UUID projectId, UUID userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private JobModel requireJobInProject(UUID jobId, UUID projectId) {
        return jobRepository.findByIdAndDeletedAtIsNull(jobId)
                .filter(j -> j.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Job.NOT_FOUND));
    }
}
