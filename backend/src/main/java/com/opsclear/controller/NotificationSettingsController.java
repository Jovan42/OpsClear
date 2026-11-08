package com.opsclear.controller;

import com.opsclear.dto.NotificationSettingsRequest;
import com.opsclear.dto.NotificationSettingsResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.NotificationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The caller's notification mutes (ADR-0051 §Muting). Core feature, not add-on gated.
 */
@RestController
@RequestMapping("/api/user/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping
    public ResponseEntity<NotificationSettingsResponse> get(Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(NotificationSettingsResponse.from(
                notificationSettingsService.getMutedTypes(callerId)));
    }

    @PutMapping
    public ResponseEntity<NotificationSettingsResponse> replace(
            @Valid @RequestBody NotificationSettingsRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(NotificationSettingsResponse.from(
                notificationSettingsService.replaceMutedTypes(callerId, request.getMuted())));
    }
}
