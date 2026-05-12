package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.CreateScheduleRequest;
import com.opsclear.dto.PauseScheduleRequest;
import com.opsclear.dto.RecurringScheduleResponse;
import com.opsclear.dto.UpdateScheduleRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.RecurringScheduleService;
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
@RequestMapping("/api/projects/{projectId}/schedules")
@RequiredArgsConstructor
public class RecurringScheduleController {

    private final RecurringScheduleService recurringScheduleService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @GetMapping
    public ResponseEntity<List<RecurringScheduleResponse>> list(
            @PathVariable String projectId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.ok(recurringScheduleService.list(pid, callerId));
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @PostMapping
    public ResponseEntity<RecurringScheduleResponse> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateScheduleRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recurringScheduleService.create(pid, request, callerId));
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @GetMapping("/{scheduleId}")
    public ResponseEntity<RecurringScheduleResponse> get(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.ok(recurringScheduleService.get(pid, scheduleId, callerId));
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @PutMapping("/{scheduleId}")
    public ResponseEntity<RecurringScheduleResponse> update(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateScheduleRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.ok(recurringScheduleService.update(pid, scheduleId, request, callerId));
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        recurringScheduleService.delete(pid, scheduleId, callerId);
        return ResponseEntity.noContent().build();
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @PostMapping("/{scheduleId}/pause")
    public ResponseEntity<RecurringScheduleResponse> pause(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody PauseScheduleRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.ok(recurringScheduleService.pause(pid, scheduleId, request, callerId));
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @PostMapping("/{scheduleId}/resume")
    public ResponseEntity<RecurringScheduleResponse> resume(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.ok(recurringScheduleService.resume(pid, scheduleId, callerId));
    }
}
