package com.opsclear.service;

import com.opsclear.dto.AddMemberRequest;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
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
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ProjectMemberModel> listMembers(UUID projectId, UUID requesterId) {
        requireProjectExists(projectId);
        requireMember(projectId, requesterId);
        return projectMemberRepository.findByProjectId(projectId);
    }

    @Transactional
    public ProjectMemberModel addMember(UUID projectId, UUID requesterId, AddMemberRequest request) {
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, requesterId);

        if (request.getRole() == ProjectMemberRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Member.CANNOT_ASSIGN_OWNER_ROLE);
        }

        requireUserExists(request.getUserId());

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getUserId())) {
            throw new ConflictException(ErrorMessages.Member.ALREADY_A_MEMBER);
        }

        ProjectMemberModel member = ProjectMemberModel.builder()
                .projectId(projectId)
                .userId(request.getUserId())
                .role(request.getRole())
                .build();

        ProjectMemberModel saved = projectMemberRepository.save(member);
        log.info("Added user {} as {} to project {}", request.getUserId(), request.getRole(), projectId);
        return saved;
    }

    @Transactional
    public ProjectMemberModel updateRole(UUID projectId, UUID requesterId, UUID memberId, ProjectMemberRole newRole) {
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, requesterId);

        if (newRole == ProjectMemberRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Member.CANNOT_ASSIGN_OWNER_ROLE);
        }

        ProjectMemberModel member = requireMembership(memberId);
        requireMemberInProject(member, projectId);

        if (member.getRole() == ProjectMemberRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Member.CANNOT_CHANGE_OWNER_ROLE);
        }

        member.setRole(newRole);
        ProjectMemberModel updated = projectMemberRepository.save(member);
        log.info("Updated member {} role to {} in project {}", memberId, newRole, projectId);
        return updated;
    }

    @Transactional
    public void removeMember(UUID projectId, UUID requesterId, UUID memberId) {
        requireProjectExists(projectId);
        requireOwnerOrAdmin(projectId, requesterId);

        ProjectMemberModel member = requireMembership(memberId);
        requireMemberInProject(member, projectId);

        if (member.getRole() == ProjectMemberRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Member.CANNOT_REMOVE_OWNER);
        }

        projectMemberRepository.delete(memberId);
        log.info("Removed member {} from project {}", memberId, projectId);
    }

    // --- Guards ---

    private ProjectMemberModel requireMembership(UUID memberId) {
        return projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Member.NOT_FOUND));
    }

    private void requireMemberInProject(ProjectMemberModel member, UUID projectId) {
        if (!member.getProjectId().equals(projectId)) {
            throw new NotFoundException(ErrorMessages.Member.NOT_FOUND);
        }
    }

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private void requireUserExists(UUID userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));
    }

    // --- Permission helpers ---

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException(ErrorMessages.Member.INSUFFICIENT_PERMISSIONS_OWNER_OR_ADMIN);
        }
    }

    private void requireMember(UUID projectId, UUID userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }
}
