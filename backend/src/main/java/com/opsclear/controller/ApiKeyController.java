package com.opsclear.controller;

import com.opsclear.dto.ApiKeyResponse;
import com.opsclear.dto.CreateApiKeyRequest;
import com.opsclear.dto.CreateApiKeyResponse;
import com.opsclear.service.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<CreateApiKeyResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateApiKeyRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        CreateApiKeyResponse response = apiKeyService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> list(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(apiKeyService.list(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(jwt.getSubject());
        apiKeyService.revoke(userId, id);
        return ResponseEntity.noContent().build();
    }
}
