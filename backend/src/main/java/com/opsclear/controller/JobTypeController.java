package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.CreateJobTypeRequest;
import com.opsclear.dto.JobTypeResponse;
import com.opsclear.dto.UpdateJobTypeRequest;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.JobTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.opsclear.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/job-types")
@RequiredArgsConstructor
public class JobTypeController {

    private final JobTypeService jobTypeService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.JOB_TYPES)
    @GetMapping
    public ResponseEntity<List<JobTypeResponse>> list(
            @PathVariable String projectId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        List<JobTypeResponse> types = jobTypeService.list(pid, userId)
                .stream()
                .map(JobTypeResponse::from)
                .toList();
        return ResponseEntity.ok(types);
    }

    @RequiresAddon(AddonCode.JOB_TYPES)
    @PostMapping
    public ResponseEntity<JobTypeResponse> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateJobTypeRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobTypeResponse.from(jobTypeService.create(pid, request, userId)));
    }

    @RequiresAddon(AddonCode.JOB_TYPES)
    @PutMapping("/{typeId}")
    public ResponseEntity<JobTypeResponse> update(
            @PathVariable String projectId,
            @PathVariable UUID typeId,
            @Valid @RequestBody UpdateJobTypeRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        return ResponseEntity.ok(
                JobTypeResponse.from(jobTypeService.update(pid, typeId, request, userId)));
    }

    @RequiresAddon(AddonCode.JOB_TYPES)
    @DeleteMapping("/{typeId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable UUID typeId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        jobTypeService.delete(pid, typeId, userId);
        return ResponseEntity.noContent().build();
    }
}
