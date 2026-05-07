package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.DashboardResponse;
import com.opsclear.service.DashboardService;
import com.opsclear.service.FriendlyIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.opsclear.security.SecurityUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.DASHBOARD)
    @GetMapping("/api/projects/{projectId}/dashboard")
    public ResponseEntity<DashboardResponse> get(
            @PathVariable String projectId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, callerId);
        return ResponseEntity.ok(dashboardService.get(pid, callerId));
    }
}
