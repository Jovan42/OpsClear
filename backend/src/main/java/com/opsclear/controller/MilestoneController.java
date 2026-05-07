package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.CreateMilestoneRequest;
import com.opsclear.dto.MilestoneResponse;
import com.opsclear.dto.UpdateMilestoneRequest;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.MilestoneService;
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
@RequestMapping("/api/projects/{projectId}/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.MILESTONES)
    @GetMapping
    public ResponseEntity<List<MilestoneResponse>> list(
            @PathVariable String projectId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        List<MilestoneResponse> milestones = milestoneService.list(pid, userId)
                .stream()
                .map(MilestoneResponse::from)
                .toList();
        return ResponseEntity.ok(milestones);
    }

    @RequiresAddon(AddonCode.MILESTONES)
    @PostMapping
    public ResponseEntity<MilestoneResponse> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateMilestoneRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MilestoneResponse.from(milestoneService.create(pid, request, userId)));
    }

    @RequiresAddon(AddonCode.MILESTONES)
    @PutMapping("/{milestoneId}")
    public ResponseEntity<MilestoneResponse> update(
            @PathVariable String projectId,
            @PathVariable String milestoneId,
            @Valid @RequestBody UpdateMilestoneRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID mid = friendlyIdResolver.resolveMilestone(milestoneId, userId);
        return ResponseEntity.ok(
                MilestoneResponse.from(milestoneService.update(pid, mid, request, userId)));
    }

    @RequiresAddon(AddonCode.MILESTONES)
    @DeleteMapping("/{milestoneId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable String milestoneId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        UUID mid = friendlyIdResolver.resolveMilestone(milestoneId, userId);
        milestoneService.softDelete(pid, mid, userId);
        return ResponseEntity.noContent().build();
    }
}
