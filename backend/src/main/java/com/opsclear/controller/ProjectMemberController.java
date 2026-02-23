package com.opsclear.controller;

import com.opsclear.dto.AddMemberRequest;
import com.opsclear.dto.ProjectMemberResponse;
import com.opsclear.dto.UpdateMemberRoleRequest;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.service.ProjectMemberService;
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
@RequestMapping("/api/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    @PostMapping
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddMemberRequest request,
            JwtAuthenticationToken auth) {
        UUID requesterId = UUID.fromString(auth.getToken().getSubject());
        ProjectMemberModel member = projectMemberService.addMember(projectId, requesterId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectMemberResponse.from(member));
    }

    @GetMapping
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(
            @PathVariable UUID projectId,
            JwtAuthenticationToken auth) {
        UUID requesterId = UUID.fromString(auth.getToken().getSubject());
        List<ProjectMemberResponse> members = projectMemberService.listMembers(projectId, requesterId)
                .stream()
                .map(ProjectMemberResponse::from)
                .toList();
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<ProjectMemberResponse> updateRole(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            JwtAuthenticationToken auth) {
        UUID requesterId = UUID.fromString(auth.getToken().getSubject());
        ProjectMemberModel member = projectMemberService.updateRole(
                projectId, requesterId, memberId, request.getRole());
        return ResponseEntity.ok(ProjectMemberResponse.from(member));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID memberId,
            JwtAuthenticationToken auth) {
        UUID requesterId = UUID.fromString(auth.getToken().getSubject());
        projectMemberService.removeMember(projectId, requesterId, memberId);
        return ResponseEntity.noContent().build();
    }
}
