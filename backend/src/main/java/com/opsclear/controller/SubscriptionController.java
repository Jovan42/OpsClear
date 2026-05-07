package com.opsclear.controller;

import com.opsclear.dto.OrgSubscriptionResponse;
import com.opsclear.dto.UpsertSubscriptionRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/{orgId}/subscription")
    public ResponseEntity<OrgSubscriptionResponse> getSubscription(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(subscriptionService.getSubscription(orgId, callerId));
    }

    @PutMapping("/{orgId}/subscription")
    public ResponseEntity<OrgSubscriptionResponse> upsertSubscription(
            @PathVariable UUID orgId,
            @Valid @RequestBody UpsertSubscriptionRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(subscriptionService.upsertSubscription(orgId, callerId, request));
    }
}
