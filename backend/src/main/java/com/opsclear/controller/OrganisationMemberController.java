package com.opsclear.controller;

import com.opsclear.dto.AddOrgMemberRequest;
import com.opsclear.dto.OrgMemberResponse;
import com.opsclear.dto.UpdateOrgMemberRoleRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.OrganisationMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/organisations/{orgId}/members")
@RequiredArgsConstructor
public class OrganisationMemberController {

    private final OrganisationMemberService organisationMemberService;

    @GetMapping
    public ResponseEntity<List<OrgMemberResponse>> listMembers(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        List<OrgMemberResponse> members = organisationMemberService.listMembers(orgId, callerId)
                .stream()
                .map(OrgMemberResponse::from)
                .toList();
        return ResponseEntity.ok(members);
    }

    @PostMapping
    public ResponseEntity<OrgMemberResponse> addMember(
            @PathVariable UUID orgId,
            @Valid @RequestBody AddOrgMemberRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrgMemberResponse.from(organisationMemberService.addMember(orgId, callerId, request)));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<OrgMemberResponse> updateRole(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateOrgMemberRoleRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(
                OrgMemberResponse.from(organisationMemberService.updateRole(orgId, callerId, userId, request)));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID orgId,
            @PathVariable UUID userId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        organisationMemberService.removeMember(orgId, callerId, userId);
        return ResponseEntity.noContent().build();
    }
}
