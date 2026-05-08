package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.CreateJobTemplateRequest;
import com.opsclear.dto.JobTemplateResponse;
import com.opsclear.dto.UpdateJobTemplateRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.JobTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/projects/{projectId}/templates")
@RequiredArgsConstructor
public class JobTemplateController {

    private final JobTemplateService jobTemplateService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.JOB_TEMPLATES)
    @GetMapping
    public ResponseEntity<List<JobTemplateResponse>> list(
            @PathVariable String projectId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        List<JobTemplateResponse> templates = jobTemplateService.list(pid, userId)
                .stream()
                .map(JobTemplateResponse::from)
                .toList();
        return ResponseEntity.ok(templates);
    }

    @RequiresAddon(AddonCode.JOB_TEMPLATES)
    @PostMapping
    public ResponseEntity<JobTemplateResponse> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateJobTemplateRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JobTemplateResponse.from(jobTemplateService.create(pid, request, userId)));
    }

    @RequiresAddon(AddonCode.JOB_TEMPLATES)
    @PutMapping("/{templateId}")
    public ResponseEntity<JobTemplateResponse> update(
            @PathVariable String projectId,
            @PathVariable String templateId,
            @Valid @RequestBody UpdateJobTemplateRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID tid = friendlyIdResolver.resolveTemplate(templateId, userId);
        return ResponseEntity.ok(
                JobTemplateResponse.from(jobTemplateService.update(pid, tid, request, userId)));
    }

    @RequiresAddon(AddonCode.JOB_TEMPLATES)
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable String templateId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID tid = friendlyIdResolver.resolveTemplate(templateId, userId);
        jobTemplateService.softDelete(pid, tid, userId);
        return ResponseEntity.noContent().build();
    }

    @RequiresAddon(AddonCode.JOB_TEMPLATES)
    @PostMapping("/{templateId}/use")
    public ResponseEntity<Void> recordUsage(
            @PathVariable String projectId,
            @PathVariable String templateId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID tid = friendlyIdResolver.resolveTemplate(templateId, userId);
        jobTemplateService.recordUsage(pid, tid, userId);
        return ResponseEntity.ok().build();
    }
}
