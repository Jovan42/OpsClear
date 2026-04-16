package com.opsclear.controller;

import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.JobResponse;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.dto.UpdateJobStatusRequest;
import com.opsclear.service.FriendlyIdResolver;
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
    private final FriendlyIdResolver friendlyIdResolver;

    @PostMapping
    public ResponseEntity<JobResponse> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateJobRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobResponse.from(jobService.create(pid, request, userId)));
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) JobPriority priority,
            @RequestParam(required = false) UUID milestoneId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        List<JobResponse> jobs = jobService.list(pid, userId, q, priority, milestoneId)
                .stream()
                .map(JobResponse::from)
                .toList();
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getById(
            @PathVariable String projectId,
            @PathVariable String jobId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, userId);
        return ResponseEntity.ok(JobResponse.from(jobService.getById(pid, jid, userId)));
    }

    @PutMapping("/{jobId}")
    public ResponseEntity<JobResponse> update(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @Valid @RequestBody UpdateJobRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, userId);
        return ResponseEntity.ok(JobResponse.from(jobService.update(pid, jid, request, userId)));
    }

    @PatchMapping("/{jobId}/status")
    public ResponseEntity<JobResponse> updateStatus(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @Valid @RequestBody UpdateJobStatusRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, userId);
        return ResponseEntity.ok(JobResponse.from(
                jobService.updateStatus(pid, jid, request.getStatus(), request.getReason(), userId)));
    }

    @DeleteMapping("/{jobId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable String jobId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, userId);
        jobService.softDelete(pid, jid, userId);
        return ResponseEntity.noContent().build();
    }
}
