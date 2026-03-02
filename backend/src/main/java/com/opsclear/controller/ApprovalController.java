package com.opsclear.controller;

import com.opsclear.dto.ApprovalResponse;
import com.opsclear.dto.DecideApprovalRequest;
import com.opsclear.dto.RequestApprovalRequest;
import com.opsclear.model.ApprovalModel;
import com.opsclear.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    @PostMapping("/api/projects/{projectId}/jobs/{jobId}/approvals")
    public ResponseEntity<ApprovalResponse> request(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @Valid @RequestBody RequestApprovalRequest request,
            JwtAuthenticationToken auth) {
        UUID callerId = UUID.fromString(auth.getToken().getSubject());
        ApprovalModel approval = approvalService.request(projectId, jobId, request.getDescription(), callerId);
        return ResponseEntity.status(201).body(ApprovalResponse.from(approval));
    }

    @PatchMapping("/api/projects/{projectId}/jobs/{jobId}/approvals/{approvalId}/status")
    public ResponseEntity<ApprovalResponse> decide(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            @PathVariable UUID approvalId,
            @Valid @RequestBody DecideApprovalRequest request,
            JwtAuthenticationToken auth) {
        UUID callerId = UUID.fromString(auth.getToken().getSubject());
        ApprovalModel approval = approvalService.decide(
                projectId, jobId, approvalId, request.getStatus(), request.getComment(), callerId);
        return ResponseEntity.ok(ApprovalResponse.from(approval));
    }

    @GetMapping("/api/projects/{projectId}/jobs/{jobId}/approvals")
    public ResponseEntity<List<ApprovalResponse>> listByJob(
            @PathVariable UUID projectId,
            @PathVariable UUID jobId,
            JwtAuthenticationToken auth) {
        UUID callerId = UUID.fromString(auth.getToken().getSubject());
        List<ApprovalResponse> approvals = approvalService.listByJob(projectId, jobId, callerId)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
        return ResponseEntity.ok(approvals);
    }

    @GetMapping("/api/projects/{projectId}/approvals/pending")
    public ResponseEntity<List<ApprovalResponse>> listPendingByProject(
            @PathVariable UUID projectId,
            JwtAuthenticationToken auth) {
        UUID callerId = UUID.fromString(auth.getToken().getSubject());
        List<ApprovalResponse> approvals = approvalService.listPendingByProject(projectId, callerId)
                .stream()
                .map(ApprovalResponse::from)
                .toList();
        return ResponseEntity.ok(approvals);
    }
}
