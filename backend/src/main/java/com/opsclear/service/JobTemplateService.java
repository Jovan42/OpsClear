package com.opsclear.service;

import com.opsclear.dto.CreateJobTemplateRequest;
import com.opsclear.dto.UpdateJobTemplateRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobTemplateService {

    private final JobTemplateRepository jobTemplateRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganisationRepository organisationRepository;
    private final FriendlyIdService friendlyIdService;

    @Transactional(readOnly = true)
    public List<JobTemplateModel> list(UUID projectId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);
        return jobTemplateRepository.findActiveByProjectId(projectId);
    }

    @Transactional
    public JobTemplateModel create(UUID projectId, CreateJobTemplateRequest request, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);

        String friendlyId = organisationRepository.findByMember(requesterId)
                .map(o -> friendlyIdService.nextFriendlyId(o.getId(), FriendlyIdEntityType.TEMPLATE))
                .orElse(null);

        JobTemplateModel template = JobTemplateModel.builder()
                .friendlyId(friendlyId)
                .projectId(projectId)
                .name(request.getName().strip())
                .title(request.getTitle())
                .description(request.getDescription())
                .client(request.getClient())
                .priority(request.getPriority())
                .assigneeMode(Objects.requireNonNullElse(request.getAssigneeMode(), "NONE"))
                .assigneeId(request.getAssigneeId())
                .milestoneId(request.getMilestoneId())
                .deadlineOffsetDays(request.getDeadlineOffsetDays())
                .createdBy(requesterId)
                .build();

        JobTemplateModel saved = jobTemplateRepository.save(template);
        log.info("Created job template '{}' in project {} by user {}", saved.getName(), projectId, requesterId);
        return saved;
    }

    @Transactional
    public JobTemplateModel update(UUID projectId, UUID templateId,
                                   UpdateJobTemplateRequest request, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        JobTemplateModel template = requireTemplate(projectId, templateId);

        template.setName(request.getName().strip());
        template.setTitle(request.getTitle());
        template.setDescription(request.getDescription());
        template.setClient(request.getClient());
        template.setPriority(request.getPriority());
        template.setAssigneeMode(
                request.getAssigneeMode() != null ? request.getAssigneeMode() : template.getAssigneeMode());
        template.setAssigneeId(request.getAssigneeId());
        template.setMilestoneId(request.getMilestoneId());
        template.setDeadlineOffsetDays(request.getDeadlineOffsetDays());

        JobTemplateModel updated = jobTemplateRepository.save(template);
        log.info("Updated job template {} in project {} by user {}", templateId, projectId, requesterId);
        return updated;
    }

    @Transactional
    public void softDelete(UUID projectId, UUID templateId, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        requireTemplate(projectId, templateId);
        jobTemplateRepository.softDelete(templateId);
        log.info("Soft-deleted job template {} from project {} by user {}", templateId, projectId, requesterId);
    }

    @Transactional
    public void recordUsage(UUID projectId, UUID templateId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);
        requireTemplate(projectId, templateId);
        jobTemplateRepository.incrementOccurrenceCount(templateId);
        log.info("Recorded usage of job template {} by user {}", templateId, requesterId);
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

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    private JobTemplateModel requireTemplate(UUID projectId, UUID templateId) {
        return jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .filter(t -> t.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobTemplate.NOT_FOUND));
    }
}
