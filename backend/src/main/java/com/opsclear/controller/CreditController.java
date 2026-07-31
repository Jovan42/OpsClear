package com.opsclear.controller;

import com.opsclear.dto.CreditBalanceResponse;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organisations/{orgId}/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @GetMapping("/balance")
    public ResponseEntity<CreditBalanceResponse> getBalance(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        int balance = creditService.getBalance(orgId, callerId);
        return ResponseEntity.ok(CreditBalanceResponse.builder().orgId(orgId).balance(balance).build());
    }
}
