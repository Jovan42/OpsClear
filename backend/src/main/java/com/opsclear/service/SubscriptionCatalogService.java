package com.opsclear.service;

import com.opsclear.dto.CatalogResponse;
import com.opsclear.dto.SubscriptionAddonResponse;
import com.opsclear.dto.SubscriptionTierResponse;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionCatalogService {

    private final SubscriptionTierRepository tierRepository;
    private final SubscriptionAddonRepository addonRepository;

    @Transactional(readOnly = true)
    public CatalogResponse getCatalog() {
        log.debug("Fetching subscription catalog");
        return CatalogResponse.builder()
                .tiers(tierRepository.findAll().stream().map(SubscriptionTierResponse::from).toList())
                .addons(addonRepository.findAll().stream().map(SubscriptionAddonResponse::from).toList())
                .build();
    }
}
