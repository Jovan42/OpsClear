package com.opsclear.controller;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.CreateLinkRequest;
import com.opsclear.dto.LinkResponse;
import com.opsclear.dto.UpdateLinkRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.FriendlyIdResolver;
import com.opsclear.service.LinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/links")
@RequiredArgsConstructor
public class ProjectLinkController {

    private final LinkService linkService;
    private final FriendlyIdResolver friendlyIdResolver;

    @RequiresAddon(AddonCode.JOB_LINKS)
    @PostMapping
    public ResponseEntity<LinkResponse> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateLinkRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        LinkResponse response = LinkResponse.from(
                linkService.createForProject(pid, request.getUrl(), request.getLabel(), userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @RequiresAddon(AddonCode.JOB_LINKS)
    @PutMapping("/{linkId}")
    public ResponseEntity<LinkResponse> update(
            @PathVariable String projectId,
            @PathVariable UUID linkId,
            @Valid @RequestBody UpdateLinkRequest request,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        LinkResponse response = LinkResponse.from(
                linkService.updateProjectLink(pid, linkId, request.getUrl(), request.getLabel(), userId));
        return ResponseEntity.ok(response);
    }

    @RequiresAddon(AddonCode.JOB_LINKS)
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> delete(
            @PathVariable String projectId,
            @PathVariable UUID linkId,
            Authentication auth) {
        UUID userId = SecurityUtils.resolveUserId(auth);
        UUID pid = friendlyIdResolver.resolveProject(projectId, userId);
        linkService.deleteProjectLink(pid, linkId, userId);
        return ResponseEntity.noContent().build();
    }
}
