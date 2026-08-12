package com.opsclear.controller;

import com.opsclear.service.PaddleWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Paddle's webhook deliveries (ADR-0044). Deliberately unauthenticated by
 * JWT/API-key — Paddle's own servers call this, not an OpsClear client — so the
 * request body is trusted only after {@link PaddleWebhookService} verifies the
 * {@code Paddle-Signature} header. The body is read as a raw {@code String} rather
 * than a parsed DTO so the exact bytes Paddle signed are what gets HMAC-verified;
 * parsing to JSON happens separately, after verification, inside the service.
 */
@RestController
@RequestMapping("/api/webhooks/paddle")
@RequiredArgsConstructor
public class PaddleWebhookController {

    private final PaddleWebhookService paddleWebhookService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "Paddle-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        paddleWebhookService.handle(signature, rawBody);
        return ResponseEntity.ok().build();
    }
}
