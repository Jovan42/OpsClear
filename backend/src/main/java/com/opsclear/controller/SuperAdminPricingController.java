package com.opsclear.controller;

import com.opsclear.aop.RequiresSuperUser;
import com.opsclear.dto.CatalogSyncResponse;
import com.opsclear.dto.SubscriptionAddonResponse;
import com.opsclear.dto.SubscriptionTierResponse;
import com.opsclear.dto.UpdateAddonPriceRequest;
import com.opsclear.dto.UpdateTierPriceRequest;
import com.opsclear.service.SuperAdminPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/super-admin/pricing")
@RequiredArgsConstructor
public class SuperAdminPricingController {

    private final SuperAdminPricingService pricingService;

    @RequiresSuperUser
    @GetMapping("/tiers")
    public ResponseEntity<List<SubscriptionTierResponse>> listTiers() {
        List<SubscriptionTierResponse> tiers = pricingService.listTiers()
                .stream()
                .map(SubscriptionTierResponse::from)
                .toList();
        return ResponseEntity.ok(tiers);
    }

    @RequiresSuperUser
    @PutMapping("/tiers/{tierId}")
    public ResponseEntity<SubscriptionTierResponse> updateTierPrice(
            @PathVariable UUID tierId,
            @Valid @RequestBody UpdateTierPriceRequest request) {
        return ResponseEntity.ok(SubscriptionTierResponse.from(pricingService.updateTierPrice(tierId, request)));
    }

    @RequiresSuperUser
    @GetMapping("/addons")
    public ResponseEntity<List<SubscriptionAddonResponse>> listAddons() {
        List<SubscriptionAddonResponse> addons = pricingService.listAddons()
                .stream()
                .map(SubscriptionAddonResponse::from)
                .toList();
        return ResponseEntity.ok(addons);
    }

    @RequiresSuperUser
    @PutMapping("/addons/{addonKey}")
    public ResponseEntity<SubscriptionAddonResponse> updateAddonPrice(
            @PathVariable String addonKey,
            @Valid @RequestBody UpdateAddonPriceRequest request) {
        return ResponseEntity.ok(SubscriptionAddonResponse.from(pricingService.updateAddonPrice(addonKey, request)));
    }

    @RequiresSuperUser
    @PostMapping("/sync")
    public ResponseEntity<CatalogSyncResponse> syncCatalog() {
        return ResponseEntity.ok(CatalogSyncResponse.from(pricingService.syncCatalogToPaddle()));
    }
}
