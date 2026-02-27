package com.opsclear.service;

import com.opsclear.dto.CreateProjectRequest;
import com.opsclear.dto.UpdateProjectRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
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
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public ProjectModel create(CreateProjectRequest request, UUID ownerId) {
        requireUserExists(ownerId);

        ProjectModel project = ProjectModel.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .build();

        project = projectRepository.save(project);

        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(project.getId())
                .userId(ownerId)
                .role(ProjectMemberRole.OWNER)
                .build());

        log.info("Created project '{}' for user {}", project.getName(), ownerId);
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectModel> getProjectsForMember(UUID userId) {
        return projectRepository.findByMemberIdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public ProjectModel getById(UUID projectId, UUID requesterId) {
        ProjectModel project = requireProject(projectId);
        requireMember(projectId, requesterId);
        return project;
    }

    @Transactional
    public ProjectModel update(UUID projectId, UpdateProjectRequest request, UUID requesterId) {
        ProjectModel project = requireProject(projectId);
        requireOwnerOrAdmin(projectId, requesterId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        log.info("Updated project '{}'", project.getId());
        return projectRepository.save(project);
    }

    @Transactional
    public void softDelete(UUID projectId, UUID requesterId) {
        ProjectModel project = requireProject(projectId);
        requireOwner(projectId, requesterId);
        project.softDelete();
        projectRepository.save(project);
        log.info("Soft-deleted project '{}'", projectId);
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
