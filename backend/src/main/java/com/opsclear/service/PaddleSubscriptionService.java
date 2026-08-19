package com.opsclear.service;

import com.opsclear.dto.PreviewSubscriptionUpdateResponse;
import com.opsclear.dto.UpdatePaddleSubscriptionRequest;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.PaddleCatalogSyncResult;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleCustomer;
import com.opsclear.paddle.PaddlePreviewTotals;
import com.opsclear.paddle.PaddlePrice;
import com.opsclear.paddle.PaddlePriceResolver;
import com.opsclear.paddle.PaddleSubscription;
import com.opsclear.paddle.PaddleSubscriptionItem;
import com.opsclear.paddle.PaddleSubscriptionPreview;
import com.opsclear.paddle.PaddleTransaction;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Creates/updates the Paddle side of an org's subscription (ADR-0044). Paddle does
 * not support creating a Subscription directly via API — Paddle creates it
 * automatically once a customer completes an embedded checkout (Paddle.js, JOB-178).
 * So {@link #initiate} only creates the Paddle Customer; the Subscription record
 * itself, and {@code subscription_status}, only exist locally once JOB-174's webhook
 * syncs them in after checkout completes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaddleSubscriptionService {

    // JOB-198: an upgrade (net price increase) applies immediately, charged prorated
    // for the rest of the current period. A downgrade (net price decrease) applies
    // to Paddle's own items immediately too — verified live against sandbox that no
    // proration mode holds Paddle's item state back — but bills nothing until the
    // real next renewal, and critically this app's own feature access (driven by
    // tier_id/org_subscription_addons, never Paddle's item list) stays on the old
    // plan until the webhook confirms that renewal actually happened.
    private static final String PRORATION_UPGRADE = "prorated_immediately";
    private static final String PRORATION_DOWNGRADE = "full_next_billing_period";
    private static final String PRORATION_NO_CHANGE = "do_not_bill";
    private static final String INTERVAL_MONTH = "month";
    private static final String INTERVAL_YEAR = "year";
    private static final String CURRENCY_EUR = "EUR";

    private final OrgSubscriptionRepository orgSubscriptionRepository;
    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final SubscriptionTierRepository tierRepository;
    private final SubscriptionAddonRepository addonRepository;
    private final PaddleClient paddleClient;
    private final PaddlePriceResolver priceResolver;

    // JOB-200: paddle_customer_id lives on organisations, not org_subscriptions —
    // every tier/add-on has a real price, so the org_subscriptions row (tier_id is
    // NOT NULL) must only ever be created once a real payment is confirmed by the
    // webhook, never staged for free. This must still work for an org with no
    // subscription row at all yet, since picking a plan happens *after* this.
    @Transactional
    public String initiate(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        OrganisationModel org = organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
        orgSubscriptionRepository.findByOrgId(orgId).ifPresent(this::requireNotInternal);

        Optional<String> existingCustomerId = organisationRepository.findPaddleCustomerId(orgId);
        if (existingCustomerId.isPresent()) {
            log.info("Org {} already has Paddle customer {} — skipping creation", orgId, existingCustomerId.get());
            return existingCustomerId.get();
        }

        UserModel requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));

        PaddleCustomer customer = paddleClient.createCustomer(requester.getEmail(), org.getName());
        organisationRepository.updatePaddleCustomerId(orgId, customer.id());

        log.info("Created Paddle customer {} for org {}", customer.id(), orgId);
        return customer.id();
    }

    @Transactional
    public PaddleSubscription updateSubscriptionItems(
            UUID orgId, UUID requesterId, UpdatePaddleSubscriptionRequest request) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);
        SubscriptionTierModel newTier = requireTier(request.getTierId());
        Set<UUID> newAddonIds = request.getAddonIds() != null ? new HashSet<>(request.getAddonIds()) : new HashSet<>();

        String billingCycle = subscription.getBillingCycle();
        requireNotMixedChange(subscription, newTier, newAddonIds, billingCycle);
        List<PaddleSubscriptionItem> items = buildItems(newTier, newAddonIds, billingCycle);
        boolean upgrade = isUpgrade(subscription, newTier, newAddonIds, billingCycle);
        if (!upgrade) {
            // A downgrade only ever conflicts with a real scheduled CANCELLATION —
            // verified live that Paddle's full_next_billing_period calls don't
            // conflict with each other (a second/third downgrade attempt just
            // overwrites the previously pending one, harmlessly re-using the same
            // guard cancel() itself uses).
            requireNoCancellationAlreadyScheduled(subscription);
        }
        String prorationMode = upgrade ? PRORATION_UPGRADE : PRORATION_DOWNGRADE;

        PaddleSubscription paddleSubscription = paddleClient.updateSubscriptionItems(
                subscription.getPaddleSubscriptionId(), items, prorationMode);

        // subscription_status is intentionally NOT written here — ADR-0044 keeps it
        // synced exclusively from Paddle webhook events (JOB-174), never computed
        // locally.
        if (upgrade) {
            // The org's own authoritative "change my plan" action for an upgrade —
            // updated immediately rather than waiting on a webhook round-trip, same
            // as before JOB-198.
            orgSubscriptionRepository.update(subscription.getId(), orgId, newTier.getId(), billingCycle, newAddonIds);
            log.info("Paddle subscription {} upgraded immediately for org {}: tier={}",
                    paddleSubscription.id(), orgId, newTier.getId());
        } else {
            // A downgrade is parked as pending — the org keeps its current
            // (already-paid-for) tier/addons until the webhook confirms the period
            // has actually rolled over (see PaddleWebhookService).
            orgSubscriptionRepository.schedulePendingDowngrade(
                    subscription.getId(), orgId, newTier.getId(), newAddonIds, paddleSubscription.nextBilledAt());
            log.info("Paddle subscription {} downgrade scheduled for org {}: pendingTier={} "
                            + "(takes effect at next renewal)",
                    paddleSubscription.id(), orgId, newTier.getId());
        }

        return paddleSubscription;
    }

    // Read-only preview of the same change updateSubscriptionItems would make,
    // without applying or billing anything — lets the frontend show the customer
    // exactly what will happen (charged now vs. takes effect later) before they
    // confirm. Uses Paddle's real preview endpoint rather than computing the
    // prorated amount ourselves.
    @Transactional(readOnly = true)
    public PreviewSubscriptionUpdateResponse previewUpdateSubscriptionItems(
            UUID orgId, UUID requesterId, UpdatePaddleSubscriptionRequest request) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);
        SubscriptionTierModel newTier = requireTier(request.getTierId());
        Set<UUID> newAddonIds = request.getAddonIds() != null ? new HashSet<>(request.getAddonIds()) : new HashSet<>();

        String billingCycle = subscription.getBillingCycle();
        requireNotMixedChange(subscription, newTier, newAddonIds, billingCycle);
        List<PaddleSubscriptionItem> items = buildItems(newTier, newAddonIds, billingCycle);
        boolean upgrade = isUpgrade(subscription, newTier, newAddonIds, billingCycle);
        if (!upgrade) {
            // A downgrade only ever conflicts with a real scheduled CANCELLATION —
            // verified live that Paddle's full_next_billing_period calls don't
            // conflict with each other (a second/third downgrade attempt just
            // overwrites the previously pending one, harmlessly re-using the same
            // guard cancel() itself uses).
            requireNoCancellationAlreadyScheduled(subscription);
        }
        String prorationMode = upgrade ? PRORATION_UPGRADE : PRORATION_DOWNGRADE;

        PaddleSubscriptionPreview preview = paddleClient.previewUpdateSubscriptionItems(
                subscription.getPaddleSubscriptionId(), items, prorationMode);

        Integer immediateChargeAmount = null;
        Integer creditApplied = null;
        String currency = null;
        if (preview.immediateTransaction() != null) {
            PaddlePreviewTotals totals = preview.immediateTransaction().details().totals();
            immediateChargeAmount = Integer.parseInt(totals.total()) / 100;
            currency = totals.currencyCode();
            // Paddle's preview already nets any attached discount (e.g. a credit,
            // JOB-180) into totals.total — this surfaces how much of that reduction
            // came from the discount specifically, so the customer sees it called out
            // rather than just a smaller number with no explanation.
            if (totals.discount() != null) {
                int discountMinorUnits = Integer.parseInt(totals.discount());
                if (discountMinorUnits > 0) {
                    creditApplied = discountMinorUnits / 100;
                }
            }
        }

        return PreviewSubscriptionUpdateResponse.builder()
                .upgrade(upgrade)
                .immediateChargeAmount(immediateChargeAmount)
                .creditApplied(creditApplied)
                .currency(currency)
                .effectiveAt(preview.currentBillingPeriod() != null ? preview.currentBillingPeriod().endsAt() : null)
                .build();
    }

    @Transactional
    public PaddleSubscription cancel(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);
        requireNoCancellationAlreadyScheduled(subscription);

        // subscription_status stays as-is here — Paddle keeps the subscription
        // "active" until the current period actually ends, then sends a
        // subscription.canceled webhook (JOB-174) that syncs the local status. This
        // just schedules the cancellation; it doesn't cut off access immediately.
        PaddleSubscription cancelled = paddleClient.cancelSubscription(subscription.getPaddleSubscriptionId());
        log.info("Scheduled end-of-period cancellation for Paddle subscription {} (org {})",
                cancelled.id(), orgId);
        return cancelled;
    }

    @Transactional
    public OrgSubscriptionModel resume(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);
        requireCancellationScheduled(subscription);

        paddleClient.removeScheduledCancellation(subscription.getPaddleSubscriptionId());
        OrgSubscriptionModel updated =
                orgSubscriptionRepository.clearScheduledCancellation(subscription.getId(), orgId);
        log.info("Removed scheduled cancellation for Paddle subscription {} (org {})",
                subscription.getPaddleSubscriptionId(), orgId);
        return updated;
    }

    // The customer changed their mind before a pending downgrade took effect —
    // reverts Paddle's items back to the currently active plan with do_not_bill
    // (verified live: zero billing impact, next_billed_at unchanged), then clears
    // the local pending state. Paddle's own item state already changed to the
    // downgraded set the moment it was scheduled (see updateSubscriptionItems'
    // class-level note) — this is undoing that, not a "cancel a scheduled
    // change" in Paddle's own model (there is no such Paddle-side concept for a
    // proration-mode item update, only for pause/cancel).
    @Transactional
    public OrgSubscriptionModel cancelPendingDowngrade(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);
        requirePendingDowngradeExists(subscription);

        SubscriptionTierModel activeTier = requireTier(subscription.getTierId());
        List<PaddleSubscriptionItem> activeItems =
                buildItems(activeTier, new HashSet<>(subscription.getAddonIds()), subscription.getBillingCycle());
        paddleClient.updateSubscriptionItems(subscription.getPaddleSubscriptionId(), activeItems, PRORATION_NO_CHANGE);

        OrgSubscriptionModel updated = orgSubscriptionRepository.clearPendingDowngrade(subscription.getId(), orgId);
        log.info("Cancelled pending downgrade for Paddle subscription {} (org {}), reverted to active tier {}",
                subscription.getPaddleSubscriptionId(), orgId, activeTier.getId());
        return updated;
    }

    // No local invoice/transaction table by design (ADR-0044) — reads live from
    // Paddle on every call rather than mirroring history into our own DB. An org
    // that hasn't completed checkout yet simply has no history, not an error — and
    // per JOB-200, may not even have an org_subscriptions row yet at all.
    @Transactional(readOnly = true)
    public List<PaddleTransaction> getBillingHistory(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
        orgSubscriptionRepository.findByOrgId(orgId).ifPresent(this::requireNotInternal);

        Optional<String> customerId = organisationRepository.findPaddleCustomerId(orgId);
        if (customerId.isEmpty()) {
            return List.of();
        }
        return paddleClient.listBillingHistory(customerId.get());
    }

    @Transactional(readOnly = true)
    public String getUpdatePaymentMethodTransactionId(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);

        return paddleClient.getUpdatePaymentMethodTransaction(subscription.getPaddleSubscriptionId()).id();
    }

    @Transactional
    public SubscriptionTierModel syncTierPriceToPaddle(SubscriptionTierModel tier) {
        String productId = tier.getPaddleProductId();
        if (productId == null) {
            productId = paddleClient.createProduct(tierProductName(tier)).id();
        }

        PaddlePrice newMonthly = paddleClient.createPrice(productId, tierProductName(tier) + " — Monthly",
                INTERVAL_MONTH, toMinorUnits(tier.getPriceMonthly()), CURRENCY_EUR);
        PaddlePrice newAnnual = paddleClient.createPrice(productId, tierProductName(tier) + " — Annual",
                INTERVAL_YEAR, toMinorUnits(tier.getPriceAnnual()), CURRENCY_EUR);

        archiveIfPresent(tier.getPaddlePriceIdMonthly());
        archiveIfPresent(tier.getPaddlePriceIdAnnual());

        SubscriptionTierModel updated =
                tierRepository.updatePaddleIds(tier.getId(), productId, newMonthly.id(), newAnnual.id());
        log.info("Synced Paddle prices for tier {}: product={}, monthly={}, annual={}",
                tier.getId(), productId, newMonthly.id(), newAnnual.id());
        return updated;
    }

    @Transactional
    public SubscriptionAddonModel syncAddonPriceToPaddle(SubscriptionAddonModel addon) {
        String productId = addon.getPaddleProductId();
        if (productId == null) {
            productId = paddleClient.createProduct(addon.getName()).id();
        }

        PaddlePrice newMonthly = paddleClient.createPrice(productId, addon.getName() + " — Monthly",
                INTERVAL_MONTH, toMinorUnits(addon.getPriceMonthly()), CURRENCY_EUR);
        PaddlePrice newAnnual = paddleClient.createPrice(productId, addon.getName() + " — Annual",
                INTERVAL_YEAR, toMinorUnits(addon.getPriceAnnual()), CURRENCY_EUR);

        archiveIfPresent(addon.getPaddlePriceIdMonthly());
        archiveIfPresent(addon.getPaddlePriceIdAnnual());

        SubscriptionAddonModel updated =
                addonRepository.updatePaddleIds(addon.getId(), productId, newMonthly.id(), newAnnual.id());
        log.info("Synced Paddle prices for addon {}: product={}, monthly={}, annual={}",
                addon.getKey(), productId, newMonthly.id(), newAnnual.id());
        return updated;
    }

    @Transactional
    public PaddleCatalogSyncResult syncCatalogToPaddle() {
        int tiersSynced = 0;
        for (SubscriptionTierModel tier : tierRepository.findAll()) {
            if (tier.getPaddleProductId() == null) {
                syncTierPriceToPaddle(tier);
                tiersSynced++;
            }
        }

        int addonsSynced = 0;
        for (SubscriptionAddonModel addon : addonRepository.findAll()) {
            if (addon.getPaddleProductId() == null) {
                syncAddonPriceToPaddle(addon);
                addonsSynced++;
            }
        }

        return new PaddleCatalogSyncResult(tiersSynced, addonsSynced);
    }

    // ─── Upgrade/downgrade classification helpers ───────────────────────────────

    private List<PaddleSubscriptionItem> buildItems(
            SubscriptionTierModel tier, Set<UUID> addonIds, String billingCycle) {
        List<PaddleSubscriptionItem> items = new ArrayList<>();
        items.add(new PaddleSubscriptionItem(priceResolver.resolveTierPriceId(tier.getId(), billingCycle), 1));
        for (UUID addonId : addonIds) {
            items.add(new PaddleSubscriptionItem(priceResolver.resolveAddonPriceId(addonId, billingCycle), 1));
        }
        return items;
    }

    // A net price decrease (or no change) is treated as a downgrade — anything that
    // isn't strictly more expensive gets the deferred-to-renewal treatment, since
    // there's no immediate extra charge to justify applying it right away.
    private boolean isUpgrade(OrgSubscriptionModel subscription, SubscriptionTierModel newTier,
            Set<UUID> newAddonIds, String billingCycle) {
        SubscriptionTierModel currentTier = requireTier(subscription.getTierId());
        int oldTotal = totalPrice(currentTier, new HashSet<>(subscription.getAddonIds()), billingCycle);
        int newTotal = totalPrice(newTier, newAddonIds, billingCycle);
        return newTotal > oldTotal;
    }

    private int totalPrice(SubscriptionTierModel tier, Set<UUID> addonIds, String billingCycle) {
        boolean annual = "ANNUAL".equals(billingCycle);
        int total = annual ? tier.getPriceAnnual() : tier.getPriceMonthly();
        if (!addonIds.isEmpty()) {
            for (SubscriptionAddonModel addon : addonRepository.findByIds(addonIds)) {
                total += annual ? addon.getPriceAnnual() : addon.getPriceMonthly();
            }
        }
        return total;
    }

    // ─── Paddle price sync helpers ──────────────────────────────────────────────

    private String tierProductName(SubscriptionTierModel tier) {
        String projects = tier.getMaxProjects() != null ? tier.getMaxProjects() + " projects" : "unlimited projects";
        return "Tier: " + tier.getMaxMembers() + " members, " + projects;
    }

    private void archiveIfPresent(String priceId) {
        if (priceId != null) {
            paddleClient.archivePrice(priceId);
        }
    }

    private static String toMinorUnits(int wholeEuros) {
        return String.valueOf(wholeEuros * 100);
    }

    // ─── Guards ───────────────────────────────────────────────────────────────

    private OrgSubscriptionModel requireSubscriptionRecord(UUID orgId) {
        return orgSubscriptionRepository.findByOrgId(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Paddle.NO_SUBSCRIPTION_RECORD));
    }

    private void requireNotInternal(OrgSubscriptionModel subscription) {
        if (subscription.isInternal()) {
            throw new BadRequestException(ErrorMessages.Paddle.INTERNAL_ORG_NOT_BILLED);
        }
    }

    private void requirePaddleSubscriptionExists(OrgSubscriptionModel subscription) {
        if (subscription.getPaddleSubscriptionId() == null) {
            throw new ConflictException(ErrorMessages.Paddle.NO_PADDLE_SUBSCRIPTION_YET);
        }
    }

    // Paddle itself rejects a second cancel with a raw 400
    // (subscription_locked_pending_changes) — catching it here first gives a clearer,
    // consistent 409 instead of forwarding Paddle's error shape to our own callers.
    private void requireNoCancellationAlreadyScheduled(OrgSubscriptionModel subscription) {
        if (subscription.getPaddleScheduledCancellationAt() != null) {
            throw new ConflictException(ErrorMessages.Paddle.CANCELLATION_ALREADY_SCHEDULED);
        }
    }

    private void requireCancellationScheduled(OrgSubscriptionModel subscription) {
        if (subscription.getPaddleScheduledCancellationAt() == null) {
            throw new ConflictException(ErrorMessages.Paddle.NO_CANCELLATION_SCHEDULED);
        }
    }

    // A single request that both adds something pricier and removes something
    // cheaper nets out to an "upgrade" by total price, which would apply
    // immediately and — because Paddle's item update is atomic — also yank the
    // removed item away right now, even though the customer already paid for it
    // this period. Rather than splitting into two Paddle calls (immediate add +
    // deferred remove) to get correct per-item semantics, simplest and safest is
    // to just block the mixed case and ask the customer to save the increase and
    // the decrease separately.
    private void requireNotMixedChange(OrgSubscriptionModel subscription, SubscriptionTierModel newTier,
            Set<UUID> newAddonIds, String billingCycle) {
        SubscriptionTierModel currentTier = requireTier(subscription.getTierId());
        int oldTierPrice = totalPrice(currentTier, Set.of(), billingCycle);
        int newTierPrice = totalPrice(newTier, Set.of(), billingCycle);
        Set<UUID> currentAddonIds = new HashSet<>(subscription.getAddonIds());
        boolean addonAdded = newAddonIds.stream().anyMatch(id -> !currentAddonIds.contains(id));
        boolean addonRemoved = currentAddonIds.stream().anyMatch(id -> !newAddonIds.contains(id));
        boolean hasIncrease = newTierPrice > oldTierPrice || addonAdded;
        boolean hasDecrease = newTierPrice < oldTierPrice || addonRemoved;
        if (hasIncrease && hasDecrease) {
            throw new ConflictException(ErrorMessages.Paddle.MIXED_UPGRADE_DOWNGRADE_NOT_ALLOWED);
        }
    }

    private void requirePendingDowngradeExists(OrgSubscriptionModel subscription) {
        if (subscription.getPendingTierId() == null) {
            throw new ConflictException(ErrorMessages.Paddle.NO_PENDING_DOWNGRADE_TO_CANCEL);
        }
    }

    private SubscriptionTierModel requireTier(UUID tierId) {
        return tierRepository.findById(tierId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.SubscriptionTier.NOT_FOUND));
    }

    private void requireOwner(UUID orgId, UUID userId) {
        OrganisationRole role = organisationRepository.findMemberRole(orgId, userId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
        if (role != OrganisationRole.OWNER) {
            throw new ForbiddenException(ErrorMessages.Organisation.INSUFFICIENT_PERMISSIONS_OWNER);
        }
    }
}
