package com.opsclear.controller;

import com.opsclear.dto.BlockReasonResponse;
import com.opsclear.service.BlockReasonService;
import com.opsclear.service.FriendlyIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.opsclear.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/block-reasons")
@RequiredArgsConstructor
public class BlockReasonController {

    private final BlockReasonService blockReasonService;
    private final FriendlyIdResolver friendlyIdResolver;

    @GetMapping
    public ResponseEntity<List<BlockReasonResponse>> list(
            @PathVariable String projectId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        List<BlockReasonResponse> reasons = blockReasonService.listActive(pid, userId)
                .stream()
                .map(BlockReasonResponse::from)
                .toList();
        return ResponseEntity.ok(reasons);
    }

    @DeleteMapping("/{reasonId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable UUID reasonId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        blockReasonService.softDelete(pid, reasonId, userId);
        return ResponseEntity.noContent().build();
    }
}
