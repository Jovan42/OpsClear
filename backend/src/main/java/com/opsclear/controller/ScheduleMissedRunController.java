package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.JobResponse;
import com.opsclear.dto.ScheduleMissedRunResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.RecurringScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/schedules/{scheduleId}/missed-runs")
@RequiredArgsConstructor
public class ScheduleMissedRunController {

    private final RecurringScheduleService recurringScheduleService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @GetMapping
    public ResponseEntity<List<ScheduleMissedRunResponse>> list(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.ok(recurringScheduleService.listMissedRuns(pid, scheduleId, callerId));
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @PostMapping("/{missedRunId}/materialize")
    public ResponseEntity<JobResponse> materialize(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            @PathVariable UUID missedRunId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        JobResponse job = recurringScheduleService.materializeMissedRun(pid, scheduleId, missedRunId, callerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @DeleteMapping("/{missedRunId}")
    public ResponseEntity<Void> dismiss(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            @PathVariable UUID missedRunId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        recurringScheduleService.dismissMissedRun(pid, scheduleId, missedRunId, callerId);
        return ResponseEntity.noContent().build();
    }

    @RequiresAddon(AddonCode.RECURRING_SCHEDULING)
    @DeleteMapping
    public ResponseEntity<Void> dismissAll(
            @PathVariable String projectId,
            @PathVariable UUID scheduleId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        recurringScheduleService.dismissAllMissedRuns(pid, scheduleId, callerId);
        return ResponseEntity.noContent().build();
    }
}
