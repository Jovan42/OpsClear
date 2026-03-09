package com.opsclear.controller;

import com.opsclear.dto.DashboardResponse;
import com.opsclear.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/api/projects/{projectId}/dashboard")
    public ResponseEntity<DashboardResponse> get(
            @PathVariable UUID projectId,
            JwtAuthenticationToken auth) {
        UUID callerId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(dashboardService.get(projectId, callerId));
    }
}
