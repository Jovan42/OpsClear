package com.opsclear.controller;

import com.opsclear.dto.EmailSignupRequest;
import com.opsclear.dto.SignupStatusResponse;
import com.opsclear.service.EmailSignupService;
import com.opsclear.service.OrganisationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, no-auth sign-up endpoints (ADR-0066). nginx rate-limits {@code POST /api/public/signups}. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicSignupController {

    private final EmailSignupService emailSignupService;
    private final OrganisationService organisationService;

    /** 204 for a new, duplicate or honeypot sign-up alike, so the response never reveals who is on the list. */
    @PostMapping("/signups")
    public ResponseEntity<Void> signUp(@Valid @RequestBody EmailSignupRequest request) {
        emailSignupService.signUp(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/signup")
    public ResponseEntity<SignupStatusResponse> status() {
        return ResponseEntity.ok(new SignupStatusResponse(organisationService.isSignupOpen()));
    }
}
