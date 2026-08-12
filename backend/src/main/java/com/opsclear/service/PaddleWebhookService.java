package com.opsclear.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.paddle.PaddleWebhookEvent;
import com.opsclear.repository.OrgSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/**
 * Handles {@code POST /api/webhooks/paddle} (ADR-0044). Signature verification per
 * Paddle's documented scheme: header {@code Paddle-Signature: ts=<unix>;h1=<hex>},
 * where {@code h1 = HMAC-SHA256(secret, "<ts>:<raw body>")}. Verified against the
 * exact raw request body — Paddle explicitly warns against re-serializing it before
 * checking, since that can silently change the bytes being signed.
 *
 * <p>Paddle's {@code transaction.payment_failed} event carries no field
 * distinguishing an individual retry attempt from the terminal failure after
 * retries are exhausted (confirmed against Paddle's current docs) — so this service
 * doesn't act on transaction events at all. Instead, {@code subscription.*} events
 * (created/activated/updated/canceled/past_due) are the sole source of truth for
 * {@code subscription_status}: each one carries the subscription's full current
 * state, and Paddle itself is what decides when dunning has actually reached a
 * terminal outcome before sending {@code past_due} or {@code canceled} — this
 * service just mirrors whatever Paddle reports, never infers it.
 *
 * <p>No explicit "already processed this event_id" ledger: every write here is a
 * plain field overwrite (UPDATE, not INSERT), so processing the same event twice
 * produces the same end state either way — naturally idempotent by construction,
 * not by tracking.
 */
@Service
@Slf4j
public class PaddleWebhookService {

    private static final Set<String> SUBSCRIPTION_EVENT_TYPES = Set.of(
            "subscription.created",
            "subscription.activated",
            "subscription.updated",
            "subscription.canceled",
            "subscription.past_due");

    // paused has no equivalent in our subscription_status CHECK (ACTIVE/PAST_DUE/
    // CANCELED only, see V032) - a paused Paddle subscription still means "this org
    // isn't actively paying", so it maps to the same restriction as CANCELED rather
    // than needing a fourth local status.
    private static final Map<String, String> PADDLE_STATUS_TO_LOCAL = Map.of(
            "active", "ACTIVE",
            "trialing", "ACTIVE",
            "past_due", "PAST_DUE",
            "canceled", "CANCELED",
            "paused", "CANCELED");

    private final ObjectMapper objectMapper;
    private final OrgSubscriptionRepository orgSubscriptionRepository;
    private final String webhookSecret;

    public PaddleWebhookService(ObjectMapper objectMapper,
                                 OrgSubscriptionRepository orgSubscriptionRepository,
                                 @Value("${paddle.webhook-secret}") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.orgSubscriptionRepository = orgSubscriptionRepository;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public void handle(String signatureHeader, String rawBody) {
        requireValidSignature(signatureHeader, rawBody);
        PaddleWebhookEvent event = parse(rawBody);

        if (!SUBSCRIPTION_EVENT_TYPES.contains(event.eventType())) {
            log.info("Ignoring Paddle event {} of type {} — not a subscription status event",
                    event.eventId(), event.eventType());
            return;
        }

        String localStatus = PADDLE_STATUS_TO_LOCAL.get(event.data().status());
        if (localStatus == null) {
            log.warn("Paddle event {} ({}) has unrecognized subscription status '{}' — ignoring",
                    event.eventId(), event.eventType(), event.data().status());
            return;
        }

        int rows = orgSubscriptionRepository.updateFromPaddleWebhook(
                event.data().customerId(), event.data().id(), localStatus);
        if (rows == 0) {
            log.warn("Paddle event {} ({}) references customer {} — no matching org_subscriptions row",
                    event.eventId(), event.eventType(), event.data().customerId());
        } else {
            log.info("Paddle event {} ({}) synced subscription_status={} for customer {}",
                    event.eventId(), event.eventType(), localStatus, event.data().customerId());
        }
    }

    // --- Guards ---

    private void requireValidSignature(String signatureHeader, String rawBody) {
        if (signatureHeader == null) {
            throw new ForbiddenException(ErrorMessages.Paddle.INVALID_WEBHOOK_SIGNATURE);
        }

        String timestamp = null;
        String expectedHash = null;
        for (String part : signatureHeader.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("ts".equals(kv[0])) {
                timestamp = kv[1];
            } else if ("h1".equals(kv[0])) {
                expectedHash = kv[1];
            }
        }

        if (timestamp == null || expectedHash == null || !computedHashMatches(timestamp, rawBody, expectedHash)) {
            throw new ForbiddenException(ErrorMessages.Paddle.INVALID_WEBHOOK_SIGNATURE);
        }
    }

    private boolean computedHashMatches(String timestamp, String rawBody, String expectedHash) {
        String signedPayload = timestamp + ":" + rawBody;
        String computedHash = hmacSha256Hex(signedPayload, webhookSecret);
        return MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is a JDK-guaranteed algorithm and webhookSecret is always a
            // non-null String, so this branch is unreachable in practice - wrapping
            // rather than declaring a checked exception through every caller.
            throw new IllegalStateException("Unable to compute HMAC-SHA256", e);
        }
    }

    private PaddleWebhookEvent parse(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaddleWebhookEvent.class);
        } catch (Exception e) {
            throw new ForbiddenException(ErrorMessages.Paddle.INVALID_WEBHOOK_SIGNATURE);
        }
    }
}
