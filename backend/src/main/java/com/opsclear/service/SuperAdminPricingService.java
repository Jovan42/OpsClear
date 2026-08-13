package com.opsclear.service;

import com.opsclear.dto.UpdateAddonPriceRequest;
import com.opsclear.dto.UpdateTierPriceRequest;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.PaddleCatalogSyncResult;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Pricing configuration for the super admin console (ADR-0043) — CRUD on subscription
 * tier and add-on prices only. Creating/deleting tiers or add-ons themselves is out of
 * scope. A price change takes effect immediately for new subscriptions; existing
 * subscriptions are left as-is (grandfathering/re-evaluation is a manual business
 * decision, not automated here). Access is gated entirely at the controller via
 * {@code @RequiresSuperUser} — this service has no requester-scoping of its own since
 * pricing is global, not org/project-scoped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuperAdminPricingService {

    private final SubscriptionTierRepository tierRepository;
    private final SubscriptionAddonRepository addonRepository;
    private final PaddleSubscriptionService paddleSubscriptionService;

    @Transactional(readOnly = true)
    public List<SubscriptionTierModel> listTiers() {
        return tierRepository.findAll();
    }

    @Transactional
    public SubscriptionTierModel updateTierPrice(UUID tierId, UpdateTierPriceRequest request) {
        requireTier(tierId);
        SubscriptionTierModel updated = tierRepository.updatePrice(
                tierId, request.getPriceMonthly(), request.getPriceAnnual());
        log.info("Updated subscription tier {} price to {}/{}",
                tierId, request.getPriceMonthly(), request.getPriceAnnual());
        return paddleSubscriptionService.syncTierPriceToPaddle(updated);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionAddonModel> listAddons() {
        return addonRepository.findAll();
    }

    @Transactional
    public SubscriptionAddonModel updateAddonPrice(String addonKey, UpdateAddonPriceRequest request) {
        requireAddon(addonKey);
        SubscriptionAddonModel updated = addonRepository.updatePrice(
                addonKey, request.getPriceMonthly(), request.getPriceAnnual());
        log.info("Updated subscription addon {} price to {}/{}",
                addonKey, request.getPriceMonthly(), request.getPriceAnnual());
        return paddleSubscriptionService.syncAddonPriceToPaddle(updated);
    }

    @Transactional
    public PaddleCatalogSyncResult syncCatalogToPaddle() {
        return paddleSubscriptionService.syncCatalogToPaddle();
    }

    // --- Guards ---

    private void requireTier(UUID tierId) {
        tierRepository.findById(tierId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.SubscriptionTier.NOT_FOUND));
    }

    private void requireAddon(String addonKey) {
        addonRepository.findByKey(addonKey)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.SubscriptionAddon.NOT_FOUND));
    }
}
