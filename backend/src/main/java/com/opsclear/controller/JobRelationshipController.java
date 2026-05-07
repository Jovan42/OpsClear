package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.CreateJobRelationshipRequest;
import com.opsclear.dto.JobRelationshipResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FriendlyIdResolver;
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
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.JOB_RELATIONSHIPS)
    @PostMapping
    public ResponseEntity<JobRelationshipResponse> create(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @Valid @RequestBody CreateJobRelationshipRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, userId);
        JobRelationshipResponse response = JobRelationshipResponse.from(
                jobRelationshipService.create(pid, jid, request.getTargetJobId(),
                        request.getType(), userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @RequiresAddon(AddonCode.JOB_RELATIONSHIPS)
    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable UUID relationshipId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, userId);
        jobRelationshipService.delete(pid, jid, relationshipId, userId);
        return ResponseEntity.noContent().build();
    }
}
