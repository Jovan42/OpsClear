package com.opsclear.service;

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
import com.opsclear.paddle.PaddlePrice;
import com.opsclear.paddle.PaddlePriceResolver;
import com.opsclear.paddle.PaddleSubscription;
import com.opsclear.paddle.PaddleSubscriptionItem;
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

    private static final String PRORATION_BILLING_MODE = "prorated_immediately";
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

    @Transactional
    public OrgSubscriptionModel initiate(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);

        if (subscription.getPaddleCustomerId() != null) {
            log.info("Org {} already has Paddle customer {} — skipping creation",
                    orgId, subscription.getPaddleCustomerId());
            return subscription;
        }

        OrganisationModel org = organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Organisation.NOT_FOUND));
        UserModel requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.User.NOT_FOUND));

        PaddleCustomer customer = paddleClient.createCustomer(requester.getEmail(), org.getName());
        OrgSubscriptionModel updated = orgSubscriptionRepository.updatePaddleCustomerId(
                subscription.getId(), orgId, customer.id());

        log.info("Created Paddle customer {} for org {}", customer.id(), orgId);
        return updated;
    }

    @Transactional
    public PaddleSubscription updateSubscriptionItems(
            UUID orgId, UUID requesterId, UpdatePaddleSubscriptionRequest request) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);
        SubscriptionTierModel tier = requireTier(request.getTierId());

        Set<UUID> addonIds = request.getAddonIds() != null ? new HashSet<>(request.getAddonIds()) : new HashSet<>();

        String billingCycle = subscription.getBillingCycle();
        List<PaddleSubscriptionItem> items = new ArrayList<>();
        items.add(new PaddleSubscriptionItem(priceResolver.resolveTierPriceId(tier.getId(), billingCycle), 1));
        for (UUID addonId : addonIds) {
            items.add(new PaddleSubscriptionItem(priceResolver.resolveAddonPriceId(addonId, billingCycle), 1));
        }

        PaddleSubscription paddleSubscription = paddleClient.updateSubscriptionItems(
                subscription.getPaddleSubscriptionId(), items, PRORATION_BILLING_MODE);

        // subscription_status is intentionally NOT written here — ADR-0044 keeps it
        // synced exclusively from Paddle webhook events (JOB-174), never computed
        // locally. tier_id/addon selection, on the other hand, is this endpoint's own
        // authoritative "change my plan" action, so it's updated immediately rather
        // than waiting on a webhook round-trip — reuses the same repository method
        // the free (non-Paddle) upsertSubscription flow already uses.
        orgSubscriptionRepository.update(
                subscription.getId(), orgId, tier.getId(), subscription.getBillingCycle(), addonIds);

        log.info("Paddle subscription {} updated for org {}: tier={}, status={}",
                paddleSubscription.id(), orgId, tier.getId(), paddleSubscription.status());
        return paddleSubscription;
    }

    @Transactional
    public PaddleSubscription cancel(UUID orgId, UUID requesterId) {
        requireOwner(orgId, requesterId);
        OrgSubscriptionModel subscription = requireSubscriptionRecord(orgId);
        requireNotInternal(subscription);
        requirePaddleSubscriptionExists(subscription);

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
