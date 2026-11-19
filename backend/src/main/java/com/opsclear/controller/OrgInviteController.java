package com.opsclear.controller;

import com.opsclear.dto.OrgInviteResponse;
import com.opsclear.dto.PublicInviteResponse;
import com.opsclear.dto.ResendInviteRequest;
import com.opsclear.dto.SendInviteRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.OrgInviteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrgInviteController {

    private final OrgInviteService orgInviteService;

    @PostMapping("/api/organisations/{orgId}/invites")
    public ResponseEntity<OrgInviteResponse> sendInvite(
            @PathVariable UUID orgId,
            @Valid @RequestBody SendInviteRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrgInviteResponse.from(orgInviteService.sendInvite(orgId, callerId, request)));
    }

    @GetMapping("/api/organisations/{orgId}/invites")
    public ResponseEntity<List<OrgInviteResponse>> listInvites(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        List<OrgInviteResponse> invites = orgInviteService.listPendingInvites(orgId, callerId)
                .stream()
                .map(OrgInviteResponse::from)
                .toList();
        return ResponseEntity.ok(invites);
    }

    @DeleteMapping("/api/organisations/{orgId}/invites/{inviteId}")
    public ResponseEntity<Void> revokeInvite(
            @PathVariable UUID orgId,
            @PathVariable UUID inviteId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        orgInviteService.revokeInvite(orgId, callerId, inviteId);
        return ResponseEntity.noContent().build();
    }

    // The body is optional: no body means the email goes out in English
    @PostMapping("/api/organisations/{orgId}/invites/{inviteId}/resend")
    public ResponseEntity<OrgInviteResponse> resendInvite(
            @PathVariable UUID orgId,
            @PathVariable UUID inviteId,
            @Valid @RequestBody(required = false) ResendInviteRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        String language = request != null ? request.getLanguage() : null;
        return ResponseEntity.ok(OrgInviteResponse.from(orgInviteService.resend(orgId, inviteId, language, callerId)));
    }

    // Public: SecurityConfig permits this GET without auth, the token is the secret (JOB-398)
    @GetMapping("/api/invites/{token}")
    public ResponseEntity<PublicInviteResponse> getPublicInvite(@PathVariable String token) {
        return ResponseEntity.ok(orgInviteService.getPublicInvite(token));
    }

    @PostMapping("/api/invites/{token}/accept")
    public ResponseEntity<OrgInviteResponse> acceptInvite(
            @PathVariable String token,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(OrgInviteResponse.from(orgInviteService.acceptInvite(token, callerId)));
    }
}
