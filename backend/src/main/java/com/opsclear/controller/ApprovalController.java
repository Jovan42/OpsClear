package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.ApprovalResponse;
import com.opsclear.dto.DecideApprovalRequest;
import com.opsclear.dto.RequestApprovalRequest;
import com.opsclear.model.ApprovalModel;
import com.opsclear.service.ApprovalService;
import com.opsclear.service.FriendlyIdResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.opsclear.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.APPROVALS)
    @PostMapping("/api/projects/{projectId}/jobs/{jobId}/approvals")
    public ResponseEntity<ApprovalResponse> request(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @Valid @RequestBody RequestApprovalRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, callerId);
        ApprovalModel approval = approvalService.request(pid, jid, request.getDescription(), callerId);
        return ResponseEntity.status(201).body(ApprovalResponse.from(approval));
    }

    @RequiresAddon(AddonCode.APPROVALS)
    @PatchMapping("/api/projects/{projectId}/jobs/{jobId}/approvals/{approvalId}/status")
    public ResponseEntity<ApprovalResponse> decide(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @PathVariable UUID approvalId,
            @Valid @RequestBody DecideApprovalRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, callerId);
        ApprovalModel approval = approvalService.decide(
                pid, jid, approvalId, request.getStatus(), request.getComment(), callerId);
        return ResponseEntity.ok(ApprovalResponse.from(approval));
    }

    @RequiresAddon(AddonCode.APPROVALS)
    @GetMapping("/api/projects/{projectId}/jobs/{jobId}/approvals")
    public ResponseEntity<List<ApprovalResponse>> listByJob(
            @PathVariable String projectId,
            @PathVariable String jobId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        UUID jid = friendlyIdResolver.resolveJob(jobId, callerId);
        List<ApprovalResponse> approvals = approvalService.listByJob(pid, jid, callerId)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
        return ResponseEntity.ok(approvals);
    }

    @RequiresAddon(AddonCode.APPROVALS)
    @GetMapping("/api/projects/{projectId}/approvals/pending")
    public ResponseEntity<List<ApprovalResponse>> listPendingByProject(
            @PathVariable String projectId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        List<ApprovalResponse> approvals = approvalService.listPendingByProject(pid, callerId)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
        return ResponseEntity.ok(approvals);
    }
}
