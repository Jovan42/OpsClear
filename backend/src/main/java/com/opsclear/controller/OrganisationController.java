package com.opsclear.controller;

import com.opsclear.dto.CreateOrganisationRequest;
import com.opsclear.dto.OrganisationResponse;
import com.opsclear.dto.UpdateOrganisationRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.OrganisationService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/organisations")
@RequiredArgsConstructor
public class OrganisationController {

    private final OrganisationService organisationService;

    @PostMapping
    public ResponseEntity<OrganisationResponse> create(
            @Valid @RequestBody CreateOrganisationRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrganisationResponse.from(organisationService.create(request, callerId)));
    }

    @GetMapping("/mine")
    public ResponseEntity<OrganisationResponse> getMyOrganisation(Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return organisationService.getMyOrganisation(callerId)
                .map(org -> ResponseEntity.ok(OrganisationResponse.from(org)))
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrganisationResponse> getById(
            @PathVariable UUID id,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(OrganisationResponse.from(organisationService.getById(id, callerId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<OrganisationResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrganisationRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        return ResponseEntity.ok(OrganisationResponse.from(organisationService.update(id, request, callerId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        organisationService.softDelete(id, callerId);
        return ResponseEntity.noContent().build();
    }
}
