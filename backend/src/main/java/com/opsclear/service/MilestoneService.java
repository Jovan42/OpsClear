package com.opsclear.service;

import com.opsclear.dto.CreateMilestoneRequest;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.dto.UpdateMilestoneRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.MilestoneModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrganisationRepository;
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
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganisationRepository organisationRepository;
    private final FriendlyIdService friendlyIdService;

    @Transactional(readOnly = true)
    public List<MilestoneModel> list(UUID projectId, UUID requesterId) {
        requireProject(projectId);
        requireMember(projectId, requesterId);
        return milestoneRepository.findActiveByProjectId(projectId);
    }

    @Transactional
    public MilestoneModel create(UUID projectId, CreateMilestoneRequest request, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);

        UUID orgId = organisationRepository.findByMember(requesterId)
                .map(OrganisationModel::getId)
                .orElse(null);

        String friendlyId = orgId != null
                ? friendlyIdService.nextFriendlyId(orgId, FriendlyIdEntityType.MILESTONE)
                : null;

        MilestoneModel milestone = MilestoneModel.builder()
                .friendlyId(friendlyId)
                .projectId(projectId)
                .name(request.getName().strip())
                .description(request.getDescription())
                .deadline(request.getDeadline())
                .build();
        MilestoneModel saved = milestoneRepository.save(milestone);
        log.info("Created milestone '{}' in project {} by user {}", saved.getName(), projectId, requesterId);
        return saved;
    }

    @Transactional
    public MilestoneModel update(UUID projectId, UUID milestoneId,
                                 UpdateMilestoneRequest request, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        MilestoneModel milestone = milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)
                .filter(m -> m.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Milestone.NOT_FOUND));
        milestone.setName(request.getName().strip());
        milestone.setDescription(request.getDescription());
        milestone.setDeadline(request.getDeadline());
        MilestoneModel updated = milestoneRepository.save(milestone);
        log.info("Updated milestone '{}' in project {} by user {}", milestoneId, projectId, requesterId);
        return updated;
    }

    @Transactional
    public void softDelete(UUID projectId, UUID milestoneId, UUID requesterId) {
        requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        milestoneRepository.findByIdAndDeletedAtIsNull(milestoneId)
                .filter(m -> m.getProjectId().equals(projectId))
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Milestone.NOT_FOUND));
        milestoneRepository.softDelete(milestoneId);
        log.info("Soft-deleted milestone {} from project {} by user {}", milestoneId, projectId, requesterId);
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
}
