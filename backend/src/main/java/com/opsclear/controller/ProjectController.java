package com.opsclear.controller;

import com.opsclear.dto.CreateProjectRequest;
import com.opsclear.dto.ProjectResponse;
import com.opsclear.dto.UpdateProjectRequest;
import com.opsclear.dto.UpdateProjectStatusRequest;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.opsclear.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final FriendlyIdResolver friendlyIdResolver;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        ProjectModel project = projectService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectResponse.from(project));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> listMyProjects(
            @RequestParam(name = "status", required = false, defaultValue = "ACTIVE") String statusParam,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        ProjectStatus status = "ALL".equalsIgnoreCase(statusParam)
                ? null : ProjectStatus.valueOf(statusParam.toUpperCase());
        List<ProjectResponse> projects = projectService.getProjectsForMember(userId, status)
                .stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(projects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getById(
            @PathVariable String id,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID projectId = friendlyIdResolver.resolveProject(id, userId);
        ProjectModel project = projectService.getById(projectId, userId);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateProjectRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID projectId = friendlyIdResolver.resolveProject(id, userId);
        ProjectModel project = projectService.update(projectId, request, userId);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProjectResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateProjectStatusRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID projectId = friendlyIdResolver.resolveProject(id, userId);
        ProjectModel project = projectService.updateStatus(projectId, request.getStatus(), userId);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID projectId = friendlyIdResolver.resolveProject(id, userId);
        projectService.softDelete(projectId, userId);
        return ResponseEntity.noContent().build();
    }
}
