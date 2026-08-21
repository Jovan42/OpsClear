package com.opsclear.service;

import com.opsclear.dto.CreateProjectRequest;
import com.opsclear.dto.UpdateProjectRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectDirectoryEntryModel;
import com.opsclear.model.ProjectLinkModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectLinkRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final BlockReasonRepository blockReasonRepository;
    private final OrganisationRepository organisationRepository;
    private final ProjectLinkRepository projectLinkRepository;
    private final FriendlyIdService friendlyIdService;

    @Transactional
    public ProjectModel create(CreateProjectRequest request, UUID ownerId) {
        UUID organisationId = requireOrgMembership(ownerId);
        requireUserExists(ownerId);
        requireNameAvailable(request.getName(), ownerId);

        String friendlyId = friendlyIdService.nextFriendlyId(organisationId, FriendlyIdEntityType.PROJECT);

        ProjectModel project = ProjectModel.builder()
                .friendlyId(friendlyId)
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .organisationId(organisationId)
                .build();

        project = projectRepository.save(project);

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project.getId())
                .userId(ownerId)
                .role(ProjectMemberRole.OWNER)
                .build());

        if (request.getBlockReasons() != null) {
            UUID projectId = project.getId();
            request.getBlockReasons().stream()
                    .map(String::strip)
                    .distinct()
                    .forEach(r -> blockReasonRepository.findOrCreate(projectId, r));
        }

        log.info("Created project '{}' for user {}", project.getName(), ownerId);
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectModel> getProjectsForMember(UUID userId, ProjectStatus status) {
        requireOrgMembership(userId);
        List<ProjectModel> projects = projectRepository.findByMemberIdAndStatusAndDeletedAtIsNull(userId, status);
        attachLinks(projects);
        return projects;
    }

    @Transactional(readOnly = true)
    public ProjectModel getById(UUID projectId, UUID requesterId) {
        requireOrgMembership(requesterId);
        ProjectModel project = requireProject(projectId);
        requireMember(projectId, requesterId);
        project.setLinks(projectLinkRepository.findByProjectId(projectId));
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectDirectoryEntryModel> getDirectory(UUID orgId, UUID callerId) {
        requireOrgOwnerOrAdmin(orgId, callerId);
        return projectRepository.findDirectoryByOrgId(orgId);
    }

    private void attachLinks(List<ProjectModel> projects) {
        List<UUID> projectIds = projects.stream().map(ProjectModel::getId).toList();
        Map<UUID, List<ProjectLinkModel>> byProjectId = projectLinkRepository.findByProjectIds(projectIds).stream()
                .collect(Collectors.groupingBy(ProjectLinkModel::getProjectId));
        projects.forEach(p -> p.setLinks(byProjectId.getOrDefault(p.getId(), List.of())));
    }

    @Transactional
    public ProjectModel update(UUID projectId, UpdateProjectRequest request, UUID requesterId) {
        requireOrgMembership(requesterId);
        ProjectModel project = requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        requireNameAvailableForUpdate(request.getName(), project.getOwnerId(), projectId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        log.info("Updated project '{}'", project.getId());
        return projectRepository.save(project);
    }

    @Transactional
    public ProjectModel updateStatus(UUID projectId, ProjectStatus newStatus, UUID requesterId) {
        requireOrgMembership(requesterId);
        ProjectModel project = requireProject(projectId);
        requireOwner(projectId, requesterId);

        if (newStatus == ProjectStatus.COMPLETED) {
            int openJobs = projectRepository.countOpenJobsByProjectId(projectId);
            if (openJobs > 0) {
                throw new ConflictException(
                        String.format(ErrorMessages.Project.HAS_OPEN_JOBS, openJobs));
            }
        }

        project.setStatus(newStatus);
        ProjectModel updated = projectRepository.save(project);
        log.info("Project '{}' status changed to {} by user {}", projectId, newStatus, requesterId);
        return updated;
    }

    @Transactional
    public void softDelete(UUID projectId, UUID requesterId) {
        requireOrgMembership(requesterId);
        ProjectModel project = requireProject(projectId);
        requireOwner(projectId, requesterId);
        project.softDelete();
        projectRepository.save(project);
        log.info("Soft-deleted project '{}'", projectId);
    }

    // --- Org guard ---

    private UUID requireOrgMembership(UUID userId) {
        return organisationRepository.findByMember(userId)
                .map(OrganisationModel::getId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Organisation.NOT_IN_ORG));
    }

    private void requireOrgOwnerOrAdmin(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
        if (role == OrganisationRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    // --- Name uniqueness ---

    private void requireNameAvailable(String name, UUID ownerId) {
        if (projectRepository.existsByNameAndOwnerIdAndDeletedAtIsNull(name, ownerId)) {
            throw new ConflictException(ErrorMessages.Project.NAME_ALREADY_EXISTS);
        }
    }

    private void requireNameAvailableForUpdate(String name, UUID ownerId, UUID excludeProjectId) {
        if (projectRepository.existsByNameAndOwnerIdAndIdNotAndDeletedAtIsNull(name, ownerId, excludeProjectId)) {
            throw new ConflictException(ErrorMessages.Project.NAME_ALREADY_EXISTS);
        }
    }

    // --- Guards ---

    private ProjectModel requireProject(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireUserExists(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));
    }

    // --- Permission helpers ---

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

    private void requireOwner(UUID projectId, UUID userId) {
        ProjectMemberModel requester = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
        if (requester.getRole() != ProjectMemberRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER);
        }
    }
}
