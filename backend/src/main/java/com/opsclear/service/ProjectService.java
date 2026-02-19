package com.opsclear.service;

import com.opsclear.dto.CreateProjectRequest;
import com.opsclear.dto.UpdateProjectRequest;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ProjectModel;
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
    public ProjectModel create(CreateProjectRequest request, UUID ownerId) {
        userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        ProjectModel project = ProjectModel.builder()
                .name(request.getName())
                .description(request.getDescription())
                .ownerId(ownerId)
                .build();

        project = projectRepository.save(project);
        log.info("Created project '{}' for user {}", project.getName(), ownerId);
        return project;
    }

    @Transactional(readOnly = true)
    public List<ProjectModel> getProjectsByOwner(UUID ownerId) {
        return projectRepository.findByOwnerIdAndDeletedAtIsNull(ownerId);
    }

    @Transactional(readOnly = true)
    public ProjectModel getById(UUID projectId) {
        return projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional
    public ProjectModel update(UUID projectId, UpdateProjectRequest request) {
        ProjectModel project = getById(projectId);
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        log.info("Updated project '{}'", project.getId());
        return projectRepository.save(project);
    }

    @Transactional
    public void softDelete(UUID projectId) {
        ProjectModel project = getById(projectId);
        project.softDelete();
        projectRepository.save(project);
        log.info("Soft-deleted project '{}'", projectId);
    }
}
