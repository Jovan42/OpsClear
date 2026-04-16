package com.opsclear.controller;

import com.opsclear.dto.AddMemberRequest;
import com.opsclear.dto.ProjectMemberResponse;
import com.opsclear.dto.UpdateMemberRoleRequest;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.opsclear.security.SecurityUtils;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final FriendlyIdResolver friendlyIdResolver;

    @PostMapping
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable String projectId,
            @Valid @RequestBody AddMemberRequest request,
            Authentication auth) {
        UUID requesterId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, requesterId);
        ProjectMemberModel member = projectMemberService.addMember(pid, requesterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectMemberResponse.from(member));
    }

    @GetMapping
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(
            @PathVariable String projectId,
            Authentication auth) {
        UUID requesterId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, requesterId);
        List<ProjectMemberResponse> members = projectMemberService.listMembers(pid, requesterId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<ProjectMemberResponse> updateRole(
            @PathVariable String projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            Authentication auth) {
        UUID requesterId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, requesterId);
        ProjectMemberModel member = projectMemberService.updateRole(
                pid, requesterId, memberId, request.getRole());
        return ResponseEntity.ok(ProjectMemberResponse.from(member));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String projectId,
            @PathVariable UUID memberId,
            Authentication auth) {
        UUID requesterId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, requesterId);
        projectMemberService.removeMember(pid, requesterId, memberId);
        return ResponseEntity.noContent().build();
    }
}
