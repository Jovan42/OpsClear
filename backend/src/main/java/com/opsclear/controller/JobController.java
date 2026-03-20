package com.opsclear.controller;

import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.JobResponse;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.dto.UpdateJobStatusRequest;
import com.opsclear.service.JobService;
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
import com.opsclear.model.JobPriority;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateJobRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobResponse.from(jobService.create(projectId, request, userId)));
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> list(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) JobPriority priority,
            @RequestParam(required = false) UUID milestoneId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        List<JobResponse> jobs = jobService.list(projectId, userId, q, priority, milestoneId)
                .stream()
                .map(JobResponse::from)
                .toList();
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getById(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(JobResponse.from(jobService.getById(projectId, jobId, userId)));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<JobResponse> update(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @Valid @RequestBody UpdateJobRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(JobResponse.from(jobService.update(projectId, jobId, request, userId)));
    }

    @PatchMapping("/{jobId}/status")
    public ResponseEntity<JobResponse> updateStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @Valid @RequestBody UpdateJobStatusRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(JobResponse.from(
                jobService.updateStatus(projectId, jobId, request.getStatus(), request.getReason(), userId)));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        jobService.softDelete(projectId, jobId, userId);
        return ResponseEntity.noContent().build();
    }
}
