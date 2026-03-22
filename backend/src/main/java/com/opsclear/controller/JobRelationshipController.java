package com.opsclear.controller;

import com.opsclear.dto.CreateJobRelationshipRequest;
import com.opsclear.dto.JobRelationshipResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.JobRelationshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/jobs/{jobId}/relationships")
@RequiredArgsConstructor
public class JobRelationshipController {

    private final JobRelationshipService jobRelationshipService;

    @PostMapping
    public ResponseEntity<JobRelationshipResponse> create(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @Valid @RequestBody CreateJobRelationshipRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        JobRelationshipResponse response = JobRelationshipResponse.from(
                jobRelationshipService.create(projectId, jobId, request.getTargetJobId(),
                        request.getType(), userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @PathVariable UUID relationshipId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        jobRelationshipService.delete(projectId, jobId, relationshipId, userId);
        return ResponseEntity.noContent().build();
    }
}
