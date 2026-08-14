package com.opsclear.controller;

import com.opsclear.dto.InitiateSubscriptionResponse;
import com.opsclear.dto.PaddleSubscriptionResponse;
import com.opsclear.dto.UpdatePaddleSubscriptionRequest;
import com.opsclear.dto.UpdatePaymentMethodTransactionResponse;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.paddle.PaddleSubscription;
import com.opsclear.security.SecurityUtils;
import com.opsclear.service.PaddleSubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organisations/{orgId}/subscription/paddle")
@RequiredArgsConstructor
public class PaddleSubscriptionController {

    private final PaddleSubscriptionService paddleSubscriptionService;

    @PostMapping
    public ResponseEntity<InitiateSubscriptionResponse> initiate(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        InitiateSubscriptionResponse response =
                InitiateSubscriptionResponse.from(paddleSubscriptionService.initiate(orgId, callerId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    public ResponseEntity<PaddleSubscriptionResponse> update(
            @PathVariable UUID orgId,
            @Valid @RequestBody UpdatePaddleSubscriptionRequest request,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        PaddleSubscription updated = paddleSubscriptionService.updateSubscriptionItems(orgId, callerId, request);
        PaddleSubscriptionResponse response = PaddleSubscriptionResponse.builder()
                .orgId(orgId)
                .paddleSubscriptionId(updated.id())
                .status(updated.status())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel")
    public ResponseEntity<PaddleSubscriptionResponse> cancel(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        PaddleSubscription cancelled = paddleSubscriptionService.cancel(orgId, callerId);
        PaddleSubscriptionResponse response = PaddleSubscriptionResponse.builder()
                .orgId(orgId)
                .paddleSubscriptionId(cancelled.id())
                .status(cancelled.status())
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resume")
    public ResponseEntity<PaddleSubscriptionResponse> resume(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        OrgSubscriptionModel resumed = paddleSubscriptionService.resume(orgId, callerId);
        PaddleSubscriptionResponse response = PaddleSubscriptionResponse.builder()
                .orgId(orgId)
                .paddleSubscriptionId(resumed.getPaddleSubscriptionId())
                .status(resumed.getSubscriptionStatus())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/update-payment-method-transaction")
    public ResponseEntity<UpdatePaymentMethodTransactionResponse> getUpdatePaymentMethodTransaction(
            @PathVariable UUID orgId,
            Authentication auth) {
        UUID callerId = SecurityUtils.resolveUserId(auth);
        String transactionId = paddleSubscriptionService.getUpdatePaymentMethodTransactionId(orgId, callerId);
        return ResponseEntity.ok(UpdatePaymentMethodTransactionResponse.builder()
                .transactionId(transactionId)
                .build());
    }
}
