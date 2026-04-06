package com.opsclear.controller;

import com.opsclear.dto.JobHistoryEntryResponse;
import com.opsclear.security.SecurityUtils;
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

    @GetMapping("/api/projects/{projectId}/jobs/{jobId}/history")
    public ResponseEntity<List<JobHistoryEntryResponse>> getHistory(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        List<JobHistoryEntryResponse> history = jobHistoryService.getHistory(projectId, jobId, callerId)
                .stream()
                .map(JobHistoryEntryResponse::from)
                .toList();
        return ResponseEntity.ok(history);
    }
}
