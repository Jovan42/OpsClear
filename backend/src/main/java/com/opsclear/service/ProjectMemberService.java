package com.opsclear.service;

import com.opsclear.dto.AddMemberRequest;
import com.opsclear.exception.ConflictException;
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
            throw new ForbiddenException("Cannot assign OWNER role");
        }

        userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getUserId())) {
            throw new ConflictException("User is already a member of this project");
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
            throw new ForbiddenException("Cannot assign OWNER role");
        }

        ProjectMemberModel member = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        requireMemberInProject(member, projectId);

        if (member.getRole() == ProjectMemberRole.OWNER) {
            throw new ForbiddenException("Cannot change the project owner's role");
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

        ProjectMemberModel member = projectMemberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        requireMemberInProject(member, projectId);

        if (member.getRole() == ProjectMemberRole.OWNER) {
            throw new ForbiddenException("Cannot remove the project owner");
        }

        projectMemberRepository.delete(memberId);
        log.info("Removed member {} from project {}", memberId, projectId);
    }

    private void requireMemberInProject(ProjectMemberModel member, UUID projectId) {
        if (!member.getProjectId().equals(projectId)) {
            throw new NotFoundException("Member not found");
        }
    }

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private void requireOwnerOrAdmin(UUID projectId, UUID userId) {
        ProjectMemberModel requester = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this project"));
        if (requester.getRole() == ProjectMemberRole.MEMBER) {
            throw new ForbiddenException("Insufficient permissions: OWNER or ADMIN role required");
        }
    }

    private void requireMember(UUID projectId, UUID userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this project"));
    }
}
