package com.opsclear.controller;

import com.opsclear.dto.ProjectDirectoryEntryResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organisations/{orgId}/projects")
@RequiredArgsConstructor
public class ProjectDirectoryController {

    private final ProjectService projectService;

    @GetMapping("/directory")
    public ResponseEntity<List<ProjectDirectoryEntryResponse>> getDirectory(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        List<ProjectDirectoryEntryResponse> directory = projectService.getDirectory(orgId, callerId).stream()
                .map(ProjectDirectoryEntryResponse::from)
                .toList();
        return ResponseEntity.ok(directory);
    }
}
