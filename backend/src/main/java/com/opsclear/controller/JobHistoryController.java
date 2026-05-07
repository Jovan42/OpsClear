package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.JobHistoryEntryResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.JobHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class JobHistoryController {

    private final JobHistoryService jobHistoryService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.JOB_STATUS_HISTORY)
    @GetMapping("/api/projects/{projectId}/jobs/{jobId}/history")
    public ResponseEntity<List<JobHistoryEntryResponse>> getHistory(
            @PathVariable String projectId,
            @PathVariable String jobId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, callerId);
        List<JobHistoryEntryResponse> history = jobHistoryService.getHistory(pid, jid, callerId)
                .stream()
                .map(JobHistoryEntryResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}
