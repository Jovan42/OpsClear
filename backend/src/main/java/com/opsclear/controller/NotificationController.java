package com.opsclear.controller;

import com.opsclear.dto.MarkReadRequest;
import com.opsclear.dto.NotificationPageResponse;
import com.opsclear.dto.NotificationResponse;
import com.opsclear.dto.UnreadCountResponse;
import com.opsclear.model.NotificationType;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The caller's in-app notifications (ADR-0051). Core feature, not add-on gated.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationPageResponse> list(
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(NotificationPageResponse.from(
                notificationService.list(callerId, unread, type, page, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(UnreadCountResponse.from(notificationService.unreadCount(callerId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable UUID id,
            @Valid @RequestBody MarkReadRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(NotificationResponse.from(
                notificationService.markRead(callerId, id, request.getRead())));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        notificationService.markAllRead(callerId);
        return ResponseEntity.noContent().build();
    }
}
