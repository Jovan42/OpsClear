package com.opsclear.controller;

import com.opsclear.dto.UpdateUserPreferencesRequest;
import com.opsclear.dto.UserPreferencesResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.UserPreferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The caller's server-synced UI preferences (ADR-0061), stored opaquely under {@code users.preferences.ui}. Core
 * feature, not add-on gated; only ever the caller's own preferences.
 */
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    @GetMapping
    public ResponseEntity<UserPreferencesResponse> get(Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(UserPreferencesResponse.from(userPreferencesService.get(callerId)));
    }

    @PatchMapping
    public ResponseEntity<UserPreferencesResponse> update(
            @Valid @RequestBody UpdateUserPreferencesRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(UserPreferencesResponse.from(
                userPreferencesService.update(callerId, request.getUi())));
    }
}
