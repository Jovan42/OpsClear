package com.opsclear.controller;

import com.opsclear.dto.CreateMilestoneRequest;
import com.opsclear.dto.MilestoneResponse;
import com.opsclear.dto.UpdateMilestoneRequest;
import com.opsclear.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    @GetMapping
    public ResponseEntity<List<MilestoneResponse>> list(
            @PathVariable UUID projectId,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        List<MilestoneResponse> milestones = milestoneService.list(projectId, userId)
                .stream()
                .map(MilestoneResponse::from)
                .toList();
        return ResponseEntity.ok(milestones);
    }

    @PostMapping
    public ResponseEntity<MilestoneResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMilestoneRequest request,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MilestoneResponse.from(milestoneService.create(projectId, request, userId)));
    }

    @PutMapping("/{milestoneId}")
    public ResponseEntity<MilestoneResponse> update(
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            @Valid @RequestBody UpdateMilestoneRequest request,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        return ResponseEntity.ok(
                MilestoneResponse.from(milestoneService.update(projectId, milestoneId, request, userId)));
    }

    @DeleteMapping("/{milestoneId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID milestoneId,
            JwtAuthenticationToken auth) {
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        milestoneService.softDelete(projectId, milestoneId, userId);
        return ResponseEntity.noContent().build();
    }
}
