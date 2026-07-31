package com.opsclear.controller;

import com.opsclear.aop.RequiresSuperUser;
import com.opsclear.dto.CreditLedgerEntryResponse;
import com.opsclear.dto.GrantCreditRequest;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.CreditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
public class SuperAdminCreditController {

    private final CreditService creditService;

    @RequiresSuperUser
    @PostMapping("/credits/grant")
    public ResponseEntity<CreditLedgerEntryResponse> grant(
            @Valid @RequestBody GrantCreditRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        CreditLedgerEntryResponse response =
                CreditLedgerEntryResponse.from(creditService.grant(callerId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @RequiresSuperUser
    @GetMapping("/organisations/{orgId}/credits")
    public ResponseEntity<List<CreditLedgerEntryResponse>> getLedger(@PathVariable UUID orgId) {
        List<CreditLedgerEntryResponse> ledger = creditService.getLedger(orgId)
                .stream()
                .map(CreditLedgerEntryResponse::from)
                .toList();
        return ResponseEntity.ok(ledger);
    }
}
