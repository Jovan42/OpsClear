package com.opsclear.service;

import com.opsclear.dto.CreateJobTemplateRequest;
import com.opsclear.dto.UpdateJobTemplateRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
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
    private final RecurringScheduleRepository recurringScheduleRepository;
    private final FriendlyIdService friendlyIdService;

    // --- Project-scoped endpoints ---

    @Transactional(readOnly = true)
    public List<JobTemplateModel> list(UUID projectId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);
        UUID orgId = organisationRepository.findByMember(requesterId)
                .map(OrganisationModel::getId)
                .orElse(null);
        return jobTemplateRepository.findActiveByProjectIdOrOrgId(projectId, orgId);
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
        JobTemplateModel template = requireTemplateForProject(projectId, templateId);
        applyUpdate(template, request);
        JobTemplateModel updated = jobTemplateRepository.save(template);
        log.info("Updated job template {} in project {} by user {}", templateId, projectId, requesterId);
        return updated;
    }

    @Transactional
    public void softDelete(UUID projectId, UUID templateId, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        requireTemplateForProject(projectId, templateId);
        requireNoActiveSchedules(templateId);
        jobTemplateRepository.softDelete(templateId);
        log.info("Soft-deleted job template {} from project {} by user {}", templateId, projectId, requesterId);
    }

    @Transactional
    public void recordUsage(UUID projectId, UUID templateId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);
        requireTemplateForProjectOrOrg(projectId, templateId, requesterId);
        jobTemplateRepository.incrementOccurrenceCount(templateId);
        log.info("Recorded usage of job template {} by user {}", templateId, requesterId);
    }

    // --- Org-scoped endpoints ---

    @Transactional(readOnly = true)
    public List<JobTemplateModel> listOrgTemplates(UUID orgId, UUID requesterId) {
        requireOrg(orgId);
        requireOrgMember(orgId, requesterId);
        return jobTemplateRepository.findActiveByOrgId(orgId);
    }

    @Transactional
    public JobTemplateModel createOrgTemplate(UUID orgId, CreateJobTemplateRequest request, UUID requesterId) {
        requireOrg(orgId);
        requireOrgOwnerOrAdmin(orgId, requesterId);

        String friendlyId = friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.TEMPLATE);

        JobTemplateModel template = JobTemplateModel.builder()
                .friendlyId(friendlyId)
                .orgId(orgId)
                .name(request.getName().strip())
                .title(request.getTitle())
                .description(request.getDescription())
                .client(request.getClient())
                .priority(request.getPriority())
                .assigneeMode(Objects.requireNonNullElse(request.getAssigneeMode(), "NONE"))
                .deadlineOffsetDays(request.getDeadlineOffsetDays())
                .createdBy(requesterId)
                .build();

        JobTemplateModel saved = jobTemplateRepository.save(template);
        log.info("Created org-level job template '{}' in org {} by user {}", saved.getName(), orgId, requesterId);
        return saved;
    }

    @Transactional
    public JobTemplateModel updateOrgTemplate(UUID orgId, UUID templateId,
                                              UpdateJobTemplateRequest request, UUID requesterId) {
        requireOrg(orgId);
        requireOrgOwnerOrAdmin(orgId, requesterId);
        JobTemplateModel template = requireTemplateForOrg(orgId, templateId);
        applyUpdate(template, request);
        JobTemplateModel updated = jobTemplateRepository.save(template);
        log.info("Updated org-level job template {} in org {} by user {}", templateId, orgId, requesterId);
        return updated;
    }

    @Transactional
    public void deleteOrgTemplate(UUID orgId, UUID templateId, UUID requesterId) {
        requireOrg(orgId);
        requireOrgOwnerOrAdmin(orgId, requesterId);
        requireTemplateForOrg(orgId, templateId);
        requireNoActiveSchedules(templateId);
        jobTemplateRepository.softDelete(templateId);
        log.info("Soft-deleted org-level job template {} from org {} by user {}", templateId, orgId, requesterId);
    }

    // --- Shared helpers ---

    private void applyUpdate(JobTemplateModel template, UpdateJobTemplateRequest request) {
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

    private void requireOrg(UUID orgId) {
        organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
    }

    private void requireOrgMember(UUID orgId, UUID userId) {
        if (!organisationRepository.existsMember(orgId, userId)) {
            throw new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER);
        }
    }

    private void requireOrgOwnerOrAdmin(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_A_MEMBER));
        if (role == OrganisationRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    private JobTemplateModel requireTemplateForProject(UUID projectId, UUID templateId) {
        return jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .filter(t -> projectId.equals(t.getProjectId()))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobTemplate.NOT_FOUND));
    }

    private JobTemplateModel requireTemplateForOrg(UUID orgId, UUID templateId) {
        return jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .filter(t -> orgId.equals(t.getOrgId()))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobTemplate.NOT_FOUND));
    }

    private void requireTemplateForProjectOrOrg(UUID projectId, UUID templateId, UUID requesterId) {
        JobTemplateModel template = jobTemplateRepository.findByIdAndDeletedAtIsNull(templateId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.JobTemplate.NOT_FOUND));
        if (projectId.equals(template.getProjectId())) {
            return;
        }
        UUID orgId = organisationRepository.findByMember(requesterId)
                .map(OrganisationModel::getId)
                .orElse(null);
        if (orgId == null || !orgId.equals(template.getOrgId())) {
            throw new NotFoundException(ErrorMessages.JobTemplate.NOT_FOUND);
        }
    }

    private void requireNoActiveSchedules(UUID templateId) {
        List<String> names = recurringScheduleRepository.findByTemplateIdAndActive(templateId)
                .stream().map(r -> r.getName()).toList();
        if (!names.isEmpty()) {
            throw new ConflictException(ErrorMessages.JobTemplate.ACTIVE_SCHEDULES + names);
        }
    }
}
