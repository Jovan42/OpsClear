package com.opsclear.controller;

import com.opsclear.dto.OrgSettingsResponse;
import com.opsclear.dto.UpdateOrgSettingsRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.OrgSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organisations/{id}/settings")
@RequiredArgsConstructor
public class OrgSettingsController {

    private final OrgSettingsService orgSettingsService;

    @GetMapping
    public ResponseEntity<OrgSettingsResponse> get(
            @PathVariable UUID id,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(OrgSettingsResponse.from(orgSettingsService.get(id, callerId)));
    }

    @PatchMapping
    public ResponseEntity<OrgSettingsResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrgSettingsRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(OrgSettingsResponse.from(orgSettingsService.update(id, request, callerId)));
    }
}
