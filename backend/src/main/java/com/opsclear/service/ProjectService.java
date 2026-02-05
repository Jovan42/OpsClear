package com.opsclear.service;

import com.opsclear.dto.CreateProjectRequest;
import com.opsclear.dto.UpdateProjectRequest;
import com.opsclear.entity.Project;
import com.opsclear.entity.User;
import com.opsclear.exception.NotFoundException;
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

    @Transactional
    public Project create(CreateProjectRequest request, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .owner(owner)
                .build();

        project = projectRepository.save(project);
        log.info("Created project '{}' for user {}", project.getName(), ownerId);
        return project;
    }

    @Transactional(readOnly = true)
    public List<Project> getProjectsByOwner(UUID ownerId) {
        return projectRepository.findByOwnerIdAndDeletedAtIsNull(ownerId);
    }

    @Transactional(readOnly = true)
    public Project getById(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional
    public Project update(UUID projectId, UpdateProjectRequest request) {
        Project project = getById(projectId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        log.info("Updated project '{}'", project.getId());
        return projectRepository.save(project);
    }

    @Transactional
    public void softDelete(UUID projectId) {
        Project project = getById(projectId);
        project.softDelete();
        projectRepository.save(project);
        log.info("Soft-deleted project '{}'", projectId);
    }
}
