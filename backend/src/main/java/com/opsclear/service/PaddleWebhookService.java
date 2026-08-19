package com.opsclear.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleWebhookEvent;
import com.opsclear.paddle.PaddleWebhookScheduledChange;
import com.opsclear.paddle.PaddleWebhookSubscriptionItem;
import com.opsclear.paddle.PaddleWebhookTransactionEvent;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
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
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
 * doesn't act on that event. Instead, {@code subscription.*} events
 * (created/activated/updated/canceled/past_due) are the sole source of truth for
 * {@code subscription_status}: each one carries the subscription's full current
 * state, and Paddle itself is what decides when dunning has actually reached a
 * terminal outcome before sending {@code past_due} or {@code canceled} — this
 * service just mirrors whatever Paddle reports, never infers it.
 *
 * <p>{@code transaction.completed} is handled, though (JOB-180): a one-time credit
 * discount turned out to not actually be transaction-scoped in Paddle's own model —
 * confirmed via real sandbox data that it applies to every transaction within the
 * same billing period, not just the first, no matter how {@code recur}/
 * {@code maximum_recurring_intervals}/{@code usage_limit} are set. As soon as a
 * completed transaction reports it used a discount, this detaches that discount from
 * the subscription immediately, so no second transaction in the same period can
 * catch it too. Every subscription-level discount in this app comes from exactly one
 * mechanism ({@code CreditService}), so detaching unconditionally on any
 * discount-bearing completed transaction is safe — there's nothing else it could be.
 *
 * <p>JOB-200: every tier/add-on has a real price, so the org's very first
 * org_subscriptions row (tier_id is NOT NULL) is only ever created here, once
 * Paddle actually confirms a real subscription — never staged for free client-side.
 * The row's tier/add-ons are resolved from the webhook's own item price ids, never
 * trusted from the client. Subsequent events for an org that already has a row just
 * update it, same as before.
 *
 * <p>No explicit "already processed this event_id" ledger: every write here is a
 * plain field overwrite (UPDATE, not INSERT, once the row exists), so processing
 * the same event twice produces the same end state either way — naturally
 * idempotent by construction, not by tracking. A redelivered first-ever event is
 * likewise safe: the row created by the first delivery makes every redelivery take
 * the UPDATE path instead of inserting a duplicate.
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
    private static final String TRANSACTION_COMPLETED_EVENT_TYPE = "transaction.completed";

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

    private static final String SCHEDULED_CHANGE_ACTION_CANCEL = "cancel";
    private static final String BILLING_CYCLE_ANNUAL = "ANNUAL";
    private static final String BILLING_CYCLE_MONTHLY = "MONTHLY";

    private final ObjectMapper objectMapper;
    private final OrganisationRepository organisationRepository;
    private final OrgSubscriptionRepository orgSubscriptionRepository;
    private final SubscriptionTierRepository tierRepository;
    private final SubscriptionAddonRepository addonRepository;
    private final PaddleClient paddleClient;
    private final String webhookSecret;

    public PaddleWebhookService(ObjectMapper objectMapper,
                                 OrganisationRepository organisationRepository,
                                 OrgSubscriptionRepository orgSubscriptionRepository,
                                 SubscriptionTierRepository tierRepository,
                                 SubscriptionAddonRepository addonRepository,
                                 PaddleClient paddleClient,
                                 @Value("${paddle.webhook-secret}") String webhookSecret) {
        this.objectMapper = objectMapper;
        this.organisationRepository = organisationRepository;
        this.orgSubscriptionRepository = orgSubscriptionRepository;
        this.tierRepository = tierRepository;
        this.addonRepository = addonRepository;
        this.paddleClient = paddleClient;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public void handle(String signatureHeader, String rawBody) {
        requireValidSignature(signatureHeader, rawBody);
        PaddleWebhookEvent event = parse(rawBody);

        if (TRANSACTION_COMPLETED_EVENT_TYPE.equals(event.eventType())) {
            handleTransactionCompleted(rawBody, event.eventId());
            return;
        }

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

        Optional<UUID> orgId = organisationRepository.findIdByPaddleCustomerId(event.data().customerId());
        if (orgId.isEmpty()) {
            log.warn("Paddle event {} ({}) references customer {} — no matching organisation",
                    event.eventId(), event.eventType(), event.data().customerId());
            return;
        }

        Instant scheduledCancellationAt = scheduledCancellationAt(event);
        Instant currentPeriodStartsAt = currentPeriodStartsAt(event);

        Optional<OrgSubscriptionModel> existing = orgSubscriptionRepository.findByOrgId(orgId.get());
        if (existing.isEmpty()) {
            createFirstSubscriptionFromWebhook(
                    orgId.get(), event, localStatus, scheduledCancellationAt, currentPeriodStartsAt);
            return;
        }

        applyPendingDowngradeIfPeriodRolledOver(existing.get(), currentPeriodStartsAt);

        int rows = orgSubscriptionRepository.updateFromPaddleWebhook(
                orgId.get(), event.data().id(), localStatus, scheduledCancellationAt, currentPeriodStartsAt);
        if (rows == 0) {
            log.warn("Paddle event {} ({}) references org {} — no matching org_subscriptions row",
                    event.eventId(), event.eventType(), orgId.get());
        } else {
            log.info("Paddle event {} ({}) synced subscription_status={} for org {}",
                    event.eventId(), event.eventType(), localStatus, orgId.get());
        }
    }

    // Resolves Paddle's real subscription items back to our own tier/add-on
    // catalog — this is the org's very first successful checkout, so nothing here
    // is trusted from the client; Paddle's own confirmed item list is the sole
    // source of truth for what was actually paid for.
    private void createFirstSubscriptionFromWebhook(UUID orgId, PaddleWebhookEvent event, String localStatus,
            Instant scheduledCancellationAt, Instant currentPeriodStartsAt) {
        List<PaddleWebhookSubscriptionItem> items = event.data().items();
        if (items == null || items.isEmpty()) {
            log.warn("Paddle event {} ({}) has no items to resolve a plan from for org {} — ignoring",
                    event.eventId(), event.eventType(), orgId);
            return;
        }

        SubscriptionTierModel tier = null;
        String billingCycle = null;
        Set<UUID> addonIds = new HashSet<>();
        for (PaddleWebhookSubscriptionItem item : items) {
            String priceId = item.price().id();
            Optional<SubscriptionTierModel> matchedTier = tierRepository.findByPaddlePriceId(priceId);
            if (matchedTier.isPresent()) {
                tier = matchedTier.get();
                billingCycle = priceId.equals(tier.getPaddlePriceIdAnnual())
                        ? BILLING_CYCLE_ANNUAL : BILLING_CYCLE_MONTHLY;
                continue;
            }
            addonRepository.findByPaddlePriceId(priceId).ifPresent(addon -> addonIds.add(addon.getId()));
        }

        if (tier == null) {
            log.warn("Paddle event {} ({}) items didn't resolve to a known tier for org {} — ignoring",
                    event.eventId(), event.eventType(), orgId);
            return;
        }

        orgSubscriptionRepository.createFromPaddleWebhook(orgId, tier.getId(), billingCycle, addonIds,
                event.data().id(), localStatus, scheduledCancellationAt, currentPeriodStartsAt);
        log.info("Created org_subscriptions row for org {} from Paddle event {} ({}): tier={}, addons={}",
                orgId, event.eventId(), event.eventType(), tier.getId(), addonIds.size());
    }

    // Detaches a one-time credit discount the moment it's actually consumed — see the
    // class-level note on why this is needed and why detaching unconditionally is safe.
    private void handleTransactionCompleted(String rawBody, String eventId) {
        PaddleWebhookTransactionEvent txnEvent = parseTransactionEvent(rawBody);
        String discountId = txnEvent.data().discountId();
        String subscriptionId = txnEvent.data().subscriptionId();
        if (discountId == null || subscriptionId == null) {
            log.debug("Paddle event {} (transaction.completed) used no discount — nothing to detach", eventId);
            return;
        }

        paddleClient.removeDiscountFromSubscription(subscriptionId);
        log.info("Detached consumed one-time discount {} from Paddle subscription {} (event {})",
                discountId, subscriptionId, eventId);
    }

    private PaddleWebhookTransactionEvent parseTransactionEvent(String rawBody) {
        try {
            return objectMapper.readValue(rawBody, PaddleWebhookTransactionEvent.class);
        } catch (Exception e) {
            throw new ForbiddenException(ErrorMessages.Paddle.INVALID_WEBHOOK_SIGNATURE);
        }
    }

    // null both when nothing is scheduled and when something other than a
    // cancellation is scheduled (e.g. a pause) — this codebase only tracks
    // scheduled cancellations, per JOB-197's scope.
    private static Instant scheduledCancellationAt(PaddleWebhookEvent event) {
        PaddleWebhookScheduledChange scheduledChange = event.data().scheduledChange();
        if (scheduledChange == null || !SCHEDULED_CHANGE_ACTION_CANCEL.equals(scheduledChange.action())) {
            return null;
        }
        return scheduledChange.effectiveAt();
    }

    // null for paused/canceled subscriptions, per Paddle's docs — not every
    // subscription.* event carries a current billing period.
    private static Instant currentPeriodStartsAt(PaddleWebhookEvent event) {
        return event.data().currentBillingPeriod() != null ? event.data().currentBillingPeriod().startsAt() : null;
    }

    // JOB-198: a downgrade never touches the active tier/addons at request time —
    // it only takes effect once the period it was requested during actually ends.
    // That's detected here: if the webhook's period start is later than the one we
    // last knew, the period has rolled over, so any pending change gets promoted to
    // active now, before this event's own status/period fields are persisted below.
    private void applyPendingDowngradeIfPeriodRolledOver(OrgSubscriptionModel subscription,
            Instant currentPeriodStartsAt) {
        if (currentPeriodStartsAt == null) {
            return;
        }
        boolean periodRolledOver = subscription.getPaddleCurrentPeriodStartsAt() == null
                || currentPeriodStartsAt.isAfter(subscription.getPaddleCurrentPeriodStartsAt());
        if (periodRolledOver && subscription.getPendingTierId() != null) {
            orgSubscriptionRepository.applyPendingDowngrade(subscription.getId());
            log.info("Applied pending downgrade for org {} at period rollover", subscription.getOrgId());
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
