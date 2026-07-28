
package com.opsclear.service;

import com.opsclear.dto.CreateJobTypeRequest;
import com.opsclear.dto.UpdateJobTypeRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobTypeModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.JobTypeRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobTypeService {

    private final JobTypeRepository jobTypeRepository;
    private final JobTemplateRepository jobTemplateRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public List<JobTypeModel> list(UUID projectId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);
        return jobTypeRepository.findByProjectId(projectId);
    }

    @Transactional
    public JobTypeModel create(UUID projectId, CreateJobTypeRequest request, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);

        JobTypeModel type = JobTypeModel.builder()
                .projectId(projectId)
                .name(request.getName().strip())
                .color(request.getColor())
                .displayOrder(jobTypeRepository.nextDisplayOrder(projectId))
                .build();
        JobTypeModel saved = jobTypeRepository.save(type);
        log.info("Created job type '{}' in project {} by user {}", saved.getName(), projectId, requesterId);
        return saved;
    }

    @Transactional
    public JobTypeModel update(UUID projectId, UUID typeId, UpdateJobTypeRequest request, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        JobTypeModel type = requireTypeForProject(projectId, typeId);

        type.setName(request.getName().strip());
        type.setColor(request.getColor());
        type.setDisplayOrder(request.getDisplayOrder());
        JobTypeModel updated = jobTypeRepository.save(type);
        log.info("Updated job type {} in project {} by user {}", typeId, projectId, requesterId);
        return updated;
    }

    @Transactional
    public void delete(UUID projectId, UUID typeId, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        requireTypeForProject(projectId, typeId);
        requireNotReferenced(typeId);

        jobTypeRepository.delete(typeId);
        log.info("Deleted job type {} from project {} by user {}", typeId, projectId, requesterId);
    }

    // --- Guards ---

    private void requireNotReferenced(UUID typeId) {
        int jobCount = jobTypeRepository.countJobsReferencing(typeId);
        int templateCount = jobTemplateRepository.countTemplatesReferencing(typeId);
        if (jobCount > 0 && templateCount > 0) {
            throw new ConflictException(String.format(
                    ErrorMessages.JobType.STILL_REFERENCED_BY_JOBS_AND_TEMPLATES, jobCount, templateCount));
        }
        if (jobCount > 0) {
            throw new ConflictException(String.format(ErrorMessages.JobType.STILL_REFERENCED_BY_JOBS, jobCount));
        }
        if (templateCount > 0) {
            throw new ConflictException(
                    String.format(ErrorMessages.JobType.STILL_REFERENCED_BY_TEMPLATES, templateCount));
        }
    }

    private JobTypeModel requireTypeForProject(UUID projectId, UUID typeId) {
        return jobTypeRepository.findById(typeId)
                .filter(t -> t.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobType.NOT_FOUND));
    }

    private void requireProject(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireMember(UUID projectId, UUID userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }
}
